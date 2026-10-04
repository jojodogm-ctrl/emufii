package eu.emufii.app.library.switchfs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ContainerTest {

    private class Bytes(private val data: ByteArray) : Pfs0.RandomAccess {
        override val size: Long get() = data.size.toLong()
        override fun read(offset: Long, length: Int): ByteArray {
            val out = ByteArray(length)
            val from = offset.toInt().coerceIn(0, data.size)
            val n = minOf(length, data.size - from)
            if (n > 0) data.copyInto(out, 0, from, from + n)
            return out
        }
    }

    private fun pfs0(files: List<Pair<String, ByteArray>>): ByteArray {
        val names = StringBuilder()
        val nameOffsets = files.map { (name, _) ->
            val at = names.length
            names.append(name).append('\u0000')
            at
        }
        while (names.length % 4 != 0) names.append('\u0000')
        val nameBytes = names.toString().toByteArray()

        val entrySize = 0x18
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
        "PFS0".toByteArray().copyInto(head)
        hb.putInt(0x04, files.size)
        hb.putInt(0x08, nameBytes.size)

        return head + table + nameBytes + files.fold(ByteArray(0)) { acc, (_, body) -> acc + body }
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
    fun `an entry pointing past the end of the file is refused`() {
        val nsp = pfs0(listOf("a.nca" to ByteArray(16)))
        ByteBuffer.wrap(nsp).order(ByteOrder.LITTLE_ENDIAN).putLong(0x18, 1L shl 40)
        assertNull(Pfs0.entries(Bytes(nsp)))
    }
}
