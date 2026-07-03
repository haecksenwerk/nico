package com.haecksenwerk.nico.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.haecksenwerk.nico.camera.CameraUiState
import com.haecksenwerk.nico.camera.ConnectionState
import com.haecksenwerk.nico.camera.EditableProperty
import com.haecksenwerk.nico.camera.FocusState
import com.haecksenwerk.nico.camera.FocusPeaking
import com.haecksenwerk.nico.domain.CameraControlMode
import com.haecksenwerk.nico.domain.PeakingColor
import com.haecksenwerk.nico.ptp.PtpConstants
import com.haecksenwerk.nico.ui.theme.NicoTheme
import kotlin.math.abs
import kotlin.math.roundToInt

// ── Palette (semantic / accent — same on dark and light) ─────────────────────
// Surface and text colours are resolved from MaterialTheme.colorScheme at each
// call site so they adapt automatically to dark/light and the user's chosen theme.

private val AccentYellow  = Color(0xFFFFCC00)
private val ColorGreen    = Color(0xFF4CAF50)
private val ColorRed      = Color(0xFFEF5350)
private val PastelRed     = Color(0xFFEF9A9A)
private val PastelGreen   = Color(0xFFA5D6A7)

private val RELEASE_DELAYS = listOf(0, 2, 5, 10)

// MF focus sensitivity = drive steps per wheel notch, ordered Low → Medium → High.
// Matches the values persisted in NicoSettings.mfStepWidth.
private val MF_SENS_LEVELS = listOf(60, 300, 1200)

// The peaking toggle button tints itself with the same colour the overlay uses, so the
// ARGB values stay sourced from FocusPeaking (single source of truth with the processor).
private fun PeakingColor.toOverlayColor(): Color = Color(
    when (this) {
        PeakingColor.RED -> FocusPeaking.COLOR_RED
        PeakingColor.YELLOW -> FocusPeaking.COLOR_YELLOW
        PeakingColor.BLUE -> FocusPeaking.COLOR_BLUE
        PeakingColor.WHITE -> FocusPeaking.COLOR_WHITE
    }
)

// ── Root composable ───────────────────────────────────────────────────────────

@Composable
fun CameraScreen(
    uiState: CameraUiState,
    liveViewBitmap: ImageBitmap?,
    peakingOverlay: ImageBitmap? = null,
    onCaptureClicked: () -> Unit,
    onFocusClicked: () -> Unit,
    onDelaySelected: (Int) -> Unit,
    onPropertySelected: (propCode: Int, index: Int) -> Unit,
    onLiveViewToggle: () -> Unit,
    onFocusPeakingToggle: () -> Unit = {},
    onAfAreaSelected: (Float, Float) -> Unit,
    cameraControlMode: CameraControlMode = CameraControlMode.TIMER,
    onMfDrive: (direction: Int) -> Unit = {},
    peakingColor: PeakingColor = PeakingColor.RED,
    mfSensitivity: Int = MF_SENS_LEVELS[1],
    onMfSensSelected: (Int) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val isReady = uiState.connectionState == ConnectionState.READY ||
            uiState.connectionState == ConnectionState.CAPTURING
    val peakColor = peakingColor.toOverlayColor()

    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp, vertical = 16.dp),
    ) {
        StatusBar(
            state = uiState.connectionState,
            battery = uiState.batteryLevel,
        )

        Spacer(Modifier.height(36.dp))

        if (isReady) {
            SettingsPanel(
                uiState = uiState,
                liveViewBitmap = liveViewBitmap,
                peakingOverlay = peakingOverlay,
                peakingColor = peakColor,
                onPropertySelected = onPropertySelected,
                onLiveViewToggle = onLiveViewToggle,
                onFocusPeakingToggle = onFocusPeakingToggle,
                onAfAreaSelected = onAfAreaSelected,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.weight(1f))
        } else {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                IdlePanel(uiState = uiState)
            }
        }

        Spacer(Modifier.height(16.dp))

        if (cameraControlMode == CameraControlMode.TIMER) {
            ReleaseDelaySelector(
                selected = uiState.releaseDelaySec,
                onSelected = onDelaySelected,
                enabled = uiState.connectionState == ConnectionState.READY,
            )
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                // Balances the sensitivity button on the right so the wheel stays centred.
                Spacer(Modifier.width(52.dp))
                MfWheel(
                    enabled = uiState.connectionState == ConnectionState.READY,
                    onDrive = onMfDrive,
                    nearLimit = uiState.mfNearLimit,
                    farLimit = uiState.mfFarLimit,
                    modifier = Modifier.weight(1f),
                )
                MfSensButton(
                    enabled = uiState.connectionState == ConnectionState.READY,
                    stepWidth = mfSensitivity,
                    onSelect = onMfSensSelected,
                )
            }
        }

        Spacer(Modifier.height(36.dp))

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (isReady && uiState.errorMessage != null) {
                Text(
                    text = uiState.errorMessage,
                    color = ColorRed.copy(alpha = 0.85f),
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                )
            }
            Box(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.27f)
                        .align(Alignment.CenterStart),
                    contentAlignment = Alignment.Center,
                ) {
                    FocusButton(
                        enabled = uiState.connectionState == ConnectionState.READY,
                        focusState = uiState.focusState,
                        onClick = onFocusClicked,
                    )
                }
                ShutterButton(
                    enabled = uiState.connectionState == ConnectionState.READY,
                    capturing = uiState.connectionState == ConnectionState.CAPTURING,
                    countdown = uiState.captureCountdown,
                    onClick = onCaptureClicked,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

// ── Status bar ────────────────────────────────────────────────────────────────

@Composable
private fun StatusBar(
    state: ConnectionState,
    battery: Int,
) {
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    val (label, dot) = when (state) {
        ConnectionState.IDLE -> "No camera" to secondary
        ConnectionState.DETECTING -> "Detecting…" to secondary
        ConnectionState.CONNECTING -> "Connecting…" to AccentYellow
        ConnectionState.USB_CONNECTED -> "Connected" to AccentYellow
        ConnectionState.READY -> "Active" to ColorGreen
        ConnectionState.CAPTURING -> "Capturing…" to AccentYellow
        ConnectionState.ERROR -> "Error" to ColorRed
    }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.CenterStart,
    ) {
        // Left: connection state dot + label
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 2.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(dot)
                    .padding(top = 2.dp),
            )
            Text(label, color = dot, fontSize = 13.sp,
                fontWeight = FontWeight.Medium)
        }

        // Centre: app name, always visible
        Text(
            text = "N I C O",
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 4.sp,
            modifier = Modifier.align(Alignment.Center).padding(top = 2.dp),
        )

        // Right: battery icon
        if (state == ConnectionState.READY || state == ConnectionState.CAPTURING) {
            BatteryIcon(
                level = battery,
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }
    }
}

@Composable
private fun BatteryIcon(level: Int, modifier: Modifier = Modifier) {
    val filledSegments = when {
        level >= 67 -> 3
        level >= 34 -> 2
        level >= 10 -> 1
        else -> 0
    }
    val tint = when {
        level < 15 -> ColorRed
        level < 30 -> AccentYellow
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Canvas(modifier = modifier.size(28.dp, 12.dp)) {
        val stroke = 1.5f.dp.toPx()
        val nubW = 3.dp.toPx()
        val nubH = size.height * 0.45f
        val bodyW = size.width - nubW
        val bodyH = size.height

        rotate(180f) {
            // Outlined body
            drawRoundRect(
                color = tint,
                size = Size(bodyW, bodyH),
                cornerRadius = CornerRadius(2.dp.toPx()),
                style = Stroke(stroke),
            )
            // Solid nub (now on the left after 180° rotation)
            drawRoundRect(
                color = tint,
                topLeft = Offset(bodyW, (bodyH - nubH) / 2f),
                size = Size(nubW, nubH),
                cornerRadius = CornerRadius(1.dp.toPx()),
            )

            // Inner area — pad matches the horizontal gap between segments for uniform spacing
            val gap   = 1.5f.dp.toPx()
            val pad   = gap
            val innerL = pad
            val innerT = pad
            val innerR = bodyW - pad
            val innerB = bodyH - pad
            val innerW = innerR - innerL
            val innerH = innerB - innerT
            val slant  = innerH * 0.55f

            // W  = the equal reference width (outer wide-end = middle = inner wide-end)
            // Outer segments use baseW at their narrow end; corner fill brings them up to W.
            //   W      = (innerW − 2·gap + slant) / 3
            //   baseW  = W − slant
            // Verify: baseW + gap + W + gap + W = baseW + 2W + 2·gap
            //       = (W−slant) + 2W + 2·gap = 3W − slant + 2·gap = innerW  ✓
            val w     = (innerW - 2f * gap + slant) / 3f
            val baseW = w - slant

            // Segment start positions (non-uniform because outer segs are narrower)
            val p0 = innerL
            val p1 = innerL + baseW + gap
            val p2 = p1 + w + gap

            clipRect(innerL, innerT, innerR, innerB) {
                listOf(p0 to baseW, p1 to w, p2 to baseW).forEachIndexed { i, (bx, bw) ->
                    val path = Path().apply {
                        moveTo(bx,                                  innerB)
                        lineTo(if (i == 2) innerR else bx + bw,    innerB)
                        lineTo(bx + bw + slant,                    innerT)
                        lineTo(if (i == 0) bx else bx + slant,     innerT)
                        close()
                    }
                    if (i < filledSegments) {
                        drawPath(path, color = tint)
                    } else {
                        drawPath(path, color = tint.copy(alpha = 0.3f), style = Stroke(stroke * 1.2f))
                    }
                }
            }
        }
    }
}

// ── Settings panel ────────────────────────────────────────────────────────────

@Composable
private fun SettingsPanel(
    uiState: CameraUiState,
    liveViewBitmap: ImageBitmap?,
    peakingOverlay: ImageBitmap?,
    peakingColor: Color,
    onPropertySelected: (Int, Int) -> Unit,
    onLiveViewToggle: () -> Unit,
    onFocusPeakingToggle: () -> Unit,
    onAfAreaSelected: (Float, Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        LiveViewArea(
            isActive = uiState.liveViewActive,
            bitmap = liveViewBitmap,
            peakingOverlay = peakingOverlay,
            peakingEnabled = uiState.focusPeakingEnabled,
            peakingColor = peakingColor,
            cameraName = uiState.cameraName,
            onToggle = onLiveViewToggle,
            onPeakingToggle = onFocusPeakingToggle,
            onAfAreaSelected = onAfAreaSelected,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(20.dp))

        TileRow {
            EditableTile("MODE",    uiState.exposureModeDisplay, uiState.modeEdit,    onPropertySelected, Modifier.weight(1f))
            TileGap()
            EditableTile("ISO",     uiState.isoDisplay,          uiState.isoEdit,     onPropertySelected, Modifier.weight(1f))
            TileGap()
            EditableTile("FOCUS",   uiState.focusModeDisplay,    uiState.focusEdit,   onPropertySelected, Modifier.weight(1f))
            TileGap()
            EditableTile("WB",      uiState.wbDisplay,           uiState.wbEdit,      onPropertySelected, Modifier.weight(1f))
        }

        Spacer(Modifier.height(8.dp))

        TileRow {
            EditableTile("APERTURE", uiState.fNumberDisplay,      uiState.apertureEdit, onPropertySelected, Modifier.weight(1f))
            TileGap()
            EditableTile("SHUTTER",  uiState.shutterDisplay,      uiState.shutterEdit,  onPropertySelected, Modifier.weight(1f))
            TileGap()
            EditableTile("EV COMP",  uiState.exposureBiasDisplay, uiState.evCompEdit,   onPropertySelected, Modifier.weight(1f))
            TileGap()
            EditableTile("METERING", uiState.meteringDisplay,     uiState.meteringEdit, onPropertySelected, Modifier.weight(1f))
        }
    }
}

@Composable
private fun TileRow(content: @Composable () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth()) {
        content()
    }
}

@Composable
private fun TileGap() {
    Spacer(Modifier.width(8.dp))
}

// ── Generic editable tile ─────────────────────────────────────────────────────

@Composable
private fun EditableTile(
    label: String,
    value: String,
    editProp: EditableProperty,
    onPropertySelected: (propCode: Int, index: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current

    var inEditMode by remember { mutableStateOf(false) }
    var pendingIndex by remember { mutableIntStateOf(0) }

    val displayValue = if (inEditMode && editProp.displayValues.isNotEmpty())
        editProp.displayValues.getOrElse(pendingIndex) { value }
    else value

    // Picker: 5 visible slots at 44 dp each; gesture step is 20 dp per index for
    // comfortable reach without needing a full-screen swipe.
    val slotDp = 44.dp
    val dragStepDp = 20.dp
    val pickerOffsetPx = with(density) { (slotDp * 5 + 8.dp).roundToPx() }

    Box(modifier = modifier.aspectRatio(3f / 2f)) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(8.dp))
                .background(if (inEditMode) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(horizontal = 6.dp, vertical = 4.dp)
                .pointerInput(editProp.displayValues, editProp.currentIndex) {
                    if (editProp.displayValues.isEmpty()) return@pointerInput
                    val stepPx = dragStepDp.toPx()
                    val startIndex = editProp.currentIndex

                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)

                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        inEditMode = true
                        pendingIndex = startIndex

                        var prevPos = down.position
                        val startX = down.position.x
                        var cumulativeY = 0f
                        var cancelled = false

                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                val ch = event.changes.find { it.id == down.id } ?: break
                                if (!ch.pressed) break

                                cumulativeY += ch.position.y - prevPos.y
                                prevPos = ch.position
                                ch.consume()

                                if (abs(ch.position.x - startX) > size.width.toFloat()) {
                                    cancelled = true
                                    break
                                }

                                // Drag down → higher index (larger f-number / slower shutter …)
                                val indexDelta = (cumulativeY / stepPx).roundToInt()
                                pendingIndex = (startIndex + indexDelta)
                                    .coerceIn(0, editProp.displayValues.lastIndex)
                            }
                        } finally {
                            if (!cancelled && pendingIndex != startIndex) {
                                onPropertySelected(editProp.propCode, pendingIndex)
                            }
                            inEditMode = false
                            pendingIndex = startIndex
                        }
                    }
                },
        ) {
            Text(
                text = label,
                color = if (inEditMode) AccentYellow else MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.2.sp,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = displayValue,
                color = if (inEditMode) AccentYellow else MaterialTheme.colorScheme.onSurface,
                fontSize = 16.sp,
                fontWeight = FontWeight.Light,
                textAlign = TextAlign.Center,
            )
        }

        if (inEditMode && editProp.displayValues.isNotEmpty()) {
            Popup(
                alignment = Alignment.TopCenter,
                offset = IntOffset(0, -pickerOffsetPx),
                properties = PopupProperties(clippingEnabled = false),
            ) {
                PickerWheel(labels = editProp.displayValues, selectedIndex = pendingIndex)
            }
        }
    }
}

@Composable
private fun PickerWheel(labels: List<String>, selectedIndex: Int) {
    val slotH = 44.dp

    val pickerBg = MaterialTheme.colorScheme.surfaceContainerHighest
    val pickerSelection = MaterialTheme.colorScheme.surfaceVariant
    Box(
        modifier = Modifier
            .width(130.dp)
            .height(slotH * 5)
            .clip(RoundedCornerShape(12.dp))
            .background(pickerBg),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .height(slotH)
                .background(pickerSelection),
        )

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            for (relIdx in -2..2) {
                val absIdx = selectedIndex + relIdx
                val dist = abs(relIdx)
                val itemAlpha = when (dist) { 0 -> 1f; 1 -> 0.55f; else -> 0.2f }
                val textSize = when (dist) { 0 -> 17.sp; 1 -> 13.sp; else -> 11.sp }
                val textColor = if (dist == 0) AccentYellow else MaterialTheme.colorScheme.onSurface

                Box(
                    modifier = Modifier.height(slotH).fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = labels.getOrElse(absIdx) { "" },
                        color = textColor,
                        fontSize = textSize,
                        fontWeight = if (dist == 0) FontWeight.Medium else FontWeight.Light,
                        modifier = Modifier.alpha(itemAlpha),
                    )
                }
            }
        }
    }
}

// ── Release delay selector ────────────────────────────────────────────────────

@Composable
private fun ReleaseDelaySelector(
    selected: Int,
    onSelected: (Int) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
        RELEASE_DELAYS.forEachIndexed { index, delay ->
            SegmentedButton(
                shape = SegmentedButtonDefaults.itemShape(index = index, count = RELEASE_DELAYS.size),
                onClick = { onSelected(delay) },
                selected = selected == delay,
                enabled = enabled,
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    activeContentColor = MaterialTheme.colorScheme.primary,
                    activeBorderColor = MaterialTheme.colorScheme.primary,
                    inactiveContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    inactiveContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    inactiveBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    disabledActiveContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    disabledActiveContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                    disabledActiveBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    disabledInactiveContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    disabledInactiveContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                    disabledInactiveBorderColor = MaterialTheme.colorScheme.outlineVariant,
                ),
                icon = {},
            ) {
                Text(
                    text = if (delay == 0) "OFF" else "${delay}s",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

// ── Live view ─────────────────────────────────────────────────────────────────

@Composable
private fun LiveViewArea(
    isActive: Boolean,
    bitmap: ImageBitmap?,
    peakingOverlay: ImageBitmap?,
    peakingEnabled: Boolean,
    peakingColor: Color,
    cameraName: String,
    onToggle: () -> Unit,
    onPeakingToggle: () -> Unit,
    onAfAreaSelected: (Float, Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var tapNorm by remember { mutableStateOf<Offset?>(null) }
    LaunchedEffect(tapNorm) {
        if (tapNorm != null) {
            delay(2_000)
            tapNorm = null
        }
    }
    Column(modifier = modifier) {
        // Header: LIVE VIEW label left · camera name centred · visibility button right
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = "LIVE VIEW",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.2.sp,
            )
            if (cameraName.isNotEmpty()) {
                Text(
                    text = cameraName,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 1.sp,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.align(Alignment.CenterEnd),
            ) {
                // Focus-peaking toggle — only meaningful while live view is running.
                // Shares a fixed height with the live-view toggle so the two pills match.
                if (isActive) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .height(30.dp)
                            .clip(RoundedCornerShape(10.dp))
                            // Solid chip in the peaking colour when active, with a neutral
                            // outline + contrast-picked label so light colours (e.g. white)
                            // stay visible in light theme.
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outline,
                                shape = RoundedCornerShape(10.dp),
                            )
                            .background(if (peakingEnabled) peakingColor.copy(alpha = 0.5f) else Color.Transparent)
                            .clickable(onClick = onPeakingToggle)
                            .padding(horizontal = 8.dp),
                    ) {
                        Text(
                            text = "PEAK",
                            color = when {
                                !peakingEnabled -> MaterialTheme.colorScheme.onSurfaceVariant
                                peakingColor.luminance() > 0.5f -> Color(0xFF111111)
                                else -> Color.White
                            },
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 1.sp,
                        )
                    }
                }

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .height(30.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .border(
                            width = 1.dp,
                            color = if (isActive) AccentYellow.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outline,
                            shape = RoundedCornerShape(10.dp),
                        )
                        .background(if (isActive) AccentYellow.copy(alpha = 0.15f) else Color.Transparent)
                        .clickable(onClick = onToggle)
                        .padding(horizontal = 10.dp),
                ) {
                    Icon(
                        imageVector = if (isActive) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = if (isActive) "Stop live view" else "Start live view",
                        tint = if (isActive) AccentYellow else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(17.dp),
                    )
                }
            }
        }

        // Image area — 3:2 landscape aspect ratio (height = width / 1.5)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 2f)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF0C0C0C)),
            contentAlignment = Alignment.Center,
        ) {
            when {
                isActive && bitmap != null -> {
                    Image(
                        bitmap = bitmap,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(onAfAreaSelected) {
                                detectTapGestures { offset ->
                                    val normX = offset.x / size.width
                                    val normY = offset.y / size.height
                                    tapNorm = Offset(normX, normY)
                                    onAfAreaSelected(normX, normY)
                                }
                            },
                    )
                    // Focus-peaking overlay — same aspect ratio + ContentScale.Fit as the
                    // base frame, so its edge marks land exactly over the sharp regions.
                    // FilterQuality.None keeps the upscaled edge marks crisp.
                    if (peakingEnabled && peakingOverlay != null) {
                        Image(
                            bitmap = peakingOverlay,
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            filterQuality = FilterQuality.None,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                isActive -> CircularProgressIndicator(
                    color = AccentYellow,
                    modifier = Modifier.size(28.dp),
                    strokeWidth = 2.dp,
                )
                else -> Icon(
                    imageVector = Icons.Default.PhotoCamera,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.size(52.dp),
                )
            }
            tapNorm?.let { norm ->
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val cx = norm.x * size.width
                    val cy = norm.y * size.height
                    val half = size.minDimension * 0.08f
                    val arm = half * 0.45f
                    val sw = 2.dp.toPx()
                    listOf(-1f, 1f).forEach { sx ->
                        listOf(-1f, 1f).forEach { sy ->
                            val bx = cx + sx * half
                            val by = cy + sy * half
                            drawLine(AccentYellow, Offset(bx - sx * arm, by), Offset(bx, by), strokeWidth = sw)
                            drawLine(AccentYellow, Offset(bx, by - sy * arm), Offset(bx, by), strokeWidth = sw)
                        }
                    }
                }
            }
        }
    }
}

// ── Idle / error panel ────────────────────────────────────────────────────────

@Composable
private fun IdlePanel(uiState: CameraUiState, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(
            text = "N I C O",
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 5.sp,
        )
        Text(
            text = "NIKON CONTROL",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 22.sp,
            fontWeight = FontWeight.ExtraLight,
            letterSpacing = 6.sp,
        )

        when (uiState.connectionState) {
            ConnectionState.CONNECTING -> {
                CircularProgressIndicator(
                    color = AccentYellow,
                    modifier = Modifier.size(36.dp),
                    strokeWidth = 2.dp,
                )
                Text(
                    "Opening PTP session…",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                )
            }
            ConnectionState.USB_CONNECTED -> {
                CircularProgressIndicator(
                    color = AccentYellow,
                    modifier = Modifier.size(36.dp),
                    strokeWidth = 2.dp,
                )
                Text(
                    "Switch camera on…",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                )
            }
            ConnectionState.ERROR -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .padding(16.dp),
                ) {
                    Text(
                        text = uiState.errorMessage ?: "Unknown error",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = "Disconnect the camera, ensure USB mode is PTP/MTP, then reconnect.",
                        color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.75f),
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            else -> {
                Text(
                    text = "Connect the camera via USB-C OTG\nSet USB mode to PTP/MTP on camera",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

// ── Focus button ──────────────────────────────────────────────────────────────

@Composable
private fun FocusButton(
    enabled: Boolean,
    focusState: FocusState,
    onClick: () -> Unit,
) {
    val color = if (focusState == FocusState.FOCUSED) PastelGreen else PastelRed
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(60.dp),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = color,
            contentColor = Color(0xFF111111),
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        contentPadding = PaddingValues(0.dp),
        elevation = null,
    ) {
        when (focusState) {
            FocusState.FOCUSING -> CircularProgressIndicator(
                color = Color(0xFF111111),
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
            )
            else -> Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF111111).copy(alpha = if (enabled) 0.2f else 0f)),
            )
        }
    }
}

// ── MF sensitivity button ─────────────────────────────────────────────────────
// Circular control next to the MF wheel (diameter = wheel height).  Ascending bars
// show the current level (Low/Med/High); tapping cycles to the next.

@Composable
private fun MfSensButton(
    enabled: Boolean,
    stepWidth: Int,
    onSelect: (Int) -> Unit,
) {
    // Nearest match so a legacy persisted step width still maps to a sensible level.
    val index = MF_SENS_LEVELS.indices.minByOrNull { abs(MF_SENS_LEVELS[it] - stepWidth) } ?: 1
    val level = index + 1                       // 1 (Low) … 3 (High)
    val inactive = MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(
                if (enabled) MaterialTheme.colorScheme.surfaceContainerHigh
                else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.4f)
            )
            .then(
                if (enabled)
                    Modifier.clickable { onSelect(MF_SENS_LEVELS[(index + 1) % MF_SENS_LEVELS.size]) }
                else Modifier
            ),
    ) {
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            listOf(8.dp, 13.dp, 18.dp).forEachIndexed { i, barHeight ->
                Box(
                    modifier = Modifier
                        .width(5.dp)
                        .height(barHeight)
                        .clip(RoundedCornerShape(1.5.dp))
                        .background(
                            if (i < level && enabled) AccentYellow
                            else inactive.copy(alpha = if (enabled) 0.3f else 0.15f)
                        ),
                )
            }
        }
    }
}

// ── MF focus wheel ────────────────────────────────────────────────────────────

@Composable
private fun MfWheel(
    enabled: Boolean,
    onDrive: (direction: Int) -> Unit,
    nearLimit: Boolean = false,
    farLimit: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    var cumulativeDrag by remember { mutableFloatStateOf(0f) }
    var visualOffset by remember { mutableFloatStateOf(0f) }
    val tickColor = MaterialTheme.colorScheme.onSurfaceVariant
    val tickSpacingDp = 20.dp
    // Read live inside the long-running gesture handler without restarting it.
    val nearLimitState = rememberUpdatedState(nearLimit)
    val farLimitState = rememberUpdatedState(farLimit)

    Box(
        modifier = modifier
            .height(52.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(
                if (enabled) MaterialTheme.colorScheme.surfaceContainerHigh
                else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.4f)
            )
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                val thresholdPx = tickSpacingDp.toPx()
                detectHorizontalDragGestures(
                    onDragEnd = { cumulativeDrag = 0f },
                    onDragCancel = { cumulativeDrag = 0f },
                ) { change, dragAmount ->
                    change.consume()
                    // At the near/far focus limit the wheel refuses to scroll further
                    // that way, so the ticks visibly stop turning. (drag+ = FAR, drag- = NEAR)
                    if ((dragAmount > 0f && farLimitState.value) || (dragAmount < 0f && nearLimitState.value)) {
                        cumulativeDrag = 0f
                        return@detectHorizontalDragGestures
                    }
                    visualOffset += dragAmount
                    cumulativeDrag += dragAmount
                    while (cumulativeDrag >= thresholdPx) {
                        cumulativeDrag -= thresholdPx
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onDrive(PtpConstants.MF_DIRECTION_FAR)
                    }
                    while (cumulativeDrag <= -thresholdPx) {
                        cumulativeDrag += thresholdPx
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onDrive(PtpConstants.MF_DIRECTION_NEAR)
                    }
                }
            },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val tickSpacingPx = tickSpacingDp.toPx()
            val majorSpacingPx = tickSpacingPx * 4f

            val minorOffset = ((visualOffset % tickSpacingPx) + tickSpacingPx) % tickSpacingPx
            val majorOffset = ((visualOffset % majorSpacingPx) + majorSpacingPx) % majorSpacingPx

            var x = minorOffset - tickSpacingPx
            while (x <= size.width + tickSpacingPx) {
                drawLine(
                    color = tickColor.copy(alpha = if (enabled) 0.28f else 0.12f),
                    start = Offset(x, size.height * 0.32f),
                    end   = Offset(x, size.height * 0.68f),
                    strokeWidth = 1f,
                )
                x += tickSpacingPx
            }

            x = majorOffset - majorSpacingPx
            while (x <= size.width + majorSpacingPx) {
                drawLine(
                    color = tickColor.copy(alpha = if (enabled) 0.6f else 0.25f),
                    start = Offset(x, size.height * 0.15f),
                    end   = Offset(x, size.height * 0.85f),
                    strokeWidth = 1.5f,
                )
                x += majorSpacingPx
            }

            // Fixed centre notch
            drawLine(
                color = AccentYellow.copy(alpha = if (enabled) 1f else 0.4f),
                start = Offset(size.width / 2f, 0f),
                end   = Offset(size.width / 2f, size.height),
                strokeWidth = 2f,
            )
        }
    }
}

// ── Shutter button ────────────────────────────────────────────────────────────

@Composable
private fun ShutterButton(
    enabled: Boolean,
    capturing: Boolean,
    countdown: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.size(80.dp),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = AccentYellow,
            contentColor = Color(0xFF111111),
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        contentPadding = PaddingValues(0.dp),
        elevation = null,
    ) {
        when {
            countdown > 0 -> {
                Text(
                    text = "$countdown",
                    color = Color(0xFF111111),
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            capturing -> {
                CircularProgressIndicator(
                    color = Color(0xFF111111),
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.dp,
                )
            }
            else -> {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(
                            if (enabled) Color(0xFF111111).copy(alpha = 0.2f)
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                        ),
                )
            }
        }
    }
}

// ── Preview ───────────────────────────────────────────────────────────────────

@Preview(showBackground = true, backgroundColor = 0xFF141414)
@Composable
fun PreviewReady() {
    NicoTheme {
        CameraScreen(
            uiState = CameraUiState(
                connectionState = ConnectionState.READY,
                cameraName = "Z fc",
                batteryLevel = 72,
                exposureModeDisplay = "A",
                fNumberDisplay = "f/2.8",
                shutterDisplay = "1/250",
                isoDisplay = "ISO 400",
                exposureBiasDisplay = "-⅓",
                wbDisplay = "Auto",
                meteringDisplay = "Matrix",
                focusModeDisplay = "AF-S",
                releaseDelaySec = 2,
            ),
            onCaptureClicked = {},
            onFocusClicked = {},
            onDelaySelected = {},
            onPropertySelected = { _, _ -> },
            onLiveViewToggle = {},
            onAfAreaSelected = { _, _ -> },
            liveViewBitmap = null,
            cameraControlMode = CameraControlMode.TIMER,
            onMfDrive = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF141414)
@Composable
fun PreviewMfMode() {
    NicoTheme {
        CameraScreen(
            uiState = CameraUiState(
                connectionState = ConnectionState.READY,
                cameraName = "Z fc",
                batteryLevel = 72,
                exposureModeDisplay = "M",
                fNumberDisplay = "f/1.8",
                shutterDisplay = "1/100",
                isoDisplay = "ISO 800",
                exposureBiasDisplay = "±0",
                wbDisplay = "Auto",
                meteringDisplay = "Matrix",
                focusModeDisplay = "MF",
                releaseDelaySec = 0,
            ),
            onCaptureClicked = {},
            onFocusClicked = {},
            onDelaySelected = {},
            onPropertySelected = { _, _ -> },
            onLiveViewToggle = {},
            onAfAreaSelected = { _, _ -> },
            liveViewBitmap = null,
            cameraControlMode = CameraControlMode.MF,
            onMfDrive = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF141414)
@Composable
fun PreviewIdle() {
    NicoTheme {
        CameraScreen(
            uiState = CameraUiState(connectionState = ConnectionState.IDLE),
            liveViewBitmap = null,
            onCaptureClicked = {},
            onFocusClicked = {},
            onDelaySelected = {},
            onPropertySelected = { _, _ -> },
            onLiveViewToggle = {},
            onAfAreaSelected = { _, _ -> },
        )
    }
}
