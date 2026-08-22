package eu.emufii.app.library.switchfs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * The filesystem inside a control NCA, built here byte by byte.
 *
 * Pinning the parser matters more than usual: it runs on the *output of a
 * decryption*, so a wrong key hands it plausible-looking noise. The header
 * length check is what stands between "no icon" and a crash on a 4 GB read, and
 * that is worth a test of its own.
 */
class RomFsTest {

    private val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0x00, 0x11, 0x22)

    /** Builds a RomFS holding [files], laid out the way a real one is. */
    private fun romfs(files: List<Pair<String, ByteArray>>): ByteArray {
        val header = 0x50
        val entries = ByteArray(files.sumOf { 0x20 + pad4(it.first.length) })
        val data = ByteArray(files.sumOf { it.second.size })

        var entryPos = 0
        var dataPos = 0
        for ((name, body) in files) {
            val b = ByteBuffer.wrap(entries).order(ByteOrder.LITTLE_ENDIAN)
            b.putInt(entryPos, -1)                    // parent
            b.putInt(entryPos + 0x04, -1)             // sibling
            b.putLong(entryPos + 0x08, dataPos.toLong())
            b.putLong(entryPos + 0x10, body.size.toLong())
            b.putInt(entryPos + 0x18, -1)             // next in hash bucket
            b.putInt(entryPos + 0x1C, name.length)
            name.toByteArray().copyInto(entries, entryPos + 0x20)
            body.copyInto(data, dataPos)
            entryPos += 0x20 + pad4(name.length)
            dataPos += body.size
        }

        val out = ByteArray(header + entries.size + data.size)
        val h = ByteBuffer.wrap(out).order(ByteOrder.LITTLE_ENDIAN)
        h.putLong(0x00, header.toLong())
        h.putLong(0x38, header.toLong())              // file table offset
        h.putLong(0x40, entries.size.toLong())        // file table size
        h.putLong(0x48, (header + entries.size).toLong()) // data offset
        entries.copyInto(out, header)
        data.copyInto(out, header + entries.size)
        return out
    }

    private fun pad4(n: Int) = ((n + 3) / 4) * 4

    private fun nacp(title: String): ByteArray {
        val out = ByteArray(0x300 * 16)
        title.toByteArray().copyInto(out, 0)
        return out
    }

    @Test
    fun `the icon and the title come back out`() {
        val fs = romfs(
            listOf(
                "control.nacp" to nacp("Balatro"),
                "icon_AmericanEnglish.dat" to jpeg
            )
        )
        val control = RomFs.control(fs, listOf("AmericanEnglish"))!!
        assertEquals("Balatro", control.title)
        assertTrue(control.iconJpeg!!.contentEquals(jpeg))
    }

    @Test
    fun `the player's language wins, and something always wins`() {
        val fs = romfs(
            listOf(
                "icon_AmericanEnglish.dat" to jpeg,
                "icon_French.dat" to (jpeg + byteArrayOf(0x42))
            )
        )
        assertEquals(7, RomFs.control(fs, listOf("French"))!!.iconJpeg!!.size)
        assertEquals(6, RomFs.control(fs, listOf("German"))!!.iconJpeg!!.size)
        // Japanese-only release: no preference matches, and it still gets a tile.
        val jp = romfs(listOf("icon_Japanese.dat" to jpeg))
        assertTrue(jp.let { RomFs.control(it, listOf("French"))!!.iconJpeg!!.contentEquals(jpeg) })
    }

    @Test
    fun `a file that isn't a JPEG is not offered as one`() {
        val fs = romfs(listOf("icon_AmericanEnglish.dat" to byteArrayOf(1, 2, 3, 4, 5, 6)))
        assertNull(RomFs.control(fs, listOf("AmericanEnglish"))?.iconJpeg)
    }

    @Test
    fun `noise from a wrong key is refused, not parsed`() {
        // This is what a bad key produces: bytes of the right length that mean
        // nothing. Without the header-size check the sizes read here would be
        // astronomravings and the reader would try to allocate them.
        val noise = ByteArray(4096) { (it * 31 and 0xFF).toByte() }
        assertNull(RomFs.control(noise, listOf("AmericanEnglish")))
        assertNull(RomFs.control(ByteArray(0), listOf("AmericanEnglish")))
        assertNull(RomFs.control(ByteArray(0x10), listOf("AmericanEnglish")))
    }

    @Test
    fun `a truncated file table stops where the data stops`() {
        val fs = romfs(listOf("icon_AmericanEnglish.dat" to jpeg))
        val cut = fs.copyOfRange(0, fs.size - 2)
        // Must not throw; may or may not find the icon depending on what was cut.
        RomFs.control(cut, listOf("AmericanEnglish"))
    }
}
