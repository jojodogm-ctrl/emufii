package eu.emufii.app.library.switchfs

import android.content.Context
import android.net.Uri
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

class SwitchReader(private val context: Context) {

    /** From the ticket/cert entry name in the NSP's plaintext PFS0 table, not the filename. */
    fun titleId(uri: Uri): String? = runCatching {
        context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
            FileInputStream(pfd.fileDescriptor).channel.use { channel ->
                Pfs0.entries(ChannelAccess(channel))?.asSequence()
                    ?.mapNotNull { entry ->
                        val stem = entry.name.substringBefore('.')
                        stem.takeIf {
                            (entry.name.endsWith(".tik") || entry.name.endsWith(".cert")) &&
                                it.length >= 16 && it.take(16).all { c -> c.isDigit() || c in 'a'..'f' || c in 'A'..'F' }
                        }
                    }
                    ?.firstOrNull()
                    ?.take(16)
                    ?.uppercase()
            }
        }
    }.getOrNull()

    private class ChannelAccess(private val channel: FileChannel) : Pfs0.RandomAccess {
        override val size: Long get() = channel.size()
        override fun read(offset: Long, length: Int): ByteArray {
            require(length >= 0) { "negative read" }
            val buffer = ByteBuffer.allocate(length)
            channel.position(offset)
            while (buffer.hasRemaining()) {
                if (channel.read(buffer) < 0) break
            }
            return buffer.array()
        }
    }
}
