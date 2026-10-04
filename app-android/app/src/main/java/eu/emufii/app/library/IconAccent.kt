package eu.emufii.app.library

import android.graphics.Bitmap
import kotlin.math.max
import kotlin.math.min

/** Saturation-weighted average: a flat one is dominated by background. */
object IconAccent {

    /** DS icons use index 0 as transparency. */
    private const val MIN_ALPHA = 128

    private const val MIN_SATURATION = 0.18f

    fun fromPixels(pixels: IntArray): Int? {
        var weightSum = 0f
        var r = 0f
        var g = 0f
        var b = 0f

        for (pixel in pixels) {
            val alpha = (pixel ushr 24) and 0xFF
            if (alpha < MIN_ALPHA) continue

            val pr = (pixel shr 16) and 0xFF
            val pg = (pixel shr 8) and 0xFF
            val pb = pixel and 0xFF

            val maxC = max(pr, max(pg, pb))
            val minC = min(pr, min(pg, pb))
            if (maxC == 0) continue

            val saturation = (maxC - minC).toFloat() / maxC
            if (saturation < MIN_SATURATION) continue

            val weight = saturation * saturation
            weightSum += weight
            r += pr * weight
            g += pg * weight
            b += pb * weight
        }

        if (weightSum <= 0f) return null

        return argb(
            lift(r / weightSum),
            lift(g / weightSum),
            lift(b / weightSum)
        )
    }

    fun fromBitmap(bitmap: Bitmap): Int? {
        val width = minOf(bitmap.width, 48)
        val height = minOf(bitmap.height, 48)
        if (width <= 0 || height <= 0) return null
        val scaled = if (bitmap.width == width && bitmap.height == height) bitmap
        else Bitmap.createScaledBitmap(bitmap, width, height, true)
        val pixels = IntArray(width * height)
        scaled.getPixels(pixels, 0, width, 0, 0, width, height)
        if (scaled !== bitmap) scaled.recycle()
        return fromPixels(pixels)
    }

    private fun lift(channel: Float): Int {
        val boosted = 0.35f * 255f + 0.75f * channel
        return boosted.toInt().coerceIn(0, 255)
    }

    private fun argb(r: Int, g: Int, b: Int): Int =
        (0xFF shl 24) or (r shl 16) or (g shl 8) or b
}
