package com.haecksenwerk.nico.domain

enum class ThemeMode { LIGHT, DARK, SYSTEM }
enum class ThemeColor { NICO, DYNAMIC, CUSTOM }
enum class CameraControlMode { TIMER, MF }
enum class PeakingSensitivity { LOW, MEDIUM, HIGH }
enum class PeakingColor { RED, YELLOW, BLUE, WHITE }

data class NicoSettings(
    val liveViewOnConnect: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val themeColor: ThemeColor = ThemeColor.NICO,
    val customSourceColorIndex: Int = 4,
    val trueBlack: Boolean = false,
    val language: String = "system",
    val showFormatBadges: Boolean = true,
    val thumbnailsPerRow: Int = 3,
    val cameraControlMode: CameraControlMode = CameraControlMode.TIMER,
    val mfStepWidth: Int = 300,
    val peakingSensitivity: PeakingSensitivity = PeakingSensitivity.MEDIUM,
    val peakingColor: PeakingColor = PeakingColor.RED,
)
