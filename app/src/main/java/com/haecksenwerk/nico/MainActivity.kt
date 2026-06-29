package com.haecksenwerk.nico

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Context.USB_SERVICE
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import com.haecksenwerk.nico.browser.BrowserViewModel
import com.haecksenwerk.nico.camera.CameraRepository
import com.haecksenwerk.nico.camera.CameraViewModel
import com.haecksenwerk.nico.ptp.PtpConstants
import com.haecksenwerk.nico.ui.navigation.AppNavHost
import com.haecksenwerk.nico.ui.screens.settings.SettingsViewModel
import com.haecksenwerk.nico.ui.theme.NicoTheme

class MainActivity : ComponentActivity() {

    private val usbManager: UsbManager by lazy {
        getSystemService(USB_SERVICE) as UsbManager
    }

    private val repository: CameraRepository by lazy { CameraRepository(usbManager) }

    private val viewModel: CameraViewModel by viewModels {
        CameraViewModel.Factory(repository)
    }

    private val settingsViewModel: SettingsViewModel by viewModels {
        SettingsViewModel.Factory(this)
    }

    private val browserViewModel: BrowserViewModel by viewModels {
        BrowserViewModel.Factory(this, repository)
    }

    private val usbPermissionAction = "com.haecksenwerk.nico.USB_PERMISSION"

    // ── Broadcast receiver ────────────────────────────────────────────────────

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val device = intent.usbDevice() ?: return
            when (intent.action) {
                usbPermissionAction -> {
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        viewModel.onDeviceAttached(device)
                    }
                }
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    if (device.isNikon()) requestPermissionOrConnect(device)
                }
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    viewModel.onDeviceDetached()
                }
            }
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.BLACK),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.BLACK),
        )
        registerUsbReceiver()

        if (intent?.action == UsbManager.ACTION_USB_DEVICE_ATTACHED) {
            handleIntent(intent)
        } else {
            usbManager.deviceList.values
                .firstOrNull { it.isNikon() }
                ?.let { requestPermissionOrConnect(it) }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                settingsViewModel.settings.collect { settings ->
                    repository.liveViewOnConnect = settings.liveViewOnConnect
                }
            }
        }

        setContent {
            val settings by settingsViewModel.settings.collectAsState()
            NicoTheme(settings = settings) {
                val uiState by viewModel.uiState.collectAsState()
                val liveViewBitmap by viewModel.liveViewBitmap.collectAsState()
                AppNavHost(
                    uiState = uiState,
                    liveViewBitmap = liveViewBitmap,
                    onCaptureClicked = viewModel::onCaptureClicked,
                    onFocusClicked = viewModel::onFocusClicked,
                    onDelaySelected = viewModel::onDelaySelected,
                    onPropertySelected = viewModel::onPropertySelected,
                    onLiveViewToggle = viewModel::onLiveViewToggle,
                    settingsViewModel = settingsViewModel,
                    browserViewModel = browserViewModel,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(usbReceiver)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun handleIntent(intent: Intent) {
        if (intent.action == UsbManager.ACTION_USB_DEVICE_ATTACHED) {
            intent.usbDevice()?.takeIf { it.isNikon() }?.let { requestPermissionOrConnect(it) }
        }
    }

    private fun requestPermissionOrConnect(device: UsbDevice) {
        if (usbManager.hasPermission(device)) {
            viewModel.onDeviceAttached(device)
        } else {
            val pi = PendingIntent.getBroadcast(
                this, 0,
                Intent(usbPermissionAction),
                PendingIntent.FLAG_IMMUTABLE,
            )
            usbManager.requestPermission(device, pi)
        }
    }

    private fun registerUsbReceiver() {
        val filter = IntentFilter().apply {
            addAction(usbPermissionAction)
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(usbReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(usbReceiver, filter)
        }
    }

    private fun UsbDevice.isNikon(): Boolean = vendorId == PtpConstants.NIKON_VENDOR_ID

    @Suppress("DEPRECATION")
    private fun Intent.usbDevice(): UsbDevice? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
        else
            getParcelableExtra(UsbManager.EXTRA_DEVICE)
}
