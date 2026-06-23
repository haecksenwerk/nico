package com.haecksenwerk.nico.domain

enum class ThemeMode { LIGHT, DARK, SYSTEM }
enum class ThemeColor { NICO, DYNAMIC, CUSTOM }

data class NicoSettings(
    val liveViewOnConnect: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val themeColor: ThemeColor = ThemeColor.NICO,
    val customSourceColorIndex: Int = 4,
    val trueBlack: Boolean = false,
    val language: String = "system",
    val showFormatBadges: Boolean = true,
)
