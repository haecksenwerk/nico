package com.haecksenwerk.nico.ui.screens.appinfo

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppInfoScreen(
    onBack: () -> Unit,
    onNavigateToLegal: () -> Unit,
    onNavigateToLicenses: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val version = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrDefault("1.0")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("About") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                windowInsets = WindowInsets(0),
            )
        },
        modifier = modifier,
    ) { paddingValues ->
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // ─── Animated camera icon ──────────────────────────────────────
            val scaleY = remember { Animatable(1f) }
            val scaleX = remember { Animatable(1f) }

            LaunchedEffect(Unit) {
                coroutineScope {
                    launch { scaleY.animateTo(0.8f, tween(100)) }
                    launch { scaleX.animateTo(1.2f, tween(100)) }
                }
                coroutineScope {
                    launch {
                        scaleY.animateTo(1f, spring(Spring.DampingRatioHighBouncy, Spring.StiffnessLow))
                    }
                    launch {
                        scaleX.animateTo(1f, spring(Spring.DampingRatioHighBouncy, Spring.StiffnessLow))
                    }
                }
            }

            Icon(
                imageVector = Icons.Default.PhotoCamera,
                contentDescription = "NICO",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(140.dp)
                    .graphicsLayer {
                        this.scaleY = scaleY.value
                        this.scaleX = scaleX.value
                    },
            )
            Text(
                text = "N  I  C  O",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.offset(y = (-8).dp),
            )
            Spacer(modifier = Modifier.height(32.dp))

            // ─── Info rows ─────────────────────────────────────────────────
            Text(
                text = "App info",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                ),
                shape = RoundedCornerShape(24.dp),
            ) {
                InfoRow(
                    icon = Icons.AutoMirrored.Filled.Label,
                    label = "Version",
                    value = version ?: "1.0",
                )
                InfoRow(
                    icon = Icons.Default.Person,
                    label = "Author",
                    value = "haecksenwerk",
                )
                NavigationRow(
                    icon = Icons.Default.Gavel,
                    title = "Open source licenses",
                    subtitle = "Third-party software used in this app",
                    onClick = onNavigateToLicenses,
                )
                NavigationRow(
                    icon = Icons.Default.VerifiedUser,
                    title = "Legal information",
                    subtitle = "Privacy, data usage & disclaimer",
                    onClick = onNavigateToLegal,
                )
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun InfoRow(icon: ImageVector, label: String, value: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp),
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun NavigationRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp),
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
