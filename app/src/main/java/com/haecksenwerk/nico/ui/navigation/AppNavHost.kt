package com.haecksenwerk.nico.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.haecksenwerk.nico.browser.BrowserViewModel
import com.haecksenwerk.nico.camera.CameraUiState
import com.haecksenwerk.nico.ui.BrowserScreen
import com.haecksenwerk.nico.ui.CameraScreen
import com.haecksenwerk.nico.ui.DetailScreen
import com.haecksenwerk.nico.ui.components.AppBottomBar
import com.haecksenwerk.nico.ui.screens.appinfo.AppInfoScreen
import com.haecksenwerk.nico.ui.screens.appinfo.LegalInfoScreen
import com.haecksenwerk.nico.ui.screens.appinfo.LicensesScreen
import com.haecksenwerk.nico.ui.screens.settings.SettingsScreen
import com.haecksenwerk.nico.ui.screens.settings.SettingsViewModel

@Composable
fun AppNavHost(
    uiState: CameraUiState,
    liveViewBitmap: ImageBitmap?,
    onCaptureClicked: () -> Unit,
    onFocusClicked: () -> Unit,
    onDelaySelected: (Int) -> Unit,
    onPropertySelected: (Int, Int) -> Unit,
    onLiveViewToggle: () -> Unit,
    onAfAreaSelected: (Float, Float) -> Unit,
    settingsViewModel: SettingsViewModel,
    browserViewModel: BrowserViewModel,
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()
    val currentEntry by navController.currentBackStackEntryAsState()
    val currentDestination = currentEntry?.destination

    Scaffold(
        modifier = modifier,
        bottomBar = {
            AppBottomBar(
                currentDestination = currentDestination,
                onNavigate = { route ->
                    val isOnSettingsSubScreen = currentDestination?.let { dest ->
                        dest.hasRoute(Screen.AppInfo::class) ||
                            dest.hasRoute(Screen.LegalInfo::class) ||
                            dest.hasRoute(Screen.Licenses::class)
                    } == true
                    if (isOnSettingsSubScreen) {
                        navController.popBackStack(Screen.Settings, inclusive = false)
                    }

                    val isOnDetail = currentDestination?.hasRoute(Screen.Detail::class) == true
                    if (isOnDetail) {
                        navController.popBackStack(Screen.Browser, inclusive = false)
                    }

                    navController.navigate(route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            NavHost(
                navController = navController,
                startDestination = Screen.Camera,
            ) {
                composable<Screen.Camera> {
                    CameraScreen(
                        uiState = uiState,
                        liveViewBitmap = liveViewBitmap,
                        onCaptureClicked = onCaptureClicked,
                        onFocusClicked = onFocusClicked,
                        onDelaySelected = onDelaySelected,
                        onPropertySelected = onPropertySelected,
                        onLiveViewToggle = onLiveViewToggle,
                        onAfAreaSelected = onAfAreaSelected,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                composable<Screen.Browser> {
                    val settings by settingsViewModel.settings.collectAsState()
                    BrowserScreen(
                        viewModel = browserViewModel,
                        onOpenDetail = { handle -> navController.navigate(Screen.Detail(handle)) },
                        showFormatBadges = settings.showFormatBadges,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                composable<Screen.Detail> { backStackEntry ->
                    val route = backStackEntry.toRoute<Screen.Detail>()
                    DetailScreen(
                        handle = route.handle,
                        viewModel = browserViewModel,
                        onBack = { navController.popBackStack() },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                composable<Screen.Settings> {
                    SettingsScreen(
                        viewModel = settingsViewModel,
                        onNavigateToAppInfo = { navController.navigate(Screen.AppInfo) },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                composable<Screen.AppInfo> {
                    AppInfoScreen(
                        onBack = { navController.popBackStack() },
                        onNavigateToLegal = { navController.navigate(Screen.LegalInfo) },
                        onNavigateToLicenses = { navController.navigate(Screen.Licenses) },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                composable<Screen.LegalInfo> {
                    LegalInfoScreen(
                        onBack = { navController.popBackStack() },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                composable<Screen.Licenses> {
                    LicensesScreen(
                        onBack = { navController.popBackStack() },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}
