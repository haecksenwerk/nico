package com.haecksenwerk.nico.ui.screens.appinfo

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LegalInfoScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Legal information") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                windowInsets = WindowInsets(0),
            )
        },
        contentWindowInsets = WindowInsets(0),
        modifier = modifier,
    ) { paddingValues ->
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            LegalSection(
                title = "About this app",
                body = "NICO is a free and open-source software (FOSS) project created by a hobby developer. " +
                       "The app provides remote camera control for Nikon cameras over USB using the PTP/MTP protocol. " +
                       "It is developed on a non-commercial basis. There is no commercial interest and no interest " +
                       "whatsoever in collecting, processing, or monetizing user data.",
            )
            LegalSection(
                title = "Privacy & data usage",
                body = "NICO does not track, collect, or store any personal or user-related data. " +
                       "No analytics, no telemetry, no user profiles.\n\n" +
                       "The only data stored on your device are your app settings (theme preferences, language). " +
                       "No data is transmitted to any server at any time.",
            )
            LegalSection(
                title = "USB & hardware access",
                body = "NICO communicates with your Nikon camera via the Android USB host API using the PTP " +
                       "(Picture Transfer Protocol). No data is transmitted beyond the USB connection to the " +
                       "physically connected camera. The app requires USB host permission to operate.",
            )
            LegalSection(
                title = "No warranty",
                body = "NICO is provided \"as is\" without warranty of any kind, express or implied. " +
                       "The developer makes no guarantees regarding the availability, accuracy, or reliability " +
                       "of the app. Use of the app is entirely at your own risk. The developer is not " +
                       "responsible for any damage to your camera or data caused by the use of this app.",
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun LegalSection(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = MaterialTheme.shapes.medium,
        ) {
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}
