package eu.emufii.app.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IconAccentTest {

    private fun argb(a: Int, r: Int, g: Int, b: Int) =
        (a shl 24) or (r shl 16) or (g shl 8) or b

    private fun red(pixel: Int) = (pixel shr 16) and 0xFF
    private fun green(pixel: Int) = (pixel shr 8) and 0xFF
    private fun blue(pixel: Int) = pixel and 0xFF

    @Test
    fun `a mostly grey icon with a red mark comes out red`() {
        // The case that matters: artwork is mostly a neutral background, and the
        // colour that identifies the game covers a fraction of it.
        val pixels = IntArray(1000) { argb(255, 200, 200, 200) }
        for (i in 0 until 40) pixels[i] = argb(255, 220, 20, 20)

        val accent = IconAccent.fromPixels(pixels)!!

        assertTrue("red should dominate: ${accent.toUInt().toString(16)}", red(accent) > green(accent) + 60)
        assertTrue(red(accent) > blue(accent) + 60)
    }

    @Test
    fun `transparent pixels are ignored`() {
        // DS icons leave their background transparent; counting it would drag
        // every accent towards whatever the padding happens to be.
        val pixels = IntArray(100) { argb(0, 0, 0, 255) }  // invisible blue
        for (i in 0 until 10) pixels[i] = argb(255, 20, 200, 20)

        val accent = IconAccent.fromPixels(pixels)!!

        assertTrue(green(accent) > blue(accent) + 60)
    }

    @Test
    fun `a black and white icon has no accent to borrow`() {
        val pixels = IntArray(50) { argb(255, 255, 255, 255) } +
            IntArray(50) { argb(255, 0, 0, 0) } +
            IntArray(50) { argb(255, 128, 128, 128) }

        assertNull(IconAccent.fromPixels(pixels))
    }

    @Test
    fun `a fully transparent icon has no accent`() {
        assertNull(IconAccent.fromPixels(IntArray(64) { argb(0, 255, 0, 0) }))
    }

    @Test
    fun `empty input is handled`() {
        assertNull(IconAccent.fromPixels(IntArray(0)))
    }

    @Test
    fun `the accent is always opaque and in range`() {
        val pixels = IntArray(64) { argb(255, 10, 30, 200) }

        val accent = IconAccent.fromPixels(pixels)!!

        assertEquals(0xFF, (accent ushr 24) and 0xFF)
        assertTrue(red(accent) in 0..255)
        assertTrue(green(accent) in 0..255)
        assertTrue(blue(accent) in 0..255)
    }

    @Test
    fun `a dark colour is lifted enough to read as a glow`() {
        // A deep navy icon should not produce a halo indistinguishable from black.
        val pixels = IntArray(64) { argb(255, 5, 10, 60) }

        val accent = IconAccent.fromPixels(pixels)!!

        assertNotNull(accent)
        assertTrue("too dark to glow", red(accent) + green(accent) + blue(accent) > 220)
        // Still recognisably blue rather than washed to grey.
        assertTrue(blue(accent) > red(accent) + 20)
    }

    @Test
    fun `a vivid minority beats a washed-out majority`() {
        // Saturation is squared on purpose: 900 barely-tinted pixels must not
        // outvote 100 vivid ones, or every icon ends up beige.
        val pixels = IntArray(900) { argb(255, 210, 200, 190) } +
            IntArray(100) { argb(255, 0, 90, 255) }

        val accent = IconAccent.fromPixels(pixels)!!

        assertTrue("blue should win: ${accent.toUInt().toString(16)}", blue(accent) > red(accent))
    }
}
