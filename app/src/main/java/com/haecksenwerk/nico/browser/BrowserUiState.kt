package com.haecksenwerk.nico.browser

import com.haecksenwerk.nico.ptp.PtpObjectInfo

sealed class BrowserUiState {
    data object NoCamera : BrowserUiState()
    data object Loading : BrowserUiState()
    data class Empty(val detail: String = "") : BrowserUiState()
    data class Ready(val items: List<PtpObjectInfo>) : BrowserUiState()
    data class Error(val message: String) : BrowserUiState()
}
