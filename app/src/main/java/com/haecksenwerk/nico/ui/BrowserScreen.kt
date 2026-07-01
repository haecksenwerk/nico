package com.haecksenwerk.nico.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Deselect
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.haecksenwerk.nico.browser.BrowserUiState
import com.haecksenwerk.nico.browser.BrowserViewModel
import com.haecksenwerk.nico.browser.PtpThumbKey
import com.haecksenwerk.nico.ptp.PtpConstants
import com.haecksenwerk.nico.ptp.PtpObjectInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(
    viewModel: BrowserViewModel,
    onOpenDetail: (Long) -> Unit,
    showFormatBadges: Boolean = true,
    thumbnailsPerRow: Int = 3,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()
    val selected by viewModel.selectedHandles.collectAsState()
    val downloadProgress by viewModel.downloadProgress.collectAsState()
    val downloadedFilenames by viewModel.downloadedFilenames.collectAsState()
    val readyItems = (uiState as? BrowserUiState.Ready)?.items ?: emptyList()

    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = {
                    val count = selected.size
                    Text(if (count > 0) "$count selected" else "Photos")
                },
                windowInsets = WindowInsets(0),
                actions = {
                    if (uiState is BrowserUiState.Ready && readyItems.isNotEmpty()) {
                        val selectableCount = readyItems.count { it.filename !in downloadedFilenames }
                        val allSelected = selectableCount > 0 && selected.size == selectableCount
                        IconButton(onClick = { viewModel.toggleSelectAll(readyItems) }) {
                            Icon(
                                imageVector = if (allSelected) Icons.Default.Deselect else Icons.Default.SelectAll,
                                contentDescription = if (allSelected) "Deselect all" else "Select all",
                            )
                        }
                    }
                    if (uiState is BrowserUiState.Ready || uiState is BrowserUiState.Empty) {
                        IconButton(onClick = viewModel::refresh) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            if (selected.isNotEmpty() && downloadProgress == null) {
                ExtendedFloatingActionButton(
                    onClick = { viewModel.downloadSelected(readyItems) },
                    icon = { Icon(Icons.Default.Download, contentDescription = null) },
                    text = { Text("Save ${selected.size}") },
                )
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.surface),
        ) {
            when (val state = uiState) {
                is BrowserUiState.NoCamera -> EmptyPlaceholder(
                    icon = { Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                    text = "Connect a Nikon camera",
                )
                is BrowserUiState.Loading -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
                is BrowserUiState.Empty -> EmptyPlaceholder(
                    icon = { Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                    text = "No images on camera",
                    subtitle = state.detail.ifEmpty { null },
                )
                is BrowserUiState.Error -> EmptyPlaceholder(
                    icon = { Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.error) },
                    text = state.message,
                )
                is BrowserUiState.Ready -> PhotoGrid(
                    items = state.items,
                    selectedHandles = selected,
                    imageLoader = viewModel.imageLoader,
                    showFormatBadges = showFormatBadges,
                    thumbnailsPerRow = thumbnailsPerRow,
                    downloadedFilenames = downloadedFilenames,
                    onTap = { handle ->
                        if (selected.isNotEmpty()) viewModel.toggleSelection(handle)
                        else onOpenDetail(handle)
                    },
                    onLongPress = { handle -> viewModel.toggleSelection(handle) },
                )
            }

            // Download progress overlay
            AnimatedVisibility(
                visible = downloadProgress != null,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                LinearProgressIndicator(
                    progress = { downloadProgress ?: 0f },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun EmptyPlaceholder(
    icon: @Composable () -> Unit,
    text: String,
    subtitle: String? = null,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        icon()
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp, start = 32.dp, end = 32.dp),
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp, start = 32.dp, end = 32.dp),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PhotoGrid(
    items: List<PtpObjectInfo>,
    selectedHandles: Set<Long>,
    imageLoader: coil3.ImageLoader,
    showFormatBadges: Boolean,
    thumbnailsPerRow: Int,
    downloadedFilenames: Set<String>,
    onTap: (Long) -> Unit,
    onLongPress: (Long) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(thumbnailsPerRow),
        contentPadding = PaddingValues(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(items, key = { it.handle }) { info ->
            val isSelected = info.handle in selectedHandles
            Box(
                modifier = Modifier
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .combinedClickable(
                        onClick = {
                            val downloaded = info.filename in downloadedFilenames
                            if (downloaded && selectedHandles.isNotEmpty()) Unit
                            else onTap(info.handle)
                        },
                        onLongClick = {
                            if (info.filename !in downloadedFilenames) onLongPress(info.handle)
                        },
                    ),
            ) {
                AsyncImage(
                    model = PtpThumbKey(info.handle),
                    contentDescription = info.filename,
                    imageLoader = imageLoader,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )

                // Format badge — mirrors the selection overlay structure exactly.
                // The Nikon Z fc reports all image files as 0x3000 (Undefined), so
                // we derive the label from the filename extension as primary source.
                if (showFormatBadges) {
                    val formatLabel = when (info.objectFormat) {
                        PtpConstants.OBJ_FORMAT_JPEG -> "JPEG"
                        PtpConstants.OBJ_FORMAT_NEF -> "RAW"
                        else -> {
                            val ext = info.filename.substringAfterLast('.', "").uppercase()
                            when {
                                ext == "NEF" -> "RAW"
                                ext.isNotEmpty() -> ext
                                else -> "0x${info.objectFormat.toString(16).uppercase()}"
                            }
                        }
                    }
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.TopStart,
                    ) {
                        Box(
                            modifier = Modifier
                                .padding(start = 2.dp, top = 10.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(Color.Black.copy(alpha = 0.40f))
                                .padding(horizontal = 4.dp, vertical = 0.dp)
                                .heightIn(max = 13.dp),
                        ) {
                            Text(
                                text = formatLabel,
                                color = Color.White,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.SemiBold,
                                lineHeight = 13.sp,
                            )
                        }
                    }
                }

                // Downloaded badge
                if (info.filename in downloadedFilenames) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.BottomEnd,
                    ) {
                        Box(
                            modifier = Modifier
                                .padding(end = 2.dp, bottom = 2.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(Color(0xFF2E7D32).copy(alpha = 0.85f))
                                .padding(3.dp),
                        ) {
                            Icon(
                                Icons.Default.Download,
                                contentDescription = "Already downloaded",
                                tint = Color.White,
                                modifier = Modifier.size(11.dp),
                            )
                        }
                    }
                }

                // Selection overlay
                AnimatedVisibility(
                    visible = isSelected,
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.TopEnd,
                    ) {
                        Box(
                            modifier = Modifier
                                .padding(4.dp)
                                .size(20.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape)
                                .border(1.5.dp, Color.White, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(12.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
