package com.haecksenwerk.nico.camera

import android.graphics.BitmapFactory
import android.hardware.usb.UsbDevice
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.haecksenwerk.nico.domain.PeakingColor
import com.haecksenwerk.nico.domain.PeakingSensitivity
import com.haecksenwerk.nico.ptp.PtpConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.seconds

enum class FocusState { IDLE, FOCUSING, FOCUSED, FAILED }

/**
 * Picker state for a single editable camera property.
 * [propCode] is the PTP property code, kept opaque by the UI layer.
 * [displayValues] are pre-formatted strings for each enum option.
 * [currentIndex] is the index of the camera's current value in [displayValues].
 */
data class EditableProperty(
    val propCode: Int = 0,
    val displayValues: List<String> = emptyList(),
    val currentIndex: Int = 0,
)

data class CameraUiState(
    val connectionState: ConnectionState = ConnectionState.IDLE,
    val cameraName: String = "",
    val batteryLevel: Int = 0,
    val exposureModeDisplay: String = "--",
    val fNumberDisplay: String = "--",
    val shutterDisplay: String = "--",
    val isoDisplay: String = "--",
    val exposureBiasDisplay: String = "--",
    val wbDisplay: String = "--",
    val meteringDisplay: String = "--",
    val focusModeDisplay: String = "--",
    val releaseDelaySec: Int = 0,
    val captureCountdown: Int = 0,
    val focusState: FocusState = FocusState.IDLE,
    val errorMessage: String? = null,
    val liveViewActive: Boolean = false,
    val focusPeakingEnabled: Boolean = false,
    val mfNearLimit: Boolean = false,
    val mfFarLimit: Boolean = false,
    val modeEdit:     EditableProperty = EditableProperty(),
    val isoEdit:      EditableProperty = EditableProperty(),
    val focusEdit:    EditableProperty = EditableProperty(),
    val wbEdit:       EditableProperty = EditableProperty(),
    val apertureEdit: EditableProperty = EditableProperty(),
    val shutterEdit:  EditableProperty = EditableProperty(),
    val evCompEdit:   EditableProperty = EditableProperty(),
    val meteringEdit: EditableProperty = EditableProperty(),
)

class CameraViewModel(private val repository: CameraRepository) : ViewModel() {

    private val _releaseDelay = MutableStateFlow(0)
    private val _captureCountdown = MutableStateFlow(0)
    private val _focusState = MutableStateFlow(FocusState.IDLE)
    private val _focusPeaking = MutableStateFlow(false)
    private val _peakingThreshold = MutableStateFlow(FocusPeaking.DEFAULT_THRESHOLD)
    private val _peakingColor = MutableStateFlow(FocusPeaking.COLOR_RED)
    private val _mfNearLimit = MutableStateFlow(false)
    private val _mfFarLimit = MutableStateFlow(false)
    private var afJob: Job? = null
    private var mfDriveJob: Job? = null

    private data class MfFocusBits(
        val focusState: FocusState,
        val peaking: Boolean,
        val nearLimit: Boolean,
        val farLimit: Boolean,
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    val liveViewBitmap: StateFlow<ImageBitmap?> = repository.liveViewFrame
        .mapLatest { bytes ->
            bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }
        }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * Focus-peaking overlay for the current LiveView frame, or null when peaking is off
     * or the frame cannot be processed.  Recomputed per frame on a background dispatcher;
     * stale frames are dropped via mapLatest.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val peakingOverlay: StateFlow<ImageBitmap?> =
        combine(
            repository.liveViewFrame,
            _focusPeaking,
            _peakingThreshold,
            _peakingColor,
        ) { bytes, on, threshold, color ->
            Triple(bytes.takeIf { on }, threshold, color)
        }
            .mapLatest { (bytes, threshold, color) ->
                bytes?.let { FocusPeaking.computeOverlay(it, threshold, color) }
            }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val uiState: StateFlow<CameraUiState> = combine(
        combine(repository.state, repository.properties, repository.error) { a, b, c -> Triple(a, b, c) },
        combine(repository.cameraName, _releaseDelay, _captureCountdown) { n, d, e -> Triple(n, d, e) },
        repository.propEnums,
        repository.liveViewActive,
        combine(_focusState, _focusPeaking, _mfNearLimit, _mfFarLimit) { f, p, nl, fl ->
            MfFocusBits(f, p, nl, fl)
        },
    ) { tripA, tripB, propEnums, lvActive, mfBits ->
        val (state, props, error) = tripA
        val (name, delay, countdown) = tripB
        val focusState = mfBits.focusState
        val peaking = mfBits.peaking
        CameraUiState(
            connectionState = state,
            cameraName = name,
            batteryLevel = props.batteryLevel,
            exposureModeDisplay = formatExposureMode(props.exposureProgramMode),
            fNumberDisplay = formatFNumber(props.fNumber),
            shutterDisplay = formatNikonShutter(props.nikonExposureTime),
            isoDisplay = formatIso(props.isoValue),
            exposureBiasDisplay = formatExposureBias(props.exposureBias),
            wbDisplay = formatWhiteBalance(props.whiteBalance),
            meteringDisplay = formatMetering(props.meteringMode),
            focusModeDisplay = formatFocusModeNikon(props.focusModeNikon.toLong()),
            releaseDelaySec = delay,
            captureCountdown = countdown,
            focusState = focusState,
            errorMessage = error,
            liveViewActive = lvActive,
            focusPeakingEnabled = peaking,
            mfNearLimit = mfBits.nearLimit,
            mfFarLimit = mfBits.farLimit,
            modeEdit     = EditableProperty(PtpConstants.PROP_EXPOSURE_PROGRAM_MODE),  // read-only on Z series
            isoEdit      = EditableProperty(PtpConstants.PROP_NIKON_ISO_EX),
            focusEdit    = buildEditable(PtpConstants.PROP_NIKON_FOCUS_MODE,       props.focusModeNikon.toLong(),       propEnums) { formatFocusModeNikon(it) },
            wbEdit       = buildEditable(PtpConstants.PROP_WHITE_BALANCE,          props.whiteBalance.toLong(),         propEnums) { formatWhiteBalance(it.toInt()) },
            apertureEdit = if (props.exposureProgramMode == 1 || props.exposureProgramMode == 3)
                buildEditable(PtpConstants.PROP_F_NUMBER, props.fNumber.toLong(), propEnums) { formatFNumber(it.toInt()) }
            else EditableProperty(PtpConstants.PROP_F_NUMBER),
            shutterEdit  = EditableProperty(PtpConstants.PROP_NIKON_EXPOSURE_TIME),
            evCompEdit   = EditableProperty(PtpConstants.PROP_EXPOSURE_BIAS),
            meteringEdit = buildEditable(PtpConstants.PROP_EXPOSURE_METERING_MODE, props.meteringMode.toLong(),         propEnums) { formatMetering(it.toInt()) },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CameraUiState())

    // ── UI events ─────────────────────────────────────────────────────────────

    fun onDeviceAttached(device: UsbDevice) {
        viewModelScope.launch { repository.connect(device) }
    }

    fun onDeviceDetached() {
        viewModelScope.launch { repository.disconnect() }
    }

    fun onDelaySelected(seconds: Int) {
        _releaseDelay.value = seconds
    }

    fun onLiveViewToggle() {
        viewModelScope.launch { repository.toggleLiveView() }
    }

    fun onFocusPeakingToggle() {
        _focusPeaking.value = !_focusPeaking.value
    }

    fun setPeakingSensitivity(sensitivity: PeakingSensitivity) {
        _peakingThreshold.value = when (sensitivity) {
            PeakingSensitivity.LOW -> FocusPeaking.THRESHOLD_LOW
            PeakingSensitivity.MEDIUM -> FocusPeaking.THRESHOLD_MEDIUM
            PeakingSensitivity.HIGH -> FocusPeaking.THRESHOLD_HIGH
        }
    }

    fun setPeakingColor(color: PeakingColor) {
        _peakingColor.value = when (color) {
            PeakingColor.RED -> FocusPeaking.COLOR_RED
            PeakingColor.YELLOW -> FocusPeaking.COLOR_YELLOW
            PeakingColor.BLUE -> FocusPeaking.COLOR_BLUE
            PeakingColor.WHITE -> FocusPeaking.COLOR_WHITE
        }
    }

    fun onFocusClicked() {
        if (uiState.value.connectionState != ConnectionState.READY) return
        val prev = afJob
        afJob = viewModelScope.launch {
            mfDriveJob?.cancelAndJoin()  // stop any MF drive + its DeviceReady polling first
            prev?.cancelAndJoin()   // wait for AfDriveCancel to complete before starting new AF
            _focusState.value = FocusState.FOCUSING
            val found = repository.triggerAutofocus()
            _focusState.value = if (found) FocusState.FOCUSED else FocusState.FAILED
            clearMfLimits()   // AF moved the lens; MF end-stops no longer apply
            delay(2.seconds)
            _focusState.value = FocusState.IDLE
        }
    }

    fun onAfAreaSelected(normX: Float, normY: Float) {
        if (uiState.value.connectionState != ConnectionState.READY) return
        if (!uiState.value.liveViewActive) return
        // x-range = LiveViewZoomArea max × 2 (body-specific; see PROP_NIKON_LIVE_VIEW_ZOOM_AREA).
        // y-range = x-range × (LiveView JPEG height / width) to match sensor aspect ratio.
        val xMax = repository.afAreaXMax.value.takeIf { it > 0 } ?: return
        val bm = liveViewBitmap.value
        val yMax = if (bm != null && bm.width > 0)
            (xMax.toLong() * bm.height / bm.width).toInt()
        else
            xMax * 2 / 3   // fallback: assume 3:2 sensor
        val camX = (normX * xMax).roundToInt()
        val camY = (normY * yMax).roundToInt()
        val prev = afJob
        afJob = viewModelScope.launch {
            mfDriveJob?.cancelAndJoin()  // stop any MF drive + its DeviceReady polling first
            prev?.cancelAndJoin()   // wait for AfDriveCancel to complete before ChangeAfArea + AfDrive
            repository.setAfArea(camX, camY)
            _focusState.value = FocusState.FOCUSING
            val found = repository.triggerAutofocus()
            _focusState.value = if (found) FocusState.FOCUSED else FocusState.FAILED
            clearMfLimits()   // AF moved the lens; MF end-stops no longer apply
            delay(2.seconds)
            _focusState.value = FocusState.IDLE
        }
    }

    fun onMfDrive(direction: Int, steps: Int) {
        if (uiState.value.connectionState != ConnectionState.READY) return
        if (mfDriveJob?.isActive == true) return   // drop if previous drive still in progress
        mfDriveJob = viewModelScope.launch {
            val near = direction == PtpConstants.MF_DIRECTION_NEAR
            when (repository.driveManualFocus(direction, steps)) {
                // Focus hit the near/far end — flag that direction, clear the opposite.
                MfDriveResult.LIMIT_REACHED -> {
                    _mfNearLimit.value = near
                    _mfFarLimit.value = !near
                }
                // The lens actually moved, so we are no longer parked at either end.
                MfDriveResult.MOVED -> {
                    _mfNearLimit.value = false
                    _mfFarLimit.value = false
                }
                // Step too small / no response — leave the limit flags unchanged.
                MfDriveResult.NO_MOVE -> {}
            }
        }
    }

    private fun clearMfLimits() {
        _mfNearLimit.value = false
        _mfFarLimit.value = false
    }

    fun onPropertySelected(propCode: Int, index: Int) {
        val rawValue = repository.propEnums.value[propCode]?.values?.getOrNull(index) ?: return
        viewModelScope.launch { repository.setDeviceProp(propCode, rawValue) }
    }

    fun onCaptureClicked() {
        viewModelScope.launch {
            val delaySec = _releaseDelay.value
            if (delaySec > 0) {
                _captureCountdown.value = delaySec
                var remaining = delaySec
                while (remaining > 0) {
                    delay(1.seconds)
                    remaining--
                    _captureCountdown.value = remaining
                }
            }
            repository.capture()
        }
    }

    override fun onCleared() {
        repository.close()
    }

    // ── Display formatting ────────────────────────────────────────────────────

    private fun formatFNumber(raw: Int): String {
        if (raw == 0) return "--"
        val whole = raw / 100
        val tenth = (raw % 100) / 10
        return "f/$whole.$tenth"
    }

    // Nikon 0xD100 shutter encoding: high 16 bits = numerator, low 16 bits = denominator.
    // e.g. 1/200 s → (1<<16)|200; 30 s → (30<<16)|1; Bulb → 0xFFFFFFFF.
    private fun formatNikonShutter(raw: Long): String {
        val num = ((raw shr 16) and 0xFFFF).toInt()
        val den = (raw and 0xFFFF).toInt()
        return when {
            raw == 0L           -> "--"
            raw == 0xFFFFFFFFL  -> "Bulb"
            raw == 0xFFFFFFFEL  -> "x200"
            raw == 0xFFFFFFFDL  -> "T"
            den == 1            -> "${num}\""
            else                -> "1/$den"
        }
    }

    private fun formatIso(iso: Long): String =
        if (iso == 0L) "--" else "$iso"

    private fun formatExposureMode(mode: Int): String = when (mode) {
        1 -> "M"
        2 -> "P"
        3 -> "A"
        4 -> "S"
        0x8010 -> "AUTO"
        else -> "--"
    }

    private fun formatExposureBias(biasMilliEv: Int): String {
        if (biasMilliEv == 0) return "±0"
        val sign = if (biasMilliEv > 0) "+" else "-"
        val abs = kotlin.math.abs(biasMilliEv)
        val whole = abs / 1000
        val frac = abs % 1000
        return when {
            frac == 0 -> "$sign$whole"
            frac == 333 || frac == 334 -> "$sign${whole}⅓"
            frac == 500 -> "$sign${whole}½"
            frac == 666 || frac == 667 -> "$sign${whole}⅔"
            else -> "$sign${abs / 1000.0}"
        }
    }

    private fun formatMetering(mode: Int): String = when (mode) {
        2      -> "CW"
        3      -> "Matrix"
        4      -> "Spot"
        0x8010 -> "HL-W"
        else   -> if (mode == 0) "--" else "M$mode"
    }

    private fun formatFocusModeNikon(raw: Long): String = when (raw.toInt()) {
        0 -> "AF-S"
        1 -> "AF-C"
        4 -> "MF"
        else -> if (raw == 0L) "--" else "F$raw"
    }

    private fun formatWhiteBalance(wb: Int): String = when (wb) {
        0x0002 -> "Auto"
        0x0004 -> "Daylight"
        0x0005 -> "Fluorescent"
        0x0006 -> "Tungsten"
        0x0007 -> "Flash"
        0x8010 -> "Cloudy"
        0x8011 -> "Shade"
        0x8012 -> "Choose"
        0x8013 -> "Preset"
        0x8016 -> "Neutral"
        else -> if (wb == 0) "--" else "WB $wb"
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun buildEditable(
        propCode: Int,
        currentRaw: Long,
        propEnums: Map<Int, PropDescResult>,
        formatter: (Long) -> String,
    ): EditableProperty {
        val desc = propEnums[propCode] ?: return EditableProperty(propCode)
        val idx = desc.values.indexOf(currentRaw).coerceAtLeast(0)
        return EditableProperty(
            propCode = propCode,
            displayValues = desc.values.map(formatter),
            currentIndex = idx,
        )
    }

    // ── Factory ───────────────────────────────────────────────────────────────

    class Factory(private val repository: CameraRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            CameraViewModel(repository) as T
    }
}
