package com.haecksenwerk.nico.camera

import android.graphics.BitmapFactory
import android.hardware.usb.UsbDevice
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.haecksenwerk.nico.ptp.PtpConstants
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
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
    val errorMessage: String? = null,
    val liveViewActive: Boolean = false,
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

    @OptIn(ExperimentalCoroutinesApi::class)
    val liveViewBitmap: StateFlow<ImageBitmap?> = repository.liveViewFrame
        .mapLatest { bytes ->
            bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }
        }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val uiState: StateFlow<CameraUiState> = combine(
        combine(repository.state, repository.properties, repository.error) { a, b, c -> Triple(a, b, c) },
        combine(repository.cameraName, _releaseDelay, _captureCountdown) { n, d, e -> Triple(n, d, e) },
        repository.propEnums,
        repository.liveViewActive,
    ) { (state, props, error), (name, delay, countdown), propEnums, lvActive ->
        CameraUiState(
            connectionState = state,
            cameraName = name,
            batteryLevel = props.batteryLevel,
            exposureModeDisplay = formatExposureMode(props.exposureProgramMode),
            fNumberDisplay = formatFNumber(props.fNumber),
            shutterDisplay = formatShutter(props.exposureTime),
            isoDisplay = formatIso(props.isoValue),
            exposureBiasDisplay = formatExposureBias(props.exposureBias),
            wbDisplay = formatWhiteBalance(props.whiteBalance),
            meteringDisplay = formatMetering(props.meteringMode),
            focusModeDisplay = formatFocusModeNikon(props.focusModeNikon.toLong()),
            releaseDelaySec = delay,
            captureCountdown = countdown,
            errorMessage = error,
            liveViewActive = lvActive,
            modeEdit     = EditableProperty(PtpConstants.PROP_EXPOSURE_PROGRAM_MODE),  // read-only on Z series
            isoEdit      = if (!props.autoIso)
                buildEditable(PtpConstants.PROP_NIKON_ISO_EX, props.isoValue, propEnums) { formatIso(it) }
            else EditableProperty(PtpConstants.PROP_NIKON_ISO_EX),
            focusEdit    = buildEditable(PtpConstants.PROP_NIKON_FOCUS_MODE,       props.focusModeNikon.toLong(),       propEnums) { formatFocusModeNikon(it) },
            wbEdit       = buildEditable(PtpConstants.PROP_WHITE_BALANCE,          props.whiteBalance.toLong(),         propEnums) { formatWhiteBalance(it.toInt()) },
            apertureEdit = if (props.exposureProgramMode == 1 || props.exposureProgramMode == 3)
                buildEditable(PtpConstants.PROP_F_NUMBER, props.fNumber.toLong(), propEnums) { formatFNumber(it.toInt()) }
            else EditableProperty(PtpConstants.PROP_F_NUMBER),
            shutterEdit  = EditableProperty(PtpConstants.PROP_EXPOSURE_TIME),          // read-only on Z series
            evCompEdit   = if (props.exposureProgramMode != 1 || props.autoIso)
                buildEditable(PtpConstants.PROP_EXPOSURE_BIAS, props.exposureBias.toLong(), propEnums) { formatExposureBias(it.toInt()) }
            else EditableProperty(PtpConstants.PROP_EXPOSURE_BIAS),
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
                    delay(1_000L)
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

    // PTP ExposureTime (0x500D) is in units of 0.0001 s, NOT a denominator.
    // 1/200 s is stored as 50  (50 × 0.0001 = 0.005 s)
    // 1/60 s  is stored as 167 (167 × 0.0001 ≈ 1/60 s)
    // 0xFFFFFFFF is the Bulb sentinel used by some Nikon bodies.
    private fun formatShutter(raw: Long): String = when {
        raw == 0L || raw == 0xFFFFFFFFL -> "--"
        raw >= 10_000L -> "${raw / 10_000}\""
        else -> "1/${(10_000.0 / raw).roundToInt()}"
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

    private fun formatFocusMode(mode: Int): String = when (mode) {
        1      -> "MF"
        0x8010 -> "AF-S"
        0x8011 -> "AF-C"
        0x8012 -> "AF-A"
        else   -> if (mode == 0) "--" else "F$mode"
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
