package com.haecksenwerk.nico.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import com.haecksenwerk.nico.ui.navigation.Screen

@Composable
fun AppBottomBar(
    currentDestination: NavDestination?,
    onNavigate: (Screen) -> Unit,
) {
    NavigationBar {
        // Browser (stays selected on Detail sub-screen)
        val browserSelected = currentDestination?.hasRoute(Screen.Browser::class) == true ||
            currentDestination?.hasRoute(Screen.Detail::class) == true
        val browserScale by animateFloatAsState(
            targetValue = if (browserSelected) 1.2f else 1f,
            animationSpec = tween(300),
            label = "browser_scale",
        )
        NavigationBarItem(
            selected = browserSelected,
            onClick = { onNavigate(Screen.Browser) },
            icon = {
                Icon(
                    imageVector = if (browserSelected) Icons.Default.PhotoLibrary else Icons.Outlined.PhotoLibrary,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp).scale(browserScale),
                )
            },
            label = { Text("Photos") },
        )

        // Camera
        val cameraSelected = currentDestination?.hasRoute(Screen.Camera::class) == true
        val cameraScale = remember { Animatable(1f) }
        LaunchedEffect(cameraSelected) {
            if (cameraSelected) {
                cameraScale.animateTo(1.25f, tween(150))
                cameraScale.animateTo(0.92f, tween(130))
                cameraScale.animateTo(1f, tween(150))
            }
        }
        NavigationBarItem(
            selected = cameraSelected,
            onClick = { onNavigate(Screen.Camera) },
            icon = {
                Icon(
                    imageVector = if (cameraSelected) Icons.Default.PhotoCamera else Icons.Outlined.PhotoCamera,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp).scale(cameraScale.value),
                )
            },
            label = { Text("Camera") },
        )

        // Settings (stays selected on AppInfo / LegalInfo / Licenses sub-screens)
        val settingsSelected = currentDestination?.hasRoute(Screen.Settings::class) == true ||
            currentDestination?.hasRoute(Screen.AppInfo::class) == true ||
            currentDestination?.hasRoute(Screen.LegalInfo::class) == true ||
            currentDestination?.hasRoute(Screen.Licenses::class) == true
        val settingsRotation by animateFloatAsState(
            targetValue = if (settingsSelected) 90f else 0f,
            animationSpec = tween(500),
            label = "settings_rotation",
        )
        NavigationBarItem(
            selected = settingsSelected,
            onClick = { onNavigate(Screen.Settings) },
            icon = {
                Icon(
                    imageVector = if (settingsSelected) Icons.Default.Settings else Icons.Outlined.Settings,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp).rotate(settingsRotation),
                )
            },
            label = { Text("Settings") },
        )
    }
}
