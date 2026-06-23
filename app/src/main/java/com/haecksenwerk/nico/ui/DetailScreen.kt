package com.haecksenwerk.nico.ui

import android.content.Intent
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.haecksenwerk.nico.browser.BrowserUiState
import com.haecksenwerk.nico.browser.BrowserViewModel
import com.haecksenwerk.nico.browser.ExifData
import com.haecksenwerk.nico.browser.PreviewResult
import com.haecksenwerk.nico.ptp.PtpObjectInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    handle: Long,
    viewModel: BrowserViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsState()
    val info: PtpObjectInfo? = (uiState as? BrowserUiState.Ready)?.items?.find { it.handle == handle }

    var result by remember { mutableStateOf<PreviewResult?>(null) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(handle, info?.objectFormat) {
        if (info != null) {
            loading = true
            result = viewModel.getPreview(handle, info.filename)
            loading = false
        }
    }

    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 8f)
        if (scale > 1f) offset += panChange else offset = Offset.Zero
    }

    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = info?.filename ?: "",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                windowInsets = WindowInsets(0),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            val bytes = result?.imageBytes ?: return@IconButton
                            scope.launch {
                                val file = withContext(Dispatchers.IO) {
                                    File(context.cacheDir, "nico_preview.jpg")
                                        .also { it.writeBytes(bytes) }
                                }
                                val uri = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    file,
                                )
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "image/jpeg"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(intent, null))
                            }
                        },
                        enabled = result?.imageBytes != null,
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Share", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black.copy(alpha = 0.7f),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                ),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color.Black),
        ) {
            if (info != null) {
                ExifHeader(
                    captureDate = info.captureDate,
                    exifData = result?.exifData,
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                val previewBytes = result?.imageBytes
                when {
                    loading -> CircularProgressIndicator(color = Color.White)
                    previewBytes != null -> {
                        val bitmap = remember(previewBytes) {
                            BitmapFactory.decodeByteArray(previewBytes, 0, previewBytes.size)?.asImageBitmap()
                        }
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap,
                                contentDescription = info?.filename,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer(
                                        scaleX = scale,
                                        scaleY = scale,
                                        translationX = offset.x,
                                        translationY = offset.y,
                                    )
                                    .transformable(transformState),
                            )
                        } else {
                            Text("Preview not available", color = Color.White)
                        }
                    }
                    else -> Text("Preview not available", color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun ExifHeader(
    captureDate: String,
    exifData: ExifData?,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(
            text = formatCaptureDate(captureDate),
            color = Color.White.copy(alpha = 0.55f),
            fontSize = 14.sp,
            fontWeight = FontWeight.Light,
            modifier = Modifier.padding(top = 2.dp),
        )
        if (exifData != null) {
            val parts = listOfNotNull(
                exifData.shutterDisplay,
                exifData.apertureDisplay,
                exifData.focalLengthDisplay,
                exifData.isoDisplay,
            )
            if (parts.isNotEmpty()) {
                Text(
                    text = parts.joinToString(" | "),
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Light,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
        }
    }
}

private fun formatCaptureDate(raw: String): String {
    if (raw.length < 15) return raw
    return try {
        // Input: "YYYYMMDDTHHmmss"
        "${raw.substring(6, 8)}/${raw.substring(4, 6)}/${raw.substring(0, 4)}" +
            "  ${raw.substring(9, 11)}:${raw.substring(11, 13)}:${raw.substring(13, 15)}"
    } catch (_: Exception) {
        raw
    }
}
