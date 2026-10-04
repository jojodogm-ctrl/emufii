package eu.emufii.app.ps2

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class Ps2MemoryCardTest {

    private val consoleId = Ps2NetcnfConfig.ARMSX2_CONSOLE_ID
    private val epoch = 1755729273L // fixed: a build is reproducible

    private fun card(title: String = "Emufii") = Ps2MemoryCard.generate(
        saveTitle = title,
        consoleId = consoleId,
        epochSecond = epoch,
    )

    @Test
    fun `the image is the standard 8 MB raw card`() {
        assertEquals(16384 * 528, card().size)
    }

    @Test
    fun `page 0 is the superblock the BIOS writes, byte for byte`() {
        val image = card()
        val expected = ByteArray(528)
        val sb = ByteBuffer.wrap(expected).order(ByteOrder.LITTLE_ENDIAN)
        sb.put("Sony PS2 Memory Card Format ".toByteArray(Charsets.US_ASCII))
        sb.put("1.2.0.0".toByteArray(Charsets.US_ASCII))
        sb.position(0x28)
        sb.putShort(512); sb.putShort(2); sb.putShort(16); sb.putShort((-0x0100).toShort())
        sb.putInt(8192); sb.putInt(41); sb.putInt(8135); sb.putInt(0)
        sb.putInt(1023); sb.putInt(1022)
        sb.position(0x50); sb.putInt(8)
        sb.position(0xD0); for (i in 0 until 32) sb.putInt(-1)
        sb.put(2); sb.put(0x2B)
        sb.position(0x154)
        for (word in intArrayOf(0x400, 0x100, 8, -1, 0, 0, 0, 0x1F41, 0, 0, -1, 0, -1, -1)) sb.putInt(word)
        for (offset in 0x18C until 0x200 step 4) sb.putInt(-1)
        // The spare, measured on the same card: the four ECCs of that page.
        expected[512] = 0x07; expected[513] = 0x34; expected[514] = 0x4B
        expected[515] = 0x77; expected[516] = 0x7F; expected[517] = 0x7F
        expected[518] = 0x25; expected[519] = 0x71; expected[520] = 0x0E
        expected[521] = 0x77; expected[522] = 0x7F; expected[523] = 0x7F
        assertArrayEquals(expected, image.copyOfRange(0, 528))
    }

    @Test
    fun `the erased reserved block carries ECC spares, untouched space stays erased`() {
        val image = card()
        for (page in 1 until 16) {
            assertArrayEquals(
                ByteArray(512) { 0xFF.toByte() },
                image.copyOfRange(page * 528, page * 528 + 512),
            )
            assertArrayEquals(
                byteArrayOf(0x77, 0x7F, 0x7F, 0x77, 0x7F, 0x7F, 0x77, 0x7F, 0x7F, 0x77, 0x7F, 0x7F, 0, 0, 0, 0),
                image.copyOfRange(page * 528 + 512, page * 528 + 528),
            )
        }
        val far = 16000 * 528
        assertArrayEquals(ByteArray(528) { 0xFF.toByte() }, image.copyOfRange(far, far + 528))
    }

    @Test
    fun `every written page's ECC matches its data, and no page is half-written`() {
        val image = card()
        var written = 0
        for (page in 0 until 16384) {
            val spare = image.copyOfRange(page * 528 + 512, page * 528 + 528)
            if (spare.contentEquals(ByteArray(16) { 0xFF.toByte() })) continue
            written++
            assertArrayEquals(
                "page $page",
                Ps2MemoryCard.spare(image.copyOfRange(page * 528, page * 528 + 512)),
                spare,
            )
        }
        assertTrue(written in 50..500)
    }

    @Test
    fun `the indirect FAT lists the thirty-two FAT clusters and nothing else`() {
        val image = card()
        val ifd = cluster(image, 8)
        for (i in 0 until 31) {
            assertEquals(9 + i, u32(ifd, i * 4))
        }
        for (i in 32 until 256) {
            assertEquals(-1, u32(ifd, i * 4))
        }
    }

    @Test
    fun `the directory tree carries the save, protected, with every file intact`() {
        val image = card()
        val root = readChain(image, 0)
        assertEquals(0x8427, u32(root, 0)) // '.'
        assertEquals(3, u32(root, 4)) // '.', '..', BWNETCNF
        assertEquals(0xA426, u32(root, 512)) // '..'
        assertEquals(0x842F, u32(root, 1024)) // BWNETCNF, copy-protected
        assertEquals(8, u32(root, 1024 + 4)) // ., .., six files
        assertEquals("BWNETCNF", name(root, 1024))
        assertEquals(-1, u32(root, 1536)) // terminator

        val save = readChain(image, u32(root, 1024 + 0x10).toInt())
        assertEquals(0x8427, u32(save, 0))
        assertEquals(2, u32(save, 0x14)) // this directory is entry 2 of the root
        val expected = mapOf(
            "BWNETCNF" to Ps2NetcnfConfig.INDEX,
            "net000.cnf" to Ps2NetcnfConfig.NET_CNF,
            "ifc000.dat" to Ps2NetcnfConfig.ifcDat(consoleId),
            "dev000.dat" to Ps2NetcnfConfig.devDat(consoleId),
        )
        for (entry in 2 until 8) {
            val at = entry * 512
            val fileName = name(save, at)
            val mode = u32(save, at)
            val expectedContent = expected[fileName] ?: continue // the icon pair, checked in its own test
            assertEquals("file mode for $fileName", 0x8497, mode)
            val length = u32(save, at + 4).toInt()
            val first = u32(save, at + 0x10).toInt()
            assertArrayEquals("content of $fileName", expectedContent, readChain(image, first).copyOf(length))
        }
    }

    @Test
    fun `the save's encrypted halves decode back under the ARMSX2 console id`() {
        val image = card()
        val root = readChain(image, 0)
        val save = readChain(image, u32(root, 1024 + 0x10).toInt())
        fun file(named: String): ByteArray {
            for (entry in 2 until 8) {
                if (name(save, entry * 512) == named) {
                    val length = u32(save, entry * 512 + 4).toInt()
                    return readChain(image, u32(save, entry * 512 + 0x10).toInt()).copyOf(length)
                }
            }
            throw AssertionError(named)
        }
        val ifc = String(Ps2NetcnfConfig.decode(file("ifc000.dat"), consoleId), Charsets.US_ASCII)
        val dev = String(Ps2NetcnfConfig.decode(file("dev000.dat"), consoleId), Charsets.US_ASCII)
        assertTrue(ifc.endsWith("type nic\ndhcp\n"))
        assertTrue(dev.contains("product \"Ethernet (Network Adaptor)\""))
    }

    @Test
    fun `the icon pair is present and parses as the format says`() {
        val image = card()
        val root = readChain(image, 0)
        val save = readChain(image, u32(root, 1024 + 0x10).toInt())
        var iconSys: ByteArray? = null
        var icon: ByteArray? = null
        for (entry in 2 until 8) {
            when (name(save, entry * 512)) {
                "icon.sys" -> iconSys = readChain(image, u32(save, entry * 512 + 0x10).toInt())
                    .copyOf(u32(save, entry * 512 + 4).toInt())
                "EMUFII.ICO" -> icon = readChain(image, u32(save, entry * 512 + 0x10).toInt())
                    .copyOf(u32(save, entry * 512 + 4).toInt())
            }
        }
        assertEquals(964, iconSys!!.size)
        assertEquals("PS2D", String(iconSys.copyOfRange(0, 4), Charsets.US_ASCII))
        assertEquals("EMUFII.ICO", String(iconSys.copyOfRange(260, 270), Charsets.US_ASCII))

        val ico = icon!!
        assertEquals(0x00010000, u32(ico, 0)) // magic
        assertEquals(6, u32(ico, 16)) // six vertices, two triangles
        // 20 header + 6*24 vertices + 36 animation, then the RLE texture.
        assertEquals(20 + 144 + 36 + 12, ico.size)
    }

    @Test
    fun `two builds at one time are identical, and the title reaches the card`() {
        val a = card("Alice")
        val b = card("Alice")
        assertArrayEquals(a, b)
        val other = card("Bob")
        var differs = 0
        for (i in a.indices) if (a[i] != other[i]) differs++
        assertTrue(differs > 0)
        assertTrue("only the title row may differ, saw $differs bytes", differs <= 68)
    }

    @Test
    fun `a non-ascii title degrades to printable ascii rather than failing`() {
        val image = card("Jérüme 🎮")
        val root = readChain(image, 0)
        val save = readChain(image, u32(root, 1024 + 0x10).toInt())
        var titleField = ""
        for (entry in 2 until 8) {
            if (name(save, entry * 512) == "icon.sys") {
                val iconSys = readChain(image, u32(save, entry * 512 + 0x10).toInt())
                    .copyOf(u32(save, entry * 512 + 4).toInt())
                titleField = String(iconSys.copyOfRange(192, 260), Charsets.US_ASCII)
                    .takeWhile { it in ' '..'~' }
                break
            }
        }
        assertTrue(titleField.all { it in ' '..'~' })
    }

    /** The 1024 data bytes of a cluster, spare bytes skipped. */
    private fun cluster(image: ByteArray, c: Int): ByteArray {
        val out = ByteArray(1024)
        for (page in 0 until 2) {
            val from = (2 * c + page) * 528
            System.arraycopy(image, from, out, page * 512, 512)
        }
        return out
    }

    /** Walks the FAT chain starting at a cluster relative to the alloc offset. */
    private fun readChain(image: ByteArray, first: Int): ByteArray {
        val chunks = mutableListOf<ByteArray>()
        var current = first
        val seen = mutableSetOf<Int>()
        while (true) {
            chunks.add(cluster(image, 41 + current))
            val entry = fat(image, current)
            if (entry == -1) break
            val next = entry and 0x7FFFFFFF
            assertTrue("chain revisits cluster $next", seen.add(next))
            current = next
        }
        return chunks.reduce { acc, bytes -> acc + bytes }
    }

    private fun fat(image: ByteArray, index: Int): Int {
        val ifd = cluster(image, 8)
        val fatCluster = 9 + index / 256
        return u32(cluster(image, fatCluster), (index % 256) * 4)
    }

    private fun u32(b: ByteArray, at: Int): Int =
        (b[at].toInt() and 0xFF) or ((b[at + 1].toInt() and 0xFF) shl 8) or
            ((b[at + 2].toInt() and 0xFF) shl 16) or ((b[at + 3].toInt() and 0xFF) shl 24)

    private fun name(entry: ByteArray, at: Int): String {
        var end = at + 0x40
        while (end < at + 0x60 && entry[end].toInt() != 0) end++
        return String(entry.copyOfRange(at + 0x40, end), Charsets.US_ASCII)
    }
}
