package eu.emufii.app.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DiscImagePs2SerialTest {

    @Test
    fun `the boot line becomes the serial as everyone else spells it`() {
        assertEquals("SLES-53717", DiscImage.bootSerial("BOOT2 = cdrom0:\\SLES_537.17;1\n"))
        assertEquals("SLUS-21355", DiscImage.bootSerial("BOOT2=cdrom0:\\SLUS_213.55;1"))
    }

    @Test
    fun `the rest of the file does not get in the way`() {
        val cnf = """
            BOOT2 = cdrom0:\SLES_508.77;1
            VER = 1.00
            VMODE = PAL
        """.trimIndent()
        assertEquals("SLES-50877", DiscImage.bootSerial(cnf))
    }

    @Test
    fun `a boot file that is not a serial yields nothing`() {
        assertNull(DiscImage.bootSerial("BOOT2 = cdrom0:\\MYHOMEBREW.ELF;1"))
        assertNull(DiscImage.bootSerial("VER = 1.00"))
        assertNull(DiscImage.bootSerial(""))
    }

    @Test
    fun `a PS1 disc is not answered for`() {
        // PS1 uses BOOT, not BOOT2.
        assertNull(DiscImage.bootSerial("BOOT = cdrom:\\SLES_012.34;1"))
    }

    @Test
    fun `the walk finds SYSTEM_CNF through the root directory`() {
        val image = syntheticIso(serial = "SLES_537.17")
        val serial = DiscImage.ps2Serial { offset, into ->
            val from = offset.toInt()
            if (from >= image.size) 0 else {
                val n = minOf(into.size, image.size - from)
                System.arraycopy(image, from, into, 0, n)
                n
            }
        }
        assertEquals("SLES-53717", serial)
    }

    @Test
    fun `a disc with no SYSTEM_CNF answers null rather than guessing`() {
        val image = syntheticIso(serial = null)
        assertNull(DiscImage.ps2Serial { offset, into ->
            val from = offset.toInt()
            if (from >= image.size) 0 else {
                val n = minOf(into.size, image.size - from)
                System.arraycopy(image, from, into, 0, n)
                n
            }
        })
    }

    private fun syntheticIso(serial: String?): ByteArray {
        val sector = 2048
        val image = ByteArray(sector * 20)

        val pvd = sector * 16
        "CD001".toByteArray(Charsets.ISO_8859_1).copyInto(image, pvd + 1)

        // The root directory record is embedded at offset 156 of the descriptor.
        val root = pvd + 156
        image[root] = 34
        putLe(image, root + 2, 18)
        putLe(image, root + 10, sector)

        val dir = sector * 18
        val name = "SYSTEM.CNF;1"
        val recordLength = 33 + name.length
        image[dir] = recordLength.toByte()
        putLe(image, dir + 2, 19)
        putLe(image, dir + 10, 64)
        image[dir + 32] = name.length.toByte()
        name.toByteArray(Charsets.ISO_8859_1).copyInto(image, dir + 33)
        // A zero length says "no more records in this sector".
        image[dir + recordLength] = 0

        if (serial != null) {
            val cnf = "BOOT2 = cdrom0:\\$serial;1\nVER = 1.00\n"
            cnf.toByteArray(Charsets.ISO_8859_1).copyInto(image, sector * 19)
        } else {
            image[dir] = 0
        }
        return image
    }

    private fun putLe(bytes: ByteArray, at: Int, value: Int) {
        bytes[at] = (value and 0xFF).toByte()
        bytes[at + 1] = ((value shr 8) and 0xFF).toByte()
        bytes[at + 2] = ((value shr 16) and 0xFF).toByte()
        bytes[at + 3] = ((value shr 24) and 0xFF).toByte()
    }
}
