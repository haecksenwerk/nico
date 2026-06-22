package com.haecksenwerk.nico.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.haecksenwerk.nico.domain.NicoSettings
import com.haecksenwerk.nico.domain.ThemeColor
import com.haecksenwerk.nico.domain.ThemeMode

@Composable
fun isEffectiveDarkTheme(themeMode: ThemeMode): Boolean = when (themeMode) {
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
}

@Composable
fun NicoTheme(settings: NicoSettings = NicoSettings(), content: @Composable () -> Unit) {
    val isDark = isEffectiveDarkTheme(settings.themeMode)

    val colorScheme = when (settings.themeColor) {
        ThemeColor.DYNAMIC -> {
            val base = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val context = LocalContext.current
                if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            } else {
                if (isDark) nicoDarkScheme else nicoLightScheme
            }
            if (settings.trueBlack) applyTrueBlack(base, isDark) else base
        }
        ThemeColor.CUSTOM -> customColorScheme(
            seedIndex = settings.customSourceColorIndex,
            isDark = isDark,
            trueBlack = settings.trueBlack,
        )
        ThemeColor.NICO -> {
            val base = if (isDark) nicoDarkScheme else nicoLightScheme
            if (settings.trueBlack) applyTrueBlack(base, isDark) else base
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}
