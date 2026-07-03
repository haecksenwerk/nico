package com.haecksenwerk.nico.camera

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/**
 * On-device focus peaking: highlights the in-focus (high local contrast) regions of a
 * LiveView frame by painting their edges a solid colour on an otherwise transparent
 * overlay.  There is no camera-side focus map available over PTP, so sharpness is
 * inferred here with a Sobel gradient on luminance — the same technique mirrorless
 * bodies use for their own peaking display.
 *
 * All work runs on the calling thread; invoke from a background dispatcher.
 */
object FocusPeaking {

    // Solid overlay colours (opaque, premultiplied ARGB).
    const val COLOR_RED = 0xFFFF1744.toInt()
    const val COLOR_YELLOW = 0xFFFFEA00.toInt()
    const val COLOR_BLUE = 0xFF2979FF.toInt()
    const val COLOR_WHITE = 0xFFFFFFFF.toInt()

    /**
     * Gradient-magnitude thresholds above which a pixel counts as a sharp edge.
     * Sobel magnitude ranges 0…~1440; a lower threshold marks more (higher
     * sensitivity), a higher threshold marks only the crispest edges.
     */
    const val THRESHOLD_HIGH = 340
    const val THRESHOLD_MEDIUM = 400
    const val THRESHOLD_LOW = 460

    const val DEFAULT_THRESHOLD = THRESHOLD_MEDIUM

    /**
     * The frame is downscaled to at most this width before edge detection.  Peaking
     * does not need full resolution, and this keeps the per-frame convolution cheap
     * enough to run at LiveView frame rate.
     */
    private const val MAX_WORK_WIDTH = 480

    /**
     * Decodes [jpeg], detects sharp edges and returns a transparent overlay with those
     * edges painted [color].  Returns null if the frame cannot be decoded.
     */
    fun computeOverlay(
        jpeg: ByteArray,
        threshold: Int = DEFAULT_THRESHOLD,
        color: Int = COLOR_RED,
    ): ImageBitmap? {
        // Downscale during decode: measure first, then pick a power-of-two sample size.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= MAX_WORK_WIDTH) sample *= 2

        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bmp = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, opts) ?: return null
        val w = bmp.width
        val h = bmp.height
        if (w < 3 || h < 3) { bmp.recycle(); return null }

        val pixels = IntArray(w * h)
        bmp.getPixels(pixels, 0, w, 0, 0, w, h)
        bmp.recycle()

        // Precompute luminance (integer BT.601 approximation).
        val lum = IntArray(w * h)
        for (i in pixels.indices) {
            val p = pixels[i]
            val r = (p ushr 16) and 0xFF
            val g = (p ushr 8) and 0xFF
            val b = p and 0xFF
            lum[i] = (r * 77 + g * 150 + b * 29) ushr 8
        }

        val out = IntArray(w * h)   // transparent (0) everywhere by default
        val thr2 = threshold * threshold
        for (y in 1 until h - 1) {
            val row = y * w
            for (x in 1 until w - 1) {
                val i = row + x
                val tl = lum[i - w - 1]; val tc = lum[i - w]; val tr = lum[i - w + 1]
                val ml = lum[i - 1];                          val mr = lum[i + 1]
                val bl = lum[i + w - 1]; val bc = lum[i + w]; val br = lum[i + w + 1]
                val gx = (tr + 2 * mr + br) - (tl + 2 * ml + bl)
                val gy = (bl + 2 * bc + br) - (tl + 2 * tc + tr)
                if (gx * gx + gy * gy >= thr2) out[i] = color
            }
        }

        val overlay = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        overlay.setPixels(out, 0, w, 0, 0, w, h)
        return overlay.asImageBitmap()
    }
}
