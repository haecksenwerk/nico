package com.haecksenwerk.nico.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.haecksenwerk.nico.domain.CameraControlMode
import com.haecksenwerk.nico.domain.NicoSettings
import com.haecksenwerk.nico.domain.PeakingColor
import com.haecksenwerk.nico.domain.PeakingSensitivity
import com.haecksenwerk.nico.domain.ThemeColor
import com.haecksenwerk.nico.domain.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "nico_settings")

private object Keys {
    val LIVE_VIEW_ON_CONNECT = booleanPreferencesKey("live_view_on_connect")
    val THEME_MODE = stringPreferencesKey("theme_mode")
    val THEME_COLOR = stringPreferencesKey("theme_color")
    val CUSTOM_COLOR_INDEX = intPreferencesKey("custom_color_index")
    val TRUE_BLACK = booleanPreferencesKey("true_black")
    val LANGUAGE = stringPreferencesKey("language")
    val SHOW_FORMAT_BADGES = booleanPreferencesKey("show_format_badges")
    val THUMBNAILS_PER_ROW = intPreferencesKey("thumbnails_per_row")
    val CAMERA_CONTROL_MODE = stringPreferencesKey("camera_control_mode")
    val MF_STEP_WIDTH = intPreferencesKey("mf_step_width")
    val PEAKING_SENSITIVITY = stringPreferencesKey("peaking_sensitivity")
    val PEAKING_COLOR = stringPreferencesKey("peaking_color")
}

class SettingsDataStore(private val context: Context) {

    val settingsFlow: Flow<NicoSettings> = context.dataStore.data.map { prefs ->
        NicoSettings(
            liveViewOnConnect = prefs[Keys.LIVE_VIEW_ON_CONNECT] ?: false,
            themeMode = runCatching { ThemeMode.valueOf(prefs[Keys.THEME_MODE] ?: "") }
                .getOrDefault(ThemeMode.SYSTEM),
            themeColor = runCatching { ThemeColor.valueOf(prefs[Keys.THEME_COLOR] ?: "") }
                .getOrDefault(ThemeColor.NICO),
            customSourceColorIndex = prefs[Keys.CUSTOM_COLOR_INDEX] ?: 4,
            trueBlack = prefs[Keys.TRUE_BLACK] ?: false,
            language = prefs[Keys.LANGUAGE] ?: "system",
            showFormatBadges = prefs[Keys.SHOW_FORMAT_BADGES] ?: true,
            thumbnailsPerRow = (prefs[Keys.THUMBNAILS_PER_ROW] ?: 3).coerceIn(2, 4),
            cameraControlMode = runCatching { CameraControlMode.valueOf(prefs[Keys.CAMERA_CONTROL_MODE] ?: "") }
                .getOrDefault(CameraControlMode.TIMER),
            mfStepWidth = prefs[Keys.MF_STEP_WIDTH] ?: 100,
            peakingSensitivity = runCatching { PeakingSensitivity.valueOf(prefs[Keys.PEAKING_SENSITIVITY] ?: "") }
                .getOrDefault(PeakingSensitivity.MEDIUM),
            peakingColor = runCatching { PeakingColor.valueOf(prefs[Keys.PEAKING_COLOR] ?: "") }
                .getOrDefault(PeakingColor.RED),
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) =
        context.dataStore.edit { it[Keys.THEME_MODE] = mode.name }

    suspend fun setThemeColor(color: ThemeColor) =
        context.dataStore.edit { it[Keys.THEME_COLOR] = color.name }

    suspend fun setCustomColorIndex(index: Int) =
        context.dataStore.edit { it[Keys.CUSTOM_COLOR_INDEX] = index }

    suspend fun setTrueBlack(enabled: Boolean) =
        context.dataStore.edit { it[Keys.TRUE_BLACK] = enabled }

    suspend fun setLanguage(lang: String) =
        context.dataStore.edit { it[Keys.LANGUAGE] = lang }

    suspend fun setLiveViewOnConnect(enabled: Boolean) =
        context.dataStore.edit { it[Keys.LIVE_VIEW_ON_CONNECT] = enabled }

    suspend fun setShowFormatBadges(enabled: Boolean) =
        context.dataStore.edit { it[Keys.SHOW_FORMAT_BADGES] = enabled }

    suspend fun setThumbnailsPerRow(count: Int) =
        context.dataStore.edit { it[Keys.THUMBNAILS_PER_ROW] = count.coerceIn(2, 4) }

    suspend fun setCameraControlMode(mode: CameraControlMode) =
        context.dataStore.edit { it[Keys.CAMERA_CONTROL_MODE] = mode.name }

    suspend fun setMfStepWidth(width: Int) =
        context.dataStore.edit { it[Keys.MF_STEP_WIDTH] = width }

    suspend fun setPeakingSensitivity(sensitivity: PeakingSensitivity) =
        context.dataStore.edit { it[Keys.PEAKING_SENSITIVITY] = sensitivity.name }

    suspend fun setPeakingColor(color: PeakingColor) =
        context.dataStore.edit { it[Keys.PEAKING_COLOR] = color.name }
}
