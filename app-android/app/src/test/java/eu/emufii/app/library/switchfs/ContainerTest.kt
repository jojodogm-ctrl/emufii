package eu.emufii.app.library.switchfs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * The two containers a Switch game arrives in: PFS0 for a download, XCI
 * for a cartridge dump whose partitions are HFS0.
 *
 * They are similar enough to be mistaken for one another and different enough
 * that the mistake is silent: HFS0 entries are 0x40 bytes where PFS0 entries are
 * 0x18. A generator written here got that wrong while building a test fixture,
 * and the reader's only visible symptom was a tile with no icon, which is the
 * same symptom as "no keys". Hence entry sizes are pinned by name below.
 */
class ContainerTest {

    private class Bytes(private val data: ByteArray) : SwitchControl.RandomAccess {
        override val size: Long get() = data.size.toLong()
        override fun read(offset: Long, length: Int): ByteArray {
            val out = ByteArray(length)
            val from = offset.toInt().coerceIn(0, data.size)
            val n = minOf(length, data.size - from)
            if (n > 0) data.copyInto(out, 0, from, from + n)
            return out
        }
    }

    private fun pfs0(files: List<Pair<String, ByteArray>>): ByteArray =
        archive(files, magic = "PFS0", entrySize = 0x18)

    private fun hfs0(files: List<Pair<String, ByteArray>>): ByteArray =
        archive(files, magic = "HFS0", entrySize = 0x40)

    private fun archive(
        files: List<Pair<String, ByteArray>>,
        magic: String,
        entrySize: Int
    ): ByteArray {
        val names = StringBuilder()
        val nameOffsets = files.map { (name, _) ->
            val at = names.length
            names.append(name).append('\u0000')
            at
        }
        while (names.length % 4 != 0) names.append('\u0000')
        val nameBytes = names.toString().toByteArray()

        val table = ByteArray(files.size * entrySize)
        val b = ByteBuffer.wrap(table).order(ByteOrder.LITTLE_ENDIAN)
        var dataOffset = 0L
        files.forEachIndexed { i, (_, body) ->
            val at = i * entrySize
            b.putLong(at, dataOffset)
            b.putLong(at + 0x08, body.size.toLong())
            b.putInt(at + 0x10, nameOffsets[i])
            dataOffset += body.size
        }

        val head = ByteArray(0x10)
        val hb = ByteBuffer.wrap(head).order(ByteOrder.LITTLE_ENDIAN)
        magic.toByteArray().copyInto(head)
        hb.putInt(0x04, files.size)
        hb.putInt(0x08, nameBytes.size)

        return head + table + nameBytes + files.fold(ByteArray(0)) { acc, (_, body) -> acc + body }
    }

    private fun xci(secure: ByteArray): ByteArray {
        val root = hfs0(listOf("secure" to secure))
        val out = ByteArray(0x1000 + root.size)
        "HEAD".toByteArray().copyInto(out, 0x100)
        ByteBuffer.wrap(out).order(ByteOrder.LITTLE_ENDIAN).putLong(0x130, 0x1000L)
        root.copyInto(out, 0x1000)
        return out
    }

    @Test
    fun `a download announces its contents with no keys at all`() {
        val nsp = pfs0(listOf("game.nca" to ByteArray(8) { 1 }, "meta.cnmt.xml" to ByteArray(4)))
        val entries = Pfs0.entries(Bytes(nsp))!!
        assertEquals(listOf("game.nca", "meta.cnmt.xml"), entries.map { it.name })
        assertEquals(8L, entries[0].size)
        assertEquals(4L, entries[1].size)
    }

    @Test
    fun `a cartridge dump leads to the same NCAs, two levels down`() {
        val secure = hfs0(listOf("control.nca" to ByteArray(16) { 7 }))
        val entries = Xci.secureEntries(Bytes(xci(secure)))!!
        assertEquals(listOf("control.nca"), entries.map { it.name })
        assertEquals(16L, entries[0].size)
    }

    @Test
    fun `the two formats are not interchangeable`() {
        val nsp = pfs0(listOf("a.nca" to ByteArray(4)))
        val secure = hfs0(listOf("a.nca" to ByteArray(4)))
        // Each reader refuses the other's archive rather than reading it wrong.
        assertNull(Pfs0.entries(Bytes(xci(secure))))
        assertNull(Hfs0.entries(Bytes(nsp), 0))
        assertFalse(Xci.isXci(Bytes(nsp)))
    }

    @Test
    fun `contents finds the NCAs whichever container it is handed`() {
        val nsp = pfs0(listOf("a.nca" to ByteArray(4)))
        val cart = xci(hfs0(listOf("b.nca" to ByteArray(4))))
        assertEquals("a.nca", SwitchControl.contents(Bytes(nsp))!!.single().name)
        assertEquals("b.nca", SwitchControl.contents(Bytes(cart))!!.single().name)
    }

    @Test
    fun `something that is neither is neither`() {
        for (junk in listOf(ByteArray(0), ByteArray(8), ByteArray(0x400) { 0x5A })) {
            assertNull(SwitchControl.contents(Bytes(junk)))
        }
    }

    @Test
    fun `an entry pointing past the end of the file is refused`() {
        val nsp = pfs0(listOf("a.nca" to ByteArray(16)))
        // Claim the entry is far bigger than the archive: a truncated download
        // would otherwise have us read, and allocate, whatever it asked for.
        ByteBuffer.wrap(nsp).order(ByteOrder.LITTLE_ENDIAN).putLong(0x18, 1L shl 40)
        assertNull(Pfs0.entries(Bytes(nsp)))
    }

    @Test
    fun `an XCI whose secure partition is missing is refused`() {
        val onlyLogo = hfs0(listOf("logo" to ByteArray(8)))
        val out = ByteArray(0x1000 + onlyLogo.size)
        "HEAD".toByteArray().copyInto(out, 0x100)
        ByteBuffer.wrap(out).order(ByteOrder.LITTLE_ENDIAN).putLong(0x130, 0x1000L)
        onlyLogo.copyInto(out, 0x1000)
        assertTrue(Xci.isXci(Bytes(out)))
        assertNull(Xci.secureEntries(Bytes(out)))
    }
}
