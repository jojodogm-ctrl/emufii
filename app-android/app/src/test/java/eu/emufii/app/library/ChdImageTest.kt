package eu.emufii.app.library

import java.io.File
import java.io.RandomAccessFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * The one container where the extension settles nothing.
 *
 * `.chd` holds PSP, PS2 and Dreamcast games alike, and unlike an `.iso` the
 * bytes that answer the question are compressed. The three fixtures next to this
 * test are real CHDs, small ones: a v5 header, a Huffman-coded hunk map and
 * zlib hunks, built to the same layout as the two commercial files the reader
 * was measured against (a Dreamcast `Phantasy Star Online` and a PS2
 * `Unreal Tournament`).
 *
 * The direction of the rule is what these protect, as in [DiscImageTest]: a
 * wrong "yes" moves somebody's game onto an emulator that cannot open it, a
 * wrong "no" leaves it where it already was. Hence the Dreamcast fixture, which
 * is the nasty one: it carries a *PlayStation* volume descriptor behind a
 * GD-ROM tag, so anything that decoded content before checking the tag would
 * claim it for the PS2.
 */
class ChdImageTest {

    private fun source(name: String): ChdImage.Source {
        val url = requireNotNull(javaClass.classLoader?.getResource("chd/$name")) {
            "fixture chd/$name absente"
        }
        val file = RandomAccessFile(File(url.toURI()), "r")
        return object : ChdImage.Source {
            override fun read(offset: Long, into: ByteArray, count: Int): Int {
                file.seek(offset)
                var done = 0
                while (done < count) {
                    val n = file.read(into, done, count - done)
                    if (n <= 0) break
                    done += n
                }
                return done
            }
        }
    }

    private fun sector(name: String) = ChdImage.readSector(source(name))

    @Test
    fun `a PS2 disc is recognised through the compression`() {
        val sector = requireNotNull(sector("ps2.chd")) { "secteur illisible" }
        val (console, gameId) = requireNotNull(DiscImage.fromSector(sector))
        assertEquals(Console.PS2, console)
        // The disc's own number, separator included, as ARMSX2 displays it.
        assertEquals("SLES-50877", gameId)
    }

    @Test
    fun `a UMD rip in the same container is left to the PSP`() {
        val sector = requireNotNull(sector("psp.chd")) { "secteur illisible" }
        // Not "identified as PSP": identified as nothing, which is what leaves
        // the file with the console its extension gave it.
        assertNull(DiscImage.fromSector(sector))
    }

    @Test
    fun `a Dreamcast disc is refused on its tag, before its content is believed`() {
        // This fixture's sector 16 says PLAYSTATION. If the reader ever decodes
        // first and checks the GD-ROM tag second, this test is what catches it.
        assertNull(sector("dreamcast.chd"))
    }

    @Test
    fun `anything that is not a CHD says nothing at all`() {
        val notChd = object : ChdImage.Source {
            override fun read(offset: Long, into: ByteArray, count: Int): Int {
                java.util.Arrays.fill(into, 0, count, 0x41.toByte())
                return count
            }
        }
        assertNull(ChdImage.readSector(notChd))
    }

    @Test
    fun `a truncated file says nothing rather than guessing`() {
        val truncated = object : ChdImage.Source {
            override fun read(offset: Long, into: ByteArray, count: Int): Int = 0
        }
        assertNull(ChdImage.readSector(truncated))
    }

    @Test
    fun `the sector rule reads a raw CD sector at its user data offset`() {
        // A PS2 CD is pressed MODE2, so its descriptor sits 24 bytes into the
        // raw sector; measured on the real Unreal Tournament file. A reader that
        // only knew the 2048 layout would find nothing there.
        val raw = ByteArray(2352)
        "CD001".forEachIndexed { i, c -> raw[24 + 1 + i] = c.code.toByte() }
        "PLAYSTATION".forEachIndexed { i, c -> raw[24 + 8 + i] = c.code.toByte() }
        "SLUS_201-57".forEachIndexed { i, c -> raw[24 + 40 + i] = c.code.toByte() }
        val (console, gameId) = requireNotNull(DiscImage.fromSector(raw))
        assertEquals(Console.PS2, console)
        assertEquals("SLUS-201-57", gameId)
    }

    /**
     * The commercial discs, when someone points this at them.
     *
     * The fixtures above are zlib, because that is what can be forged in a few
     * hundred bytes. Real discs are `cdlz`, that is LZMA over raw CD frames, and
     * that path deserves to be run against a real file rather than trusted. It
     * is skipped when the variables are unset, so it never fails a build for
     * being on the wrong machine:
     *
     * ```
     * EMUFII_CHD_PS2=… EMUFII_CHD_DREAMCAST=… ./gradlew :app:testDebugUnitTest
     * ```
     */
    private fun fileSource(path: String): ChdImage.Source {
        val file = RandomAccessFile(File(path), "r")
        return object : ChdImage.Source {
            override fun read(offset: Long, into: ByteArray, count: Int): Int {
                file.seek(offset)
                var done = 0
                while (done < count) {
                    val n = file.read(into, done, count - done)
                    if (n <= 0) break
                    done += n
                }
                return done
            }
        }
    }

    @Test
    fun `a real PS2 disc decodes through LZMA`() {
        val path = System.getenv("EMUFII_CHD_PS2")
        assumeTrue(path != null && File(path).exists())
        val sector = requireNotNull(ChdImage.readSector(fileSource(path!!))) {
            "le secteur 16 du disque reel n'a pas ete decode"
        }
        assertEquals(Console.PS2, requireNotNull(DiscImage.fromSector(sector)).first)
    }

    @Test
    fun `a real Dreamcast disc is still refused`() {
        val path = System.getenv("EMUFII_CHD_DREAMCAST")
        assumeTrue(path != null && File(path).exists())
        assertNull(ChdImage.readSector(fileSource(path!!)))
    }
}
