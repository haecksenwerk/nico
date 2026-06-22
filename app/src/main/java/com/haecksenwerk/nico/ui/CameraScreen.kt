package com.haecksenwerk.nico.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.haecksenwerk.nico.ui.theme.NicoTheme
import kotlin.math.abs
import kotlin.math.roundToInt

// ── Palette ───────────────────────────────────────────────────────────────────

private val BgMain = Color(0xFF141414)
private val BgCard = Color(0xFF1E1E1E)
private val TextPrimary = Color(0xFFEEEEEE)
private val TextSecondary = Color(0xFF777777)
private val TextDim = Color(0xFF444444)
private val AccentYellow = Color(0xFFFFCC00)
private val ColorGreen = Color(0xFF4CAF50)
private val ColorRed = Color(0xFFEF5350)

private val RELEASE_DELAYS = listOf(0, 2, 5, 10)

// ── Root composable ───────────────────────────────────────────────────────────

@Composable
fun CameraScreen(
    uiState: CameraUiState,
    liveViewBitmap: ImageBitmap?,
    onCaptureClicked: () -> Unit,
    onDelaySelected: (Int) -> Unit,
    onPropertySelected: (propCode: Int, index: Int) -> Unit,
    onLiveViewToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isReady = uiState.connectionState == ConnectionState.READY ||
            uiState.connectionState == ConnectionState.CAPTURING

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
                onPropertySelected = onPropertySelected,
                onDelaySelected = onDelaySelected,
                onLiveViewToggle = onLiveViewToggle,
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

        ReleaseDelaySelector(
            selected = uiState.releaseDelaySec,
            onSelected = onDelaySelected,
            enabled = uiState.connectionState == ConnectionState.READY,
        )

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
            ShutterButton(
                enabled = uiState.connectionState == ConnectionState.READY,
                capturing = uiState.connectionState == ConnectionState.CAPTURING,
                countdown = uiState.captureCountdown,
                onClick = onCaptureClicked,
            )
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
    val (label, dot) = when (state) {
        ConnectionState.IDLE -> "No camera" to TextSecondary
        ConnectionState.DETECTING -> "Detecting…" to TextSecondary
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
            color = TextDim,
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
        else -> TextSecondary
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
    onPropertySelected: (Int, Int) -> Unit,
    onDelaySelected: (Int) -> Unit,
    onLiveViewToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        LiveViewArea(
            isActive = uiState.liveViewActive,
            bitmap = liveViewBitmap,
            cameraName = uiState.cameraName,
            onToggle = onLiveViewToggle,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(44.dp))

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

@Composable
private fun SettingTile(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(BgCard)
            .padding(horizontal = 6.dp, vertical = 12.dp),
    ) {
        Text(
            text = label,
            color = TextSecondary,
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.2.sp,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = value,
            color = TextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Light,
            textAlign = TextAlign.Center,
        )
    }
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
                .background(if (inEditMode) Color(0xFF252525) else BgCard)
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
                color = if (inEditMode) AccentYellow else TextSecondary,
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.2.sp,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = displayValue,
                color = if (inEditMode) AccentYellow else TextPrimary,
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

    Box(
        modifier = Modifier
            .width(130.dp)
            .height(slotH * 5)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF242424)),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .height(slotH)
                .background(Color(0xFF333333)),
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
                val textColor = if (dist == 0) AccentYellow else TextPrimary

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
                    inactiveContainerColor = BgCard,
                    inactiveContentColor = TextSecondary,
                    inactiveBorderColor = Color(0xFF2A2A2A),
                    disabledActiveContainerColor = Color(0xFF2C2C2C),
                    disabledActiveContentColor = TextDim,
                    disabledActiveBorderColor = Color(0xFF2A2A2A),
                    disabledInactiveContainerColor = Color(0xFF1A1A1A),
                    disabledInactiveContentColor = TextDim,
                    disabledInactiveBorderColor = Color(0xFF2A2A2A),
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
    cameraName: String,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
                color = TextSecondary,
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.2.sp,
            )
            if (cameraName.isNotEmpty()) {
                Text(
                    text = cameraName,
                    color = TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 1.sp,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isActive) AccentYellow.copy(alpha = 0.15f) else Color(0xFF252525))
                    .clickable(onClick = onToggle)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Icon(
                    imageVector = if (isActive) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                    contentDescription = if (isActive) "Stop live view" else "Start live view",
                    tint = if (isActive) AccentYellow else TextSecondary,
                    modifier = Modifier.size(14.dp),
                )
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
                isActive && bitmap != null -> Image(
                    bitmap = bitmap,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
                isActive -> CircularProgressIndicator(
                    color = AccentYellow,
                    modifier = Modifier.size(28.dp),
                    strokeWidth = 2.dp,
                )
                else -> Icon(
                    imageVector = Icons.Default.PhotoCamera,
                    contentDescription = null,
                    tint = TextDim,
                    modifier = Modifier.size(52.dp),
                )
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
            color = TextDim,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 5.sp,
        )
        Text(
            text = "NIKON CONTROL",
            color = TextSecondary,
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
                    color = TextSecondary,
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
                    color = TextSecondary,
                    fontSize = 13.sp,
                )
            }
            ConnectionState.ERROR -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF2A1A1A))
                        .padding(16.dp),
                ) {
                    Text(
                        text = uiState.errorMessage ?: "Unknown error",
                        color = ColorRed,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = "Disconnect the camera, ensure USB mode is PTP/MTP, then reconnect.",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            else -> {
                Text(
                    text = "Connect the camera via USB-C OTG\nSet USB mode to PTP/MTP on camera",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                )
            }
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
) {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.size(80.dp),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                disabledContainerColor = Color(0xFF2C2C2C),
            ),
            contentPadding = PaddingValues(0.dp),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp),
        ) {
            when {
                countdown > 0 -> {
                    Text(
                        text = "$countdown",
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                capturing -> {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.onPrimary,
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
                                if (enabled) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.25f)
                                else Color(0xFF3A3A3A)
                            ),
                    )
                }
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
            onDelaySelected = {},
            onPropertySelected = { _, _ -> },
            onLiveViewToggle = {},
            liveViewBitmap = null,
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
            onDelaySelected = {},
            onPropertySelected = { _, _ -> },
            onLiveViewToggle = {},
        )
    }
}
