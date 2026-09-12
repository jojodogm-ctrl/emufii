package eu.emufii.app.library

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.FilterInputStream
import java.io.InputStream

class NdsStreamTest {

    /** An inflating stream hands over what it has, and skips nothing on its own. */
    private class Stingy(data: ByteArray) : FilterInputStream(ByteArrayInputStream(data)) {
        override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, minOf(len, 7))
        override fun skip(n: Long): Long = 0
    }

    private fun bytes(count: Int) = ByteArray(count) { it.toByte() }

    @Test
    fun `a short read is not the end of the stream`() {
        val data = bytes(64)
        assertArrayEquals(data, readAtMost(Stingy(data), 64))
    }

    @Test
    fun `a stream shorter than asked gives what it has`() {
        assertArrayEquals(bytes(10), readAtMost(Stingy(bytes(10)), 64))
    }

    @Test
    fun `a stream that skips nothing is read through instead`() {
        val stream: InputStream = Stingy(bytes(64))
        assertTrue(skipFully(stream, 40))
        assertArrayEquals(bytes(64).copyOfRange(40, 64), readAtMost(stream, 24))
    }

    @Test
    fun `skipping past the end says so`() {
        assertFalse(skipFully(Stingy(bytes(8)), 40))
    }

    @Test
    fun `skipping nothing is not a failure`() {
        assertTrue(skipFully(Stingy(ByteArray(0)), 0))
    }
}
