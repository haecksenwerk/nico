package com.haecksenwerk.nico.browser

import android.content.ContentValues
import android.content.Context
import android.util.Log
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import coil3.ImageLoader
import coil3.key.Keyer
import coil3.memory.MemoryCache
import com.haecksenwerk.nico.camera.CameraRepository
import com.haecksenwerk.nico.camera.ConnectionState
import com.haecksenwerk.nico.ptp.PtpConstants
import com.haecksenwerk.nico.ptp.PtpObjectInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class BrowserViewModel(
    private val context: Context,
    private val repository: CameraRepository,
) : ViewModel() {

    val imageLoader: ImageLoader = ImageLoader.Builder(context)
        .components {
            add(Keyer<PtpThumbKey> { data, _ -> "ptp_thumb_${data.handle}" })
            add(PtpThumbFetcher.Factory(repository))
        }
        .memoryCache {
            MemoryCache.Builder()
                .maxSizePercent(context, 0.15)
                .build()
        }
        .build()

    private val _uiState = MutableStateFlow<BrowserUiState>(BrowserUiState.NoCamera)
    val uiState: StateFlow<BrowserUiState> = _uiState.asStateFlow()

    private val _selectedHandles = MutableStateFlow<Set<Long>>(emptySet())
    val selectedHandles: StateFlow<Set<Long>> = _selectedHandles.asStateFlow()

    private val _downloadProgress = MutableStateFlow<Float?>(null)
    val downloadProgress: StateFlow<Float?> = _downloadProgress.asStateFlow()

    private val _downloadedFilenames = MutableStateFlow<Set<String>>(emptySet())
    val downloadedFilenames: StateFlow<Set<String>> = _downloadedFilenames.asStateFlow()

    init {
        viewModelScope.launch {
            repository.state.collect { state ->
                when (state) {
                    ConnectionState.READY -> loadObjects()
                    ConnectionState.IDLE, ConnectionState.USB_CONNECTED, ConnectionState.ERROR -> {
                        _uiState.value = BrowserUiState.NoCamera
                        _selectedHandles.value = emptySet()
                    }
                    else -> {}
                }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch { loadObjects() }
    }

    private suspend fun loadObjects() {
        _uiState.value = BrowserUiState.Loading
        try {
            val (items, detail) = withContext(Dispatchers.IO) {
                val all = repository.listImages()
                val fmtSummary = all.groupBy { "0x${it.objectFormat.toString(16)}" }
                    .entries.joinToString { "${it.key}×${it.value.size}" }
                    .ifEmpty { "none" }
                val sorted = all.sortedByDescending { it.captureDate }
                sorted to "${all.size} objects (formats: $fmtSummary)"
            }
            _downloadedFilenames.value = withContext(Dispatchers.IO) { queryDownloadedFilenames() }
            _uiState.value = if (items.isEmpty()) BrowserUiState.Empty(detail) else BrowserUiState.Ready(items)
        } catch (e: Exception) {
            Log.e(TAG, "loadObjects failed", e)
            _uiState.value = BrowserUiState.Error(e.message ?: "Failed to load images")
        }
    }

    fun toggleSelection(handle: Long) {
        _selectedHandles.update { current ->
            if (handle in current) current - handle else current + handle
        }
    }

    fun clearSelection() {
        _selectedHandles.value = emptySet()
    }

    /**
     * Loads a preview for the detail view.
     * JPEG files: returns the file data directly (partial or full).
     * NEF files: fetches the first 512 KB and scans for an embedded JPEG.
     */
    suspend fun getPreview(handle: Long, filename: String): ByteArray? = withContext(Dispatchers.IO) {
        // 4 MB covers any embedded JPEG preview; large-scene NEFs can have previews > 1 MB.
        val data = repository.getPartialObject(handle, 0L, 4L * 1024 * 1024) ?: return@withContext null
        if (filename.isJpeg()) data else extractLargestJpeg(data)
    }

    /**
     * Scans [data] for embedded JPEG images and returns the largest complete one.
     * Uses the three-byte SOI pattern (0xFF 0xD8 0xFF) to avoid false positives in TIFF
     * binary header data, and firstOrNull for EOI so we stop at the real end-of-image
     * rather than some later 0xFF 0xD9 sequence in adjacent binary data.
     * Falls back to the first (possibly truncated) JPEG when no complete one is found.
     */
    private fun extractLargestJpeg(data: ByteArray): ByteArray? {
        var bestStart = -1
        var bestSize = 0
        var fallbackStart = -1
        var i = 0
        while (i <= data.size - 3) {
            if (data[i] == 0xFF.toByte() && data[i + 1] == 0xD8.toByte() && data[i + 2] == 0xFF.toByte()) {
                val soiAt = i
                var eoiAt = -1
                var j = soiAt + 2
                while (j <= data.size - 2) {
                    if (data[j] == 0xFF.toByte() && data[j + 1] == 0xD9.toByte()) {
                        eoiAt = j
                        break
                    }
                    j++
                }
                if (eoiAt >= 0) {
                    val size = eoiAt + 2 - soiAt
                    if (size > bestSize) { bestStart = soiAt; bestSize = size }
                    i = eoiAt + 2
                } else {
                    if (fallbackStart < 0) fallbackStart = soiAt
                    break
                }
            } else {
                i++
            }
        }
        return when {
            bestSize > 0 -> data.copyOfRange(bestStart, bestStart + bestSize)
            fallbackStart >= 0 -> data.copyOfRange(fallbackStart, data.size)
            else -> null
        }
    }

    fun downloadSelected(items: List<PtpObjectInfo>) {
        val toDownload = items.filter { it.handle in _selectedHandles.value }
        if (toDownload.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            _downloadProgress.value = 0f
            toDownload.forEachIndexed { index, info ->
                runCatching { downloadSingle(info) }
                _downloadProgress.value = (index + 1).toFloat() / toDownload.size
            }
            _downloadedFilenames.value = queryDownloadedFilenames()
            _downloadProgress.value = null
            _selectedHandles.value = emptySet()
        }
    }

    private suspend fun downloadSingle(info: PtpObjectInfo) {
        if (info.filename in _downloadedFilenames.value) return
        val mimeType = if (info.filename.isJpeg()) "image/jpeg" else "image/x-nikon-nef"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, info.filename)
                put(MediaStore.Images.Media.MIME_TYPE, mimeType)
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/nico")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return
            try {
                resolver.openOutputStream(uri)?.use { out -> repository.downloadObject(info.handle, out) }
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                throw e
            }
        } else {
            @Suppress("DEPRECATION")
            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                "nico",
            )
            dir.mkdirs()
            val file = File(dir, info.filename)
            file.outputStream().use { out -> repository.downloadObject(info.handle, out) }
            MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), arrayOf(mimeType), null)
        }
    }

    @Suppress("DEPRECATION")
    private fun queryDownloadedFilenames(): Set<String> {
        val result = mutableSetOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val projection = arrayOf(MediaStore.Images.Media.DISPLAY_NAME)
            // LIKE handles both "Pictures/nico" and "Pictures/nico/" (MediaStore normalisation varies)
            val selection = "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?"
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                arrayOf("Pictures/nico%"),
                null,
            )?.use { cursor ->
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                while (cursor.moveToNext()) {
                    result.add(cursor.getString(nameCol))
                }
            }
        } else {
            File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                "nico",
            ).listFiles()?.forEach { result.add(it.name) }
        }
        return result
    }

    companion object {
        private const val TAG = "nico:Browser"

        private fun String.isJpeg() =
            lowercase().let { it.endsWith(".jpg") || it.endsWith(".jpeg") }

        fun Factory(context: Context, repository: CameraRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    BrowserViewModel(context.applicationContext, repository) as T
            }
    }
}
