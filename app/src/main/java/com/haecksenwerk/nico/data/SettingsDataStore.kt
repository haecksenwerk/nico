package com.haecksenwerk.nico.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.haecksenwerk.nico.domain.NicoSettings
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
}
