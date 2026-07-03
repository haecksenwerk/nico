package com.haecksenwerk.nico.ui.screens.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.haecksenwerk.nico.data.SettingsDataStore
import com.haecksenwerk.nico.domain.CameraControlMode
import com.haecksenwerk.nico.domain.NicoSettings
import com.haecksenwerk.nico.domain.ThemeColor
import com.haecksenwerk.nico.domain.ThemeMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val store: SettingsDataStore) : ViewModel() {

    val settings: StateFlow<NicoSettings> = store.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NicoSettings())

    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch { store.setThemeMode(mode) }
    fun setThemeColor(color: ThemeColor) = viewModelScope.launch { store.setThemeColor(color) }
    fun setCustomColorIndex(index: Int) = viewModelScope.launch { store.setCustomColorIndex(index) }
    fun setTrueBlack(enabled: Boolean) = viewModelScope.launch { store.setTrueBlack(enabled) }
    fun setLiveViewOnConnect(enabled: Boolean) = viewModelScope.launch { store.setLiveViewOnConnect(enabled) }
    fun setShowFormatBadges(enabled: Boolean) = viewModelScope.launch { store.setShowFormatBadges(enabled) }
    fun setThumbnailsPerRow(count: Int) = viewModelScope.launch { store.setThumbnailsPerRow(count) }
    fun setCameraControlMode(mode: CameraControlMode) = viewModelScope.launch { store.setCameraControlMode(mode) }
    fun setMfStepWidth(width: Int) = viewModelScope.launch { store.setMfStepWidth(width) }

    companion object {
        fun Factory(context: Context): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return SettingsViewModel(SettingsDataStore(context.applicationContext)) as T
            }
        }
    }
}
