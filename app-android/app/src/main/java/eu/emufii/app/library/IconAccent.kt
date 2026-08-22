package eu.emufii.app.library

import android.graphics.Bitmap
import kotlin.math.max
import kotlin.math.min

/**
 * The one colour that stands for a game, pulled out of its own artwork.
 *
 * The app's chrome is deliberately neutral, grey gradient, white cards, no
 * coloured furniture, because the colour is supposed to come from the content.
 * That only works if each tile knows its own colour, so this reduces an icon to
 * a single accent used for its glow.
 *
 * Averaging the whole icon gives mud: most artwork is mostly background. So
 * pixels are weighted by saturation, which is what makes Pokémon Red read as red
 * rather than as the grey of its border.
 *
 * The maths is pure and takes an [IntArray] so it can be tested off-device.
 */
object IconAccent {

    /** Ignore near-transparent pixels: DS icons use index 0 as a hole. */
    private const val MIN_ALPHA = 128

    /** Below this, a pixel is grey and says nothing about the game's colour. */
    private const val MIN_SATURATION = 0.18f

    /**
     * A saturation-weighted average of [pixels] (ARGB_8888), or null when the
     * artwork has no colour worth borrowing, a black-and-white icon should fall
     * back to the neutral chrome rather than glow grey.
     */
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

            // Squared so a genuinely vivid pixel outvotes a dozen washed-out ones.
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
        // A 32x32 sample is plenty for an average and costs nothing: DS icons are
        // that size already, and 3DS ones are 48x48.
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

    /**
     * Pushes a dull average towards something usable as a glow. A tile's halo
     * has to be visible against a pale grey background; the honest average of an
     * icon often is not.
     */
    private fun lift(channel: Float): Int {
        val boosted = 0.35f * 255f + 0.75f * channel
        return boosted.toInt().coerceIn(0, 255)
    }

    private fun argb(r: Int, g: Int, b: Int): Int =
        (0xFF shl 24) or (r shl 16) or (g shl 8) or b
}
