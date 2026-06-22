package com.haecksenwerk.nico.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp),
    )
}

private class CardLayoutInfo {
    val heights = mutableListOf<Float>()
}

@Composable
fun SettingsCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val containerColor = MaterialTheme.colorScheme.surfaceContainer
    val layoutInfo = androidx.compose.runtime.remember { CardLayoutInfo() }

    Layout(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .drawBehind {
                val gap = 2.dp.toPx()
                var yPos = 0f
                val outerRadius = CornerRadius(24.dp.toPx(), 24.dp.toPx())
                val innerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())

                layoutInfo.heights.forEachIndexed { index, height ->
                    val isFirst = index == 0
                    val isLast = index == layoutInfo.heights.size - 1

                    val path = Path().apply {
                        addRoundRect(
                            RoundRect(
                                left = 0f,
                                top = yPos,
                                right = size.width,
                                bottom = yPos + height,
                                topLeftCornerRadius = if (isFirst) outerRadius else innerRadius,
                                topRightCornerRadius = if (isFirst) outerRadius else innerRadius,
                                bottomRightCornerRadius = if (isLast) outerRadius else innerRadius,
                                bottomLeftCornerRadius = if (isLast) outerRadius else innerRadius,
                            )
                        )
                    }
                    drawPath(path = path, color = containerColor)
                    yPos += height + gap
                }
            },
        content = content,
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints) }
        val gap = 2.dp.roundToPx()
        val totalHeight = placeables.sumOf { it.height } + (placeables.size - 1).coerceAtLeast(0) * gap

        layoutInfo.heights.clear()
        placeables.forEach { layoutInfo.heights.add(it.height.toFloat()) }

        layout(constraints.maxWidth, totalHeight) {
            var y = 0
            placeables.forEach { placeable ->
                placeable.place(x = 0, y = y)
                y += placeable.height + gap
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> SegmentedButtonRow(
    title: String,
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    labelOf: @Composable (T) -> String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    iconOf: (@Composable (T) -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .alpha(if (enabled) 1f else 0.38f),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = option == selected,
                    onClick = { if (enabled) onSelect(option) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                    enabled = enabled,
                    icon = {},
                    label = {
                        val label = labelOf(option)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            if (iconOf != null) {
                                iconOf(option)
                                if (label.isNotEmpty()) Spacer(modifier = Modifier.width(8.dp))
                            }
                            if (label.isNotEmpty()) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false),
                                )
                            }
                        }
                    },
                )
            }
        }
    }
}

@Composable
fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, role = Role.Switch) { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        if (leadingIcon != null) {
            Box(modifier = Modifier.padding(end = 16.dp), contentAlignment = Alignment.Center) {
                leadingIcon()
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        Box(modifier = Modifier.padding(start = 16.dp), contentAlignment = Alignment.Center) {
            Switch(checked = checked, onCheckedChange = null, enabled = enabled)
        }
    }
}

@Composable
fun ClickableRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.38f)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        if (leadingIcon != null) {
            Box(modifier = Modifier.padding(end = 16.dp), contentAlignment = Alignment.Center) {
                leadingIcon()
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        if (trailingContent != null) {
            Box(modifier = Modifier.padding(start = 16.dp), contentAlignment = Alignment.Center) {
                trailingContent()
            }
        }
    }
}
