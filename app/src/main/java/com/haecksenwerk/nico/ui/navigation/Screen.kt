package com.haecksenwerk.nico.ui.navigation

import kotlinx.serialization.Serializable

sealed class Screen {
    @Serializable data object Camera : Screen()
    @Serializable data object Browser : Screen()
    @Serializable data object Settings : Screen()
    @Serializable data object AppInfo : Screen()
    @Serializable data object LegalInfo : Screen()
    @Serializable data object Licenses : Screen()
    @Serializable data class Detail(val handle: Long) : Screen()
}
