package eu.emufii.app.library

import java.io.File
import java.io.RandomAccessFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

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
        assertEquals("SLES-50877", gameId)
    }

    @Test
    fun `a UMD rip in the same container answers for the PSP`() {
        val sector = requireNotNull(sector("psp.chd")) { "secteur illisible" }
        assertEquals(Console.PSP, requireNotNull(DiscImage.fromSector(sector)).first)
    }

    @Test
    fun `a Dreamcast disc is refused on its tag, before its content is believed`() {
        // Sector 16 of this fixture says PLAYSTATION: the GD-ROM tag must be checked first.
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
    fun `the sparse FLAC hunk used by DVD CHDs decodes as zero`() {
        val frame = byteArrayOf(
            0x4C, 0xFF.toByte(), 0xF8.toByte(), 0xA9.toByte(), 0x18, 0x00, 0x07,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x76, 0x46,
        )
        val decoded = requireNotNull(ChdImage.flacSilence(frame, 4096))
        assertEquals(4096, decoded.size)
        assertTrue(decoded.all { it == 0.toByte() })
    }

    @Test
    fun `a nonzero FLAC constant is refused rather than invented`() {
        val frame = byteArrayOf(
            0x4C, 0xFF.toByte(), 0xF8.toByte(), 0xA9.toByte(), 0x18, 0x00, 0x07,
            0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x76, 0x46,
        )
        assertNull(ChdImage.flacSilence(frame, 4096))
    }

    @Test
    fun `the sector rule reads a raw CD sector at its user data offset`() {
        val raw = ByteArray(2352)
        "CD001".forEachIndexed { i, c -> raw[24 + 1 + i] = c.code.toByte() }
        "PLAYSTATION".forEachIndexed { i, c -> raw[24 + 8 + i] = c.code.toByte() }
        "SLUS_201-57".forEachIndexed { i, c -> raw[24 + 40 + i] = c.code.toByte() }
        val (console, gameId) = requireNotNull(DiscImage.fromSector(raw))
        assertEquals(Console.PS2, console)
        assertEquals("SLUS-201-57", gameId)
    }

    /** Optional real-disc check, set EMUFII_CHD_PS2 / EMUFII_CHD_DREAMCAST to run it. */
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
            "sector 16 of the real disc was not decoded"
        }
        assertEquals(Console.PS2, requireNotNull(DiscImage.fromSector(sector)).first)
    }

    @Test
    fun `a real PS2 CHD yields ARMSX2 serial and ELF CRC`() {
        val path = System.getenv("EMUFII_CHD_PS2")
        val serial = System.getenv("EMUFII_CHD_PS2_SERIAL")
        val crc = System.getenv("EMUFII_CHD_PS2_CRC")
        assumeTrue(path != null && File(path).exists() && serial != null && crc != null)
        val reader = requireNotNull(ChdImage.open(fileSource(path!!))) {
            "the real CHD was not opened"
        }
        assertEquals(
            serial,
            DiscImage.ps2Serial { offset, into -> reader.read(offset, into, into.size) },
        )
        val boot = requireNotNull(Ps2DiscIdentityReader.locateBoot(reader)) {
            "SYSTEM.CNF is readable, but its BOOT2 ELF cannot be found"
        }
        assertEquals(serial, boot.serial)
        val actual = Ps2DiscIdentityReader.read(reader)
        assertEquals(Ps2DiscIdentity(serial!!, crc!!), actual)
    }

    @Test
    fun `a real Dreamcast disc is still refused`() {
        val path = System.getenv("EMUFII_CHD_DREAMCAST")
        assumeTrue(path != null && File(path).exists())
        assertNull(ChdImage.readSector(fileSource(path!!)))
    }
}
