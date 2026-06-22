package com.haecksenwerk.nico.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.materialkolor.dynamiccolor.MaterialDynamicColors
import com.materialkolor.hct.Hct
import com.materialkolor.scheme.SchemeTonalSpot

// ─── nico palette (M3 tonal spot from Nikon brand yellow seed 0xFFFFCC00) ─────

private val nicoSeed = Color(0xFFFFCC00)

val nicoLightScheme: ColorScheme by lazy { nicoColorScheme(isDark = false) }
val nicoDarkScheme: ColorScheme by lazy { nicoColorScheme(isDark = true) }

private fun nicoColorScheme(isDark: Boolean): ColorScheme {
    val hct = Hct.fromInt(nicoSeed.toArgb())
    val scheme = SchemeTonalSpot(hct, isDark, 0.0)
    val mdColors = MaterialDynamicColors()
    return ColorScheme(
        primary = Color(mdColors.primary().getArgb(scheme)),
        onPrimary = Color(mdColors.onPrimary().getArgb(scheme)),
        primaryContainer = Color(mdColors.primaryContainer().getArgb(scheme)),
        onPrimaryContainer = Color(mdColors.onPrimaryContainer().getArgb(scheme)),
        inversePrimary = Color(mdColors.inversePrimary().getArgb(scheme)),
        secondary = Color(mdColors.secondary().getArgb(scheme)),
        onSecondary = Color(mdColors.onSecondary().getArgb(scheme)),
        secondaryContainer = Color(mdColors.secondaryContainer().getArgb(scheme)),
        onSecondaryContainer = Color(mdColors.onSecondaryContainer().getArgb(scheme)),
        tertiary = Color(mdColors.tertiary().getArgb(scheme)),
        onTertiary = Color(mdColors.onTertiary().getArgb(scheme)),
        tertiaryContainer = Color(mdColors.tertiaryContainer().getArgb(scheme)),
        onTertiaryContainer = Color(mdColors.onTertiaryContainer().getArgb(scheme)),
        background = Color(mdColors.background().getArgb(scheme)),
        onBackground = Color(mdColors.onBackground().getArgb(scheme)),
        surface = Color(mdColors.surface().getArgb(scheme)),
        onSurface = Color(mdColors.onSurface().getArgb(scheme)),
        surfaceVariant = Color(mdColors.surfaceVariant().getArgb(scheme)),
        onSurfaceVariant = Color(mdColors.onSurfaceVariant().getArgb(scheme)),
        surfaceTint = Color(mdColors.surfaceTint().getArgb(scheme)),
        inverseSurface = Color(mdColors.inverseSurface().getArgb(scheme)),
        inverseOnSurface = Color(mdColors.inverseOnSurface().getArgb(scheme)),
        error = Color(mdColors.error().getArgb(scheme)),
        onError = Color(mdColors.onError().getArgb(scheme)),
        errorContainer = Color(mdColors.errorContainer().getArgb(scheme)),
        onErrorContainer = Color(mdColors.onErrorContainer().getArgb(scheme)),
        outline = Color(mdColors.outline().getArgb(scheme)),
        outlineVariant = Color(mdColors.outlineVariant().getArgb(scheme)),
        scrim = Color(mdColors.scrim().getArgb(scheme)),
        surfaceBright = Color(mdColors.surfaceBright().getArgb(scheme)),
        surfaceContainer = Color(mdColors.surfaceContainer().getArgb(scheme)),
        surfaceContainerHigh = Color(mdColors.surfaceContainerHigh().getArgb(scheme)),
        surfaceContainerHighest = Color(mdColors.surfaceContainerHighest().getArgb(scheme)),
        surfaceContainerLow = Color(mdColors.surfaceContainerLow().getArgb(scheme)),
        surfaceContainerLowest = Color(mdColors.surfaceContainerLowest().getArgb(scheme)),
        surfaceDim = Color(mdColors.surfaceDim().getArgb(scheme)),
    )
}

// ─── custom color swatch palette (10 seeds) ───────────────────────────────────

private val CUSTOM_LIGHT_SEEDS = listOf(
    Color(0xFFc75b7a), // Rose
    Color(0xFFd17b2a), // Orange
    Color(0xFF8a8520), // Olive
    Color(0xFF4e944f), // Green
    Color(0xFF179299), // Teal      ← default (index 4)
    Color(0xFF04a5e5), // Sky
    Color(0xFF209fb5), // Sapphire
    Color(0xFF2196f3), // Blue
    Color(0xFF7287fd), // Lavender
    Color(0xFF8839ef), // Mauve
)

val CUSTOM_SWATCH_COLORS: List<Color> = CUSTOM_LIGHT_SEEDS

private val CUSTOM_DARK_SEEDS = listOf(
    Color(0xFFf8bbd9), // Pink
    Color(0xFFffb386), // Peach
    Color(0xFFffd54f), // Amber
    Color(0xFFa5d6a7), // Green
    Color(0xFF94e2d5), // Teal       ← default (index 4)
    Color(0xFF89dceb), // Sky
    Color(0xFF74c7ec), // Sapphire
    Color(0xFF89b4fa), // Blue
    Color(0xFFb4befe), // Lavender
    Color(0xFFcba6f7), // Mauve
)

private const val SCHEME_CACHE_VERSION = 1
private val schemeCache = HashMap<Triple<Int, Boolean, Boolean>, Pair<Int, ColorScheme>>(40)

fun customColorScheme(seedIndex: Int, isDark: Boolean, trueBlack: Boolean): ColorScheme {
    val cacheKey = Triple(seedIndex, isDark, trueBlack)
    schemeCache[cacheKey]?.takeIf { it.first == SCHEME_CACHE_VERSION }?.let { return it.second }

    val seeds = if (isDark) CUSTOM_DARK_SEEDS else CUSTOM_LIGHT_SEEDS
    val seed = seeds.getOrElse(seedIndex) { seeds[4] }

    val hct = Hct.fromInt(seed.toArgb())
    val scheme = SchemeTonalSpot(hct, isDark, 0.0)
    val mdColors = MaterialDynamicColors()

    val colorScheme = ColorScheme(
        primary = Color(mdColors.primary().getArgb(scheme)),
        onPrimary = Color(mdColors.onPrimary().getArgb(scheme)),
        primaryContainer = Color(mdColors.primaryContainer().getArgb(scheme)),
        onPrimaryContainer = Color(mdColors.onPrimaryContainer().getArgb(scheme)),
        inversePrimary = Color(mdColors.inversePrimary().getArgb(scheme)),
        secondary = Color(mdColors.secondary().getArgb(scheme)),
        onSecondary = Color(mdColors.onSecondary().getArgb(scheme)),
        secondaryContainer = Color(mdColors.secondaryContainer().getArgb(scheme)),
        onSecondaryContainer = Color(mdColors.onSecondaryContainer().getArgb(scheme)),
        tertiary = Color(mdColors.tertiary().getArgb(scheme)),
        onTertiary = Color(mdColors.onTertiary().getArgb(scheme)),
        tertiaryContainer = Color(mdColors.tertiaryContainer().getArgb(scheme)),
        onTertiaryContainer = Color(mdColors.onTertiaryContainer().getArgb(scheme)),
        background = Color(mdColors.background().getArgb(scheme)),
        onBackground = Color(mdColors.onBackground().getArgb(scheme)),
        surface = Color(mdColors.surface().getArgb(scheme)),
        onSurface = Color(mdColors.onSurface().getArgb(scheme)),
        surfaceVariant = Color(mdColors.surfaceVariant().getArgb(scheme)),
        onSurfaceVariant = Color(mdColors.onSurfaceVariant().getArgb(scheme)),
        surfaceTint = Color(mdColors.surfaceTint().getArgb(scheme)),
        inverseSurface = Color(mdColors.inverseSurface().getArgb(scheme)),
        inverseOnSurface = Color(mdColors.inverseOnSurface().getArgb(scheme)),
        error = Color(mdColors.error().getArgb(scheme)),
        onError = Color(mdColors.onError().getArgb(scheme)),
        errorContainer = Color(mdColors.errorContainer().getArgb(scheme)),
        onErrorContainer = Color(mdColors.onErrorContainer().getArgb(scheme)),
        outline = Color(mdColors.outline().getArgb(scheme)),
        outlineVariant = Color(mdColors.outlineVariant().getArgb(scheme)),
        scrim = Color(mdColors.scrim().getArgb(scheme)),
        surfaceBright = Color(mdColors.surfaceBright().getArgb(scheme)),
        surfaceContainer = Color(mdColors.surfaceContainer().getArgb(scheme)),
        surfaceContainerHigh = Color(mdColors.surfaceContainerHigh().getArgb(scheme)),
        surfaceContainerHighest = Color(mdColors.surfaceContainerHighest().getArgb(scheme)),
        surfaceContainerLow = Color(mdColors.surfaceContainerLow().getArgb(scheme)),
        surfaceContainerLowest = Color(mdColors.surfaceContainerLowest().getArgb(scheme)),
        surfaceDim = Color(mdColors.surfaceDim().getArgb(scheme)),
    )

    val result = if (trueBlack) applyTrueBlack(colorScheme, isDark) else colorScheme
    schemeCache[cacheKey] = Pair(SCHEME_CACHE_VERSION, result)
    return result
}

fun applyTrueBlack(base: ColorScheme, isDark: Boolean): ColorScheme = if (!isDark) base else base.copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceContainerLowest = Color(0xFF080808),
    surfaceContainerLow = Color(0xFF0F0F0F),
    surfaceContainer = Color(0xFF141414),
    surfaceContainerHigh = Color(0xFF1A1A1A),
    surfaceContainerHighest = Color(0xFF1F1F1F),
)
