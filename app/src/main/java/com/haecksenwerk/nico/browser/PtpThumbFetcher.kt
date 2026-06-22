package com.haecksenwerk.nico.browser

import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import com.haecksenwerk.nico.camera.CameraRepository
import okio.Buffer
import okio.FileSystem

/** Opaque key carrying a PTP object handle; used as Coil request model for thumbnails. */
@JvmInline value class PtpThumbKey(val handle: Long)

class PtpThumbFetcher private constructor(
    private val key: PtpThumbKey,
    private val repository: CameraRepository,
) : Fetcher {

    override suspend fun fetch(): FetchResult? {
        val bytes = repository.getThumb(key.handle) ?: return null
        return SourceFetchResult(
            source = ImageSource(source = Buffer().write(bytes), fileSystem = FileSystem.SYSTEM),
            mimeType = "image/jpeg",
            dataSource = DataSource.DISK,
        )
    }

    class Factory(private val repository: CameraRepository) : Fetcher.Factory<PtpThumbKey> {
        override fun create(data: PtpThumbKey, options: Options, imageLoader: ImageLoader): Fetcher =
            PtpThumbFetcher(data, repository)
    }
}
