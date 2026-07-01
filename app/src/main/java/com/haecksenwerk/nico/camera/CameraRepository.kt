package com.haecksenwerk.nico.camera

import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import com.haecksenwerk.nico.ptp.PtpConstants
import com.haecksenwerk.nico.ptp.PtpException
import com.haecksenwerk.nico.ptp.PtpObjectInfo
import com.haecksenwerk.nico.ptp.PtpSession
import com.haecksenwerk.nico.ptp.PtpTransport
import java.io.OutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

enum class ConnectionState { IDLE, DETECTING, CONNECTING, USB_CONNECTED, READY, CAPTURING, ERROR }

/** PTP data type and enumerated allowed values for a device property. */
data class PropDescResult(val dataType: Int, val values: List<Long>)

data class CameraProperties(
    val batteryLevel: Int = 0,
    val exposureProgramMode: Int = 0,  // 1=M 2=P 3=A 4=S 0x8010=AUTO (from 0x500E, read-only)
    val fNumber: Int = 0,              // f × 100 (280 = f/2.8)
    val nikonExposureTime: Long = 0,   // Nikon encoding (num<<16)|den from 0xD100 (display only)
    val isoValue: Long = 0,            // raw ISO number (read from 0x500F)
    val exposureBias: Int = 0,         // EV × 1000 (INT16 from 0x5010, display only)
    val whiteBalance: Int = 0,
    val meteringMode: Int = 0,         // 2=CW 3=Matrix 4=Spot 0x8010=HL-W
    val focusModeNikon: Int = 0,       // 0xD061 value: 0=AF-S, 1=AF-C, 4=MF (writable)
)

/**
 * PTP state machine.  All suspend functions are safe to call from the Main
 * dispatcher; heavy USB I/O is dispatched internally via [ptpMutex].
 */
class CameraRepository(private val usbManager: UsbManager) {

    private val repoScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Serialises all USB transactions — PTP is strictly request/response and
    // cannot handle interleaved commands from different coroutines.
    private val ptpMutex = Mutex()

    private val _state = MutableStateFlow(ConnectionState.IDLE)
    val state: StateFlow<ConnectionState> = _state.asStateFlow()

    private val _cameraName = MutableStateFlow("")
    val cameraName: StateFlow<String> = _cameraName.asStateFlow()

    private val _properties = MutableStateFlow(CameraProperties())
    val properties: StateFlow<CameraProperties> = _properties.asStateFlow()

    private val _propEnums = MutableStateFlow<Map<Int, PropDescResult>>(emptyMap())
    val propEnums: StateFlow<Map<Int, PropDescResult>> = _propEnums.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _liveViewActive = MutableStateFlow(false)
    val liveViewActive: StateFlow<Boolean> = _liveViewActive.asStateFlow()

    private val _liveViewFrame = MutableStateFlow<ByteArray?>(null)
    val liveViewFrame: StateFlow<ByteArray?> = _liveViewFrame.asStateFlow()

    // ChangeAfArea x-coordinate range = LiveViewZoomArea max × 2.
    // Populated at connect time; 0 until the property has been read.
    private val _afAreaXMax = MutableStateFlow(0)
    val afAreaXMax: StateFlow<Int> = _afAreaXMax.asStateFlow()

    private var transport: PtpTransport? = null
    private var session: PtpSession? = null
    private var activationJob: Job? = null
    private var pollJob: Job? = null
    private var liveViewJob: Job? = null
    @Volatile private var liveViewShouldRun = false

    // Controls whether live view is started automatically when the camera becomes ready.
    // Updated by MainActivity from the settings flow.
    @Volatile var liveViewOnConnect: Boolean = false

    // Raw JPEG bytes for each thumbnail keyed by object handle.
    // Avoids repeated USB round-trips for the same thumbnail (e.g. when Coil
    // evicts a decoded Bitmap from its memory cache and needs to re-decode).
    // Byte-bounded so the cache size tracks actual memory rather than entry count.
    private val thumbCache = object : android.util.LruCache<Long, ByteArray>(8 * 1024 * 1024) {
        override fun sizeOf(key: Long, value: ByteArray) = value.size
    }

    // ── Connection lifecycle ──────────────────────────────────────────────────

    suspend fun connect(device: UsbDevice) {
        val busy = _state.value
        if (busy == ConnectionState.CONNECTING ||
            busy == ConnectionState.USB_CONNECTED ||
            busy == ConnectionState.READY) return

        _state.value = ConnectionState.CONNECTING
        _error.value = null

        if (!device.hasPtpInterface()) {
            _state.value = ConnectionState.ERROR
            _error.value = "No PTP/MTP interface found — set camera USB mode to PTP/MTP and reconnect"
            return
        }

        delay(800.milliseconds)   // USB settle time

        val t = PtpTransport(usbManager, device)
        if (!withContext(Dispatchers.IO) { t.open() }) {
            _state.value = ConnectionState.ERROR
            _error.value = "Failed to claim USB interface — reconnect the cable"
            return
        }
        transport = t
        _state.value = ConnectionState.USB_CONNECTED

        // Background loop: try to open a PTP session every 2 s until the camera is switched on.
        activationJob?.cancel()
        activationJob = repoScope.launch { activationLoop() }
    }

    private suspend fun activationLoop() {
        val t = transport ?: return
        while (_state.value == ConnectionState.USB_CONNECTED) {
            try {
                val s = PtpSession(t)
                ptpMutex.withLock { s.requireOk(s.openSession(), "OpenSession") }

                // NikonDeviceReady (0x90C8) returns RC_OK only when the camera body is
                // powered on. The Z fc opens PTP sessions even while switched off (its
                // USB chipset stays live for charging), so this is the reliable probe.
                val cameraOn = ptpMutex.withLock { s.deviceReady() }
                if (!cameraOn) {
                    ptpMutex.withLock { try { s.closeSession() } catch (_: Exception) {} }
                    if (_state.value == ConnectionState.USB_CONNECTED) delay(2.seconds)
                    continue
                }

                session = s
                delay(400.milliseconds)

                val (make, model) = ptpMutex.withLock { s.getDeviceMakeModel() }
                // Use first word of manufacturer to avoid "NIKON CORPORATION Z fc";
                // skip it entirely if the model already starts with it.
                val makeWord = make.substringBefore(" ").trim()
                _cameraName.value = if (model.startsWith(makeWord, ignoreCase = true) || makeWord.isEmpty())
                    model else "$makeWord $model"
                _state.value = ConnectionState.READY
                if (liveViewOnConnect) startLiveView()
                refreshProperties()
                fetchAllEnumValues()
                startPolling()
                return
            } catch (_: Exception) {
                session = null
                if (_state.value == ConnectionState.USB_CONNECTED) delay(2.seconds)
            }
        }
    }

    suspend fun disconnect() {
        activationJob?.cancel()
        activationJob = null
        liveViewShouldRun = false
        liveViewJob?.cancel()
        liveViewJob = null
        _liveViewActive.value = false
        _liveViewFrame.value = null
        stopPolling()
        ptpMutex.withLock {
            try { session?.closeSession() } catch (_: Exception) {}
        }
        cleanupTransport()
        _state.value = ConnectionState.IDLE
        _cameraName.value = ""
        _properties.value = CameraProperties()
        _propEnums.value = emptyMap()
        _afAreaXMax.value = 0
        _error.value = null
        thumbCache.evictAll()
    }

    fun close() {
        repoScope.cancel()
    }

    // ── Camera operations ─────────────────────────────────────────────────────

    /** Moves the AF point to pixel (x, y) in the LiveView frame. Returns false if not in LiveView. */
    suspend fun setAfArea(x: Int, y: Int): Boolean {
        val s = session ?: return false
        return try {
            val resp = ptpMutex.withLock { s.changeAfArea(x, y) }
            resp.code == PtpConstants.RC_OK
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Sends AfDrive and polls DeviceReady up to 5 s. Returns true if focus found.
     *
     * When cancelled (e.g. because the user tapped a new AF area), sends
     * AfDriveCancel (0x9206) so the camera stops hunting before the next
     * ChangeAfArea + AfDrive arrives.
     */
    suspend fun triggerAutofocus(): Boolean {
        val s = session ?: return false
        try {
            val resp = ptpMutex.withLock { s.afDrive() }
            s.requireOk(resp, "AfDrive")
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            return false
        }
        try {
            repeat(50) {
                delay(100.milliseconds)
                val code = try {
                    ptpMutex.withLock { s.deviceReadyCode() }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    return false
                }
                when (code) {
                    PtpConstants.RC_OK,
                    PtpConstants.RC_NIKON_SILENT_RELEASE_BUSY -> return true   // camera is ready (silent-shutter mode)
                    PtpConstants.RC_NIKON_OUT_OF_FOCUS        -> return false
                    PtpConstants.RC_DEVICE_BUSY,
                    PtpConstants.RC_NIKON_BULB_RELEASE_BUSY   -> { /* still searching — keep polling */ }
                    else                                      -> return false
                }
            }
            return false
        } catch (e: CancellationException) {
            // Tell the camera to stop searching before the next AF drive arrives.
            withContext(NonCancellable) {
                try { ptpMutex.withLock { s.afDriveCancel() } } catch (_: Exception) {}
            }
            throw e
        }
    }

    suspend fun capture() {
        val s = session ?: return
        _state.value = ConnectionState.CAPTURING
        try {
            val resp = ptpMutex.withLock { s.initiateCapture() }
            s.requireOk(resp, "InitiateCapture")
            ptpMutex.withLock { s.drainInterruptEvent() }
        } catch (e: Exception) {
            _error.value = e.message
        } finally {
            if (_state.value == ConnectionState.CAPTURING) {
                _state.value = ConnectionState.READY
            }
        }
    }

    // ── Live view ─────────────────────────────────────────────────────────────

    suspend fun toggleLiveView() {
        if (_liveViewActive.value) stopLiveView() else startLiveView()
    }

    private suspend fun startLiveView() {
        val s = session ?: return
        _liveViewActive.value = true  // Update UI immediately so toggle shows ON + spinner
        try {
            ptpMutex.withLock { s.startLiveView() }
        } catch (e: Exception) {
            _liveViewActive.value = false
            _error.value = "Live view: ${e.message}"
            return
        }
        // Poll until camera is ready (up to 2 s)
        var attempts = 0
        while (attempts < 20) {
            delay(100.milliseconds)
            if (try { ptpMutex.withLock { s.deviceReady() } } catch (_: Exception) { false }) break
            attempts++
        }
        liveViewShouldRun = true
        liveViewJob?.cancel()
        liveViewJob = repoScope.launch { liveViewLoop() }
    }

    suspend fun stopLiveView() {
        // Signal loop to stop cleanly so it finishes its current USB transaction
        // before we send EndLiveView. Hard-cancelling mid-transfer would leave
        // stale data in the USB IN endpoint, breaking subsequent commands.
        liveViewShouldRun = false
        withTimeoutOrNull(2.seconds) { liveViewJob?.join() }
        liveViewJob?.cancel()
        liveViewJob = null
        val s = session
        if (s != null) try { ptpMutex.withLock { s.endLiveView() } } catch (_: Exception) {}
        _liveViewActive.value = false
        _liveViewFrame.value = null
    }

    private suspend fun liveViewLoop() {
        val s = session ?: return
        while (liveViewShouldRun) {
            if (_state.value == ConnectionState.CAPTURING) { delay(100.milliseconds); continue }
            val t0 = System.currentTimeMillis()
            val jpeg = try {
                ptpMutex.withLock { s.getLiveViewImage() }
            } catch (e: Exception) {
                val msg = e.message ?: ""
                when {
                    "NotLiveView" in msg -> { _liveViewActive.value = false; return }
                    "DeviceBusy" in msg -> { delay(10.milliseconds); continue }
                    else -> { delay(50.milliseconds); continue }
                }
            }
            if (jpeg != null) _liveViewFrame.value = jpeg
            // Yield for at least 100 ms so GetDevicePropDesc / SetDevicePropValue
            // can acquire the mutex between frames without starvation.
            val elapsed = System.currentTimeMillis() - t0
            if (elapsed < 100L) delay((100L - elapsed).milliseconds)
        }
    }

    private suspend fun fetchAllEnumValues() {
        val s = session ?: return
        val settable = intArrayOf(
            PtpConstants.PROP_F_NUMBER,
            PtpConstants.PROP_WHITE_BALANCE,
            PtpConstants.PROP_EXPOSURE_METERING_MODE,
            PtpConstants.PROP_NIKON_FOCUS_MODE,
        )
        val result = mutableMapOf<Int, PropDescResult>()
        for (propCode in settable) {
            val (dataType, values) = try {
                ptpMutex.withLock { s.getDevicePropDescEnum(propCode) }
            } catch (_: Exception) { 0 to emptyList() }
            if (values.isNotEmpty()) result[propCode] = PropDescResult(dataType, values)
        }
        _propEnums.value = result

        // Derive ChangeAfArea coordinate range from LiveViewZoomArea max value.
        // ZoomArea max = half the x-coordinate range (furthest center a zoom window can sit).
        val (_, zoomAreaValues) = try {
            ptpMutex.withLock { s.getDevicePropDescEnum(PtpConstants.PROP_NIKON_LIVE_VIEW_ZOOM_AREA) }
        } catch (_: Exception) { 0 to emptyList() }
        val zoomAreaMax = zoomAreaValues.maxOrNull()?.toInt() ?: 0
        if (zoomAreaMax > 0) _afAreaXMax.value = zoomAreaMax * 4
    }

    suspend fun setDeviceProp(propCode: Int, value: Long) {
        val s = session ?: return
        val dataType = _propEnums.value[propCode]?.dataType ?: return
        try {
            ptpMutex.withLock { s.setDevicePropValueRaw(propCode, value, dataType) }
        } catch (e: Exception) {
            android.util.Log.w("nico", "setDeviceProp 0x${propCode.toString(16)} val=$value type=0x${dataType.toString(16)}: ${e.message}")
            _error.value = e.message
            return
        }
        _error.value = null
        _properties.value = when (propCode) {
            PtpConstants.PROP_F_NUMBER               -> _properties.value.copy(fNumber = value.toInt())
            PtpConstants.PROP_WHITE_BALANCE          -> _properties.value.copy(whiteBalance = value.toInt())
            PtpConstants.PROP_EXPOSURE_METERING_MODE -> _properties.value.copy(meteringMode = value.toInt())
            PtpConstants.PROP_NIKON_FOCUS_MODE       -> _properties.value.copy(focusModeNikon = value.toInt())
            else -> _properties.value
        }
    }

    suspend fun refreshProperties() {
        val s = session ?: return
        val last = _properties.value

        val battery    = tryGet(last.batteryLevel)        { ptpMutex.withLock { s.decodeUint8(s.getDevicePropValue(PtpConstants.PROP_BATTERY_LEVEL)) } }
        val mode       = tryGet(last.exposureProgramMode) { ptpMutex.withLock { s.decodeUint16(s.getDevicePropValue(PtpConstants.PROP_EXPOSURE_PROGRAM_MODE)) } }
        val fNum       = tryGet(last.fNumber)             { ptpMutex.withLock { s.decodeUint16(s.getDevicePropValue(PtpConstants.PROP_F_NUMBER)) } }
        val nikonExpTime = tryGet(last.nikonExposureTime) { ptpMutex.withLock { s.decodeUint32(s.getDevicePropValue(PtpConstants.PROP_NIKON_EXPOSURE_TIME)) } }
        val iso        = tryGet(last.isoValue)            { ptpMutex.withLock { s.decodeUintFlex(s.getDevicePropValue(PtpConstants.PROP_EXPOSURE_INDEX)) } }
        val bias       = tryGet(last.exposureBias)        { ptpMutex.withLock { s.decodeIntFlex(s.getDevicePropValue(PtpConstants.PROP_EXPOSURE_BIAS)) } }
        val wb         = tryGet(last.whiteBalance)        { ptpMutex.withLock { s.decodeUint16(s.getDevicePropValue(PtpConstants.PROP_WHITE_BALANCE)) } }
        val metering   = tryGet(last.meteringMode)        { ptpMutex.withLock { s.decodeUint16(s.getDevicePropValue(PtpConstants.PROP_EXPOSURE_METERING_MODE)) } }
        val focusNikon = tryGet(last.focusModeNikon) {
            withTimeoutOrNull(1.seconds) { ptpMutex.withLock { s.decodeUint8(s.getDevicePropValue(PtpConstants.PROP_NIKON_FOCUS_MODE)) } }
                ?: last.focusModeNikon
        }

        _properties.value = CameraProperties(
            batteryLevel = battery,
            exposureProgramMode = mode,
            fNumber = fNum,
            nikonExposureTime = nikonExpTime,
            isoValue = iso,
            exposureBias = bias,
            whiteBalance = wb,
            meteringMode = metering,
            focusModeNikon = focusNikon,
        )
        _error.value = null
    }

    // ── Event polling ─────────────────────────────────────────────────────────

    private fun startPolling() {
        pollJob?.cancel()
        pollJob = repoScope.launch { pollLoop() }
    }

    private fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
    }

    private suspend fun pollLoop() {
        while (_state.value == ConnectionState.READY || _state.value == ConnectionState.CAPTURING) {
            delay(500.milliseconds)
            val s = session ?: return

            // Skip event poll while the shutter is open — the camera is busy and
            // GetEvent would return DeviceBusy or stall.
            if (_state.value == ConnectionState.CAPTURING) continue

            val events = try {
                ptpMutex.withLock { s.getNikonEvents() }
            } catch (_: Exception) {
                continue
            }

            for ((code, param) in events) {
                if (code == PtpConstants.EVENT_DEVICE_PROP_CHANGED) {
                    refreshSingleProperty(param)
                }
            }
        }
    }

    private suspend fun refreshSingleProperty(propCode: Int) {
        val s = session ?: return
        val current = _properties.value
        val updated = when (propCode) {
            PtpConstants.PROP_BATTERY_LEVEL -> current.copy(
                batteryLevel = tryGet(current.batteryLevel) { ptpMutex.withLock { s.decodeUint8(s.getDevicePropValue(propCode)) } }
            )
            PtpConstants.PROP_EXPOSURE_PROGRAM_MODE -> current.copy(
                exposureProgramMode = tryGet(current.exposureProgramMode) { ptpMutex.withLock { s.decodeUint16(s.getDevicePropValue(propCode)) } }
            )
            PtpConstants.PROP_F_NUMBER -> current.copy(
                fNumber = tryGet(current.fNumber) { ptpMutex.withLock { s.decodeUint16(s.getDevicePropValue(propCode)) } }
            )
            PtpConstants.PROP_NIKON_EXPOSURE_TIME -> current.copy(
                nikonExposureTime = tryGet(current.nikonExposureTime) { ptpMutex.withLock { s.decodeUint32(s.getDevicePropValue(propCode)) } }
            )
            PtpConstants.PROP_EXPOSURE_INDEX -> current.copy(
                isoValue = tryGet(current.isoValue) { ptpMutex.withLock { s.decodeUintFlex(s.getDevicePropValue(propCode)) } }
            )
            PtpConstants.PROP_NIKON_ISO_EX -> current.copy(
                isoValue = tryGet(current.isoValue) { ptpMutex.withLock { s.decodeUint32(s.getDevicePropValue(propCode)) } }
            )
            PtpConstants.PROP_EXPOSURE_BIAS -> current.copy(
                exposureBias = tryGet(current.exposureBias) { ptpMutex.withLock { s.decodeIntFlex(s.getDevicePropValue(propCode)) } }
            )
            PtpConstants.PROP_WHITE_BALANCE -> current.copy(
                whiteBalance = tryGet(current.whiteBalance) { ptpMutex.withLock { s.decodeUint16(s.getDevicePropValue(propCode)) } }
            )
            PtpConstants.PROP_EXPOSURE_METERING_MODE -> current.copy(
                meteringMode = tryGet(current.meteringMode) { ptpMutex.withLock { s.decodeUint16(s.getDevicePropValue(propCode)) } }
            )
            PtpConstants.PROP_NIKON_FOCUS_MODE -> current.copy(
                focusModeNikon = tryGet(current.focusModeNikon) { ptpMutex.withLock { s.decodeUint8(s.getDevicePropValue(propCode)) } }
            )
            else -> return
        }
        _properties.value = updated
    }

    // ── Object browser ────────────────────────────────────────────────────────

    // Throws on PTP error so BrowserViewModel can surface the failure in the UI.
    suspend fun listImages(): List<PtpObjectInfo> {
        val s = session ?: return emptyList()
        return try {
            ptpMutex.withLock { s.listImages() }
        } catch (e: Exception) {
            if (e is PtpException) recoverSession()
            throw e
        }
    }

    // Returns null when thumb is unavailable; Coil will show a placeholder.
    suspend fun getThumb(handle: Long): ByteArray? {
        thumbCache[handle]?.let { return it }
        val s = session ?: return null
        return try {
            val bytes = ptpMutex.withLock { s.getThumb(handle) }
            thumbCache.put(handle, bytes)
            bytes
        } catch (e: Exception) {
            if (e is PtpException) recoverSession()
            null
        }
    }

    suspend fun getPartialObject(handle: Long, offset: Long, maxBytes: Long): ByteArray? {
        val s = session ?: return null
        return try {
            ptpMutex.withLock { s.getPartialObject(handle, offset, maxBytes) }
        } catch (e: Exception) {
            if (e is PtpException) recoverSession()
            null
        }
    }

    // Resets the session without closing the USB transport so the activationLoop
    // can re-open a PTP session on the existing connection.  Called after a
    // transport-level error (bulkOut/bulkIn failure) that leaves the USB endpoint
    // in an inconsistent state.
    private fun recoverSession() {
        if (_state.value != ConnectionState.READY) return
        _state.value = ConnectionState.USB_CONNECTED  // prevents concurrent recovery
        repoScope.launch {
            stopPolling()
            session = null
            transport?.let { t ->
                try { withContext(Dispatchers.IO) { t.drainIn() } } catch (_: Exception) {}
            }
            activationJob?.cancel()
            activationJob = repoScope.launch { activationLoop() }
        }
    }

    suspend fun downloadObject(handle: Long, out: OutputStream) {
        val s = session ?: return
        ptpMutex.withLock { s.downloadObject(handle, out) }
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private suspend fun <T> tryGet(default: T, block: suspend () -> T): T =
        try { block() } catch (_: Exception) { default }

    private fun cleanupTransport() {
        try { transport?.close() } catch (_: Exception) {}
        transport = null
        session = null
    }

    private fun UsbDevice.hasPtpInterface(): Boolean =
        (0 until interfaceCount).any { i ->
            getInterface(i).let {
                it.interfaceClass == PtpConstants.USB_CLASS_STILL_IMAGE &&
                        it.interfaceSubclass == PtpConstants.USB_SUBCLASS_STILL_IMAGE &&
                        it.interfaceProtocol == PtpConstants.USB_PROTOCOL_STILL_IMAGE
            }
        }
}
