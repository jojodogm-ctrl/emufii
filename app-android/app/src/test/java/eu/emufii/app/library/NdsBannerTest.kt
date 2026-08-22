package eu.emufii.app.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Banner decoding, checked against banners built here byte by byte. The tile
 * layout and the palette are the two things easy to get subtly wrong, a
 * transposed tile or a red/blue swap still produces a plausible-looking icon,
 * so the tests pin exact pixels rather than "it returned something".
 */
class NdsBannerTest {

    private val bitmapOffset = 0x020
    private val paletteOffset = 0x220
    private val titlesOffset = 0x240
    private val titleEntry = 0x100

    private fun banner(size: Int = 0xA40) = ByteArray(size)

    private fun ByteArray.setPalette(index: Int, r5: Int, g5: Int, b5: Int) {
        val value = (b5 shl 10) or (g5 shl 5) or r5
        this[paletteOffset + index * 2] = (value and 0xFF).toByte()
        this[paletteOffset + index * 2 + 1] = ((value shr 8) and 0xFF).toByte()
    }

    private fun ByteArray.setTitle(language: Int, text: String) {
        val bytes = text.toByteArray(Charsets.UTF_16LE)
        bytes.copyInto(this, titlesOffset + language * titleEntry)
    }

    @Test
    fun `decodes palette colours with red in the low bits`() {
        val b = banner()
        b.setPalette(1, r5 = 31, g5 = 0, b5 = 0)   // pure red
        b.setPalette(2, r5 = 0, g5 = 0, b5 = 31)   // pure blue
        // Pixel 0 of tile 0 uses index 1, pixel 1 uses index 2.
        b[bitmapOffset] = 0x21

        val pixels = NdsBanner.decodeIcon(b)!!

        assertEquals(0xFFFF0000.toInt(), pixels[0])
        assertEquals(0xFF0000FF.toInt(), pixels[1])
    }

    @Test
    fun `palette index zero is transparent, not black`() {
        val b = banner()
        b.setPalette(0, r5 = 31, g5 = 31, b5 = 31)  // even if the entry says white
        b.setPalette(1, r5 = 31, g5 = 31, b5 = 31)
        b[bitmapOffset] = 0x10  // left pixel index 0, right pixel index 1

        val pixels = NdsBanner.decodeIcon(b)!!

        assertEquals(0, pixels[0])
        assertEquals(0xFFFFFFFF.toInt(), pixels[1])
    }

    @Test
    fun `places tiles in the right corner of the icon`() {
        val b = banner()
        b.setPalette(1, r5 = 31, g5 = 0, b5 = 0)

        // Tile index 3 is the fourth tile of the top row: x 24..31, y 0..7.
        // Its first byte covers pixels (24,0) and (25,0).
        b[bitmapOffset + 3 * 32] = 0x01

        val pixels = NdsBanner.decodeIcon(b)!!

        assertEquals(0xFFFF0000.toInt(), pixels[0 * 32 + 24])
        assertEquals(0, pixels[0 * 32 + 25])
        // Nothing bled into the neighbouring tile or the row below.
        assertEquals(0, pixels[0 * 32 + 23])
        assertEquals(0, pixels[1 * 32 + 24])
    }

    @Test
    fun `places rows within a tile in order`() {
        val b = banner()
        b.setPalette(1, r5 = 0, g5 = 31, b5 = 0)

        // Tile 4 starts the second row of tiles: x 0..7, y 8..15.
        // Row 7 of that tile is y = 15.
        b[bitmapOffset + 4 * 32 + 7 * 4] = 0x01

        val pixels = NdsBanner.decodeIcon(b)!!

        assertEquals(0xFF00FF00.toInt(), pixels[15 * 32 + 0])
        assertEquals(0, pixels[14 * 32 + 0])
    }

    @Test
    fun `reads the title in the language the app is speaking`() {
        TitleLanguage.set("fr")
        val japaneseOnly = banner().apply { setTitle(0, "ポケモン") }
        assertEquals("ポケモン", NdsBanner.pickTitle(japaneseOnly))

        val withEnglish = banner().apply {
            setTitle(0, "ポケモン")
            setTitle(1, "Pokémon White 2")
        }
        assertEquals("Pokémon White 2", NdsBanner.pickTitle(withEnglish))

        val both = banner().apply {
            setTitle(0, "ポケモン")
            setTitle(1, "Pokémon White 2")
            setTitle(2, "Pokémon Version Blanche 2")
        }
        // The whole point: one cartridge, two answers, decided by the app's own
        // language rather than by a list frozen at French.
        TitleLanguage.set("fr")
        assertEquals("Pokémon Version Blanche 2", NdsBanner.pickTitle(both))
        TitleLanguage.set("en")
        assertEquals("Pokémon White 2", NdsBanner.pickTitle(both))
    }

    // The three shapes below are the exact strings dumped from the cartridges in
    // the test library, not invented ones. Getting this rule wrong is what made
    // "Pokémon White Version 2" show up in the grid as plain "Pokémon".
    @Test
    fun `joins title and subtitle but drops the publisher`() {
        val threeLines = banner().apply { setTitle(2, "Pokémon\nWhite Version 2\nNintendo") }
        assertEquals("Pokémon White Version 2", NdsBanner.pickTitle(threeLines))

        val alsoThreeLines = banner().apply { setTitle(2, "Inazuma Eleven 2\nBlizzard\nNintendo") }
        assertEquals("Inazuma Eleven 2 Blizzard", NdsBanner.pickTitle(alsoThreeLines))
    }

    @Test
    fun `treats the second line of a two-line title as the publisher`() {
        val twoLines = banner().apply { setTitle(2, "Pokémon SoulSilver\nNintendo") }

        assertEquals("Pokémon SoulSilver", NdsBanner.pickTitle(twoLines))
    }

    @Test
    fun `keeps a single-line title as it is`() {
        val oneLine = banner().apply { setTitle(2, "Mario Kart DS") }

        assertEquals("Mario Kart DS", NdsBanner.pickTitle(oneLine))
    }

    @Test
    fun `skips a language whose entry is blank`() {
        val b = banner().apply {
            setTitle(1, "Mario Kart DS")
            setTitle(2, "   \n  ")
        }

        assertEquals("Mario Kart DS", NdsBanner.pickTitle(b))
    }

    @Test
    fun `returns null rather than guessing on a truncated banner`() {
        assertNull(NdsBanner.decodeIcon(ByteArray(0x100)))
        assertNull(NdsBanner.pickTitle(ByteArray(0x240)))
    }

    @Test
    fun `reads the banner offset as little-endian and treats zero as absent`() {
        val header = ByteArray(0x200)
        header[0x068] = 0x00.toByte()
        header[0x069] = 0x82.toByte()
        header[0x06A] = 0x00.toByte()
        header[0x06B] = 0x00.toByte()

        assertEquals(0x8200L, NdsBanner.bannerOffset(header))

        assertNull(NdsBanner.bannerOffset(ByteArray(0x200)))
    }

    @Test
    fun `builds a cache key from game and maker code`() {
        val header = ByteArray(0x200)
        "POKEMON W2  ".toByteArray().copyInto(header, 0x000)
        "IRDO".toByteArray().copyInto(header, 0x00C)
        "01".toByteArray().copyInto(header, 0x010)

        assertEquals("NDS-IRDO-01", NdsBanner.cacheKey(header))
        assertEquals("POKEMON W2", NdsBanner.internalTitle(header))
    }

    @Test
    fun `cache key stays usable as a filename`() {
        val header = ByteArray(0x200)
        // Junk bytes where the codes belong, a bad dump should not produce a
        // key with a slash or a dot in it.
        header[0x00C] = 0x2F.toByte()  // '/'
        header[0x00D] = 0x2E.toByte()  // '.'
        header[0x00E] = 'A'.code.toByte()
        header[0x00F] = 'B'.code.toByte()

        val key = NdsBanner.cacheKey(header)
        assertNotNull(key)
        assertTrue(key!!.all { it.isLetterOrDigit() || it == '-' })
    }
}
