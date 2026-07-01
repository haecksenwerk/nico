package com.haecksenwerk.nico.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.haecksenwerk.nico.domain.ThemeColor
import com.haecksenwerk.nico.domain.ThemeMode
import com.haecksenwerk.nico.ui.theme.CUSTOM_SWATCH_COLORS
import com.haecksenwerk.nico.ui.theme.isEffectiveDarkTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateToAppInfo: () -> Unit,
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier,
) {
    val settings by viewModel.settings.collectAsState()
    val isDarkThemeActive = isEffectiveDarkTheme(settings.themeMode)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                windowInsets = WindowInsets(0),
            )
        },
        contentWindowInsets = WindowInsets(0),
        modifier = modifier,
    ) { paddingValues ->
        LazyColumn(
            state = rememberLazyListState(),
            contentPadding = PaddingValues(bottom = 16.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            // ─── Camera ───────────────────────────────────────────────────────
            item {
                SectionHeader(title = "Camera")

                SettingsCard {
                    SwitchRow(
                        title = "Live view on connect",
                        subtitle = "Start camera preview automatically when connecting",
                        checked = settings.liveViewOnConnect,
                        onCheckedChange = { viewModel.setLiveViewOnConnect(it) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Videocam,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
            }

            // ─── Photos ───────────────────────────────────────────────────────
            item {
                SectionHeader(title = "Photos")

                SettingsCard {
                    SwitchRow(
                        title = "Format badges",
                        subtitle = "Show RAW/JPG label on thumbnails",
                        checked = settings.showFormatBadges,
                        onCheckedChange = { viewModel.setShowFormatBadges(it) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Label,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                    )
                    SegmentedButtonRow(
                        title = "Thumbnails per row",
                        options = listOf(2, 3, 4),
                        selected = settings.thumbnailsPerRow,
                        onSelect = { viewModel.setThumbnailsPerRow(it) },
                        labelOf = { it.toString() },
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
            }

            // ─── Appearance ───────────────────────────────────────────────────
            item {
                SectionHeader(title = "Appearance")

                SettingsCard {
                    SegmentedButtonRow(
                        title = "Theme mode",
                        options = ThemeMode.entries,
                        selected = settings.themeMode,
                        onSelect = { viewModel.setThemeMode(it) },
                        labelOf = { mode ->
                            when (mode) {
                                ThemeMode.LIGHT -> "Light"
                                ThemeMode.DARK -> "Dark"
                                ThemeMode.SYSTEM -> "System"
                            }
                        },
                        iconOf = { mode ->
                            val icon = when (mode) {
                                ThemeMode.LIGHT -> Icons.Default.LightMode
                                ThemeMode.DARK -> Icons.Default.DarkMode
                                ThemeMode.SYSTEM -> Icons.Default.SettingsSuggest
                            }
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                modifier = Modifier.size(SegmentedButtonDefaults.IconSize),
                            )
                        },
                    )
                    SwitchRow(
                        title = "True black",
                        subtitle = "Darker theme for OLED displays",
                        checked = settings.trueBlack,
                        onCheckedChange = { viewModel.setTrueBlack(it) },
                        enabled = isDarkThemeActive,
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Contrast,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                SettingsCard {
                    val colorOptions = remember {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                            ThemeColor.entries
                        } else {
                            ThemeColor.entries.filter { it != ThemeColor.DYNAMIC }
                        }
                    }
                    SegmentedButtonRow(
                        title = "Theme color",
                        options = colorOptions,
                        selected = settings.themeColor,
                        onSelect = { viewModel.setThemeColor(it) },
                        labelOf = { color ->
                            when (color) {
                                ThemeColor.NICO -> "NICO"
                                ThemeColor.DYNAMIC -> "Dynamic"
                                ThemeColor.CUSTOM -> "Colors"
                            }
                        },
                        iconOf = { color ->
                            when (color) {
                                ThemeColor.NICO -> Icon(
                                    imageVector = Icons.Default.PhotoCamera,
                                    contentDescription = null,
                                    modifier = Modifier.size(SegmentedButtonDefaults.IconSize),
                                )
                                ThemeColor.DYNAMIC -> Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    modifier = Modifier.size(SegmentedButtonDefaults.IconSize),
                                )
                                ThemeColor.CUSTOM -> Icon(
                                    imageVector = Icons.Default.Palette,
                                    contentDescription = null,
                                    modifier = Modifier.size(SegmentedButtonDefaults.IconSize),
                                )
                            }
                        },
                    )
                    AnimatedVisibility(
                        visible = settings.themeColor == ThemeColor.CUSTOM,
                        enter = expandVertically(expandFrom = Alignment.Top, animationSpec = tween(300)) +
                                slideInVertically(initialOffsetY = { -it / 2 }, animationSpec = tween(300)),
                        exit = shrinkVertically(shrinkTowards = Alignment.Top, animationSpec = tween(220)) +
                               slideOutVertically(targetOffsetY = { -it / 2 }, animationSpec = tween(220)),
                    ) {
                        ColorSwatchRow(
                            selectedIndex = settings.customSourceColorIndex,
                            onSelect = { viewModel.setCustomColorIndex(it) },
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
            }

            // ─── About ────────────────────────────────────────────────────────
            item {
                SectionHeader(title = "About")
                SettingsCard {
                    ClickableRow(
                        title = "App information",
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        onClick = onNavigateToAppInfo,
                        trailingContent = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                    )
                }
            }
        }
    }
}

// ─── Color swatch row ─────────────────────────────────────────────────────────

@Composable
private fun ColorSwatchRow(
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    val density = LocalDensity.current
    var viewportWidthPx by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(selectedIndex, viewportWidthPx) {
        if (selectedIndex >= 0 && viewportWidthPx > 0f) {
            val itemWidthPx = with(density) { (44.dp + 12.dp).toPx() }
            val itemCenterPx = selectedIndex * itemWidthPx + with(density) { 34.dp.toPx() }
            val targetScrollPx = (itemCenterPx - viewportWidthPx / 2).toInt().coerceAtLeast(0)
            scrollState.animateScrollTo(targetScrollPx)
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .onGloballyPositioned { viewportWidthPx = it.size.width.toFloat() },
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            CUSTOM_SWATCH_COLORS.forEachIndexed { index, color ->
                val isSelected = index == selectedIndex
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(44.dp)
                        .then(
                            if (isSelected)
                                Modifier.border(3.dp, color.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                            else Modifier
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onSelect(index) },
                        ),
                ) {
                    Box(
                        modifier = Modifier
                            .size(if (isSelected) 32.dp else 40.dp)
                            .clip(if (isSelected) RoundedCornerShape(8.dp) else CircleShape)
                            .background(color),
                    )
                }
            }
        }
    }
}