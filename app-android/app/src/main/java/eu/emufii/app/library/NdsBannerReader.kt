package eu.emufii.app.library

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

data class NdsData(val icon: Bitmap?, val title: String?, val cacheKey: String?)

/**
 * Two small reads, the 0x200-byte header then the banner it points at, so a scan of a full
 * library stays quick; the decoding is in [NdsBanner].
 */
class NdsBannerReader(private val context: Context) {

    private companion object {
        const val HEADER_SIZE = 0x200
        const val BANNER_READ_SIZE = 0xA40
    }

    fun read(uri: Uri): NdsData = runCatching {
        context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
            FileInputStream(pfd.fileDescriptor).channel.use { channel ->
                val header = ByteBuffer.allocate(HEADER_SIZE)
                if (readAt(channel, 0, header) < HEADER_SIZE) return@use NdsData(null, null, null)
                val headerBytes = header.array()

                val bannerOffset = NdsBanner.bannerOffset(headerBytes)
                    ?: return@use decode(headerBytes, null)

                val banner = ByteBuffer.allocate(BANNER_READ_SIZE)
                val read = readAt(channel, bannerOffset, banner)
                decode(headerBytes, banner.array().copyOf(read))
            }
        } ?: NdsData(null, null, null)
    }.getOrElse { NdsData(null, null, null) }

    /** The same banner, read in one pass: an archive entry cannot be seeked. */
    fun readArchived(uri: Uri): NdsData = runCatching {
        context.contentResolver.openInputStream(uri)?.use { file ->
            val rom = NdsArchive.openRom(file) ?: return@use NdsData(null, null, null)
            val headerBytes = readAtMost(rom, HEADER_SIZE)
            if (headerBytes.size < HEADER_SIZE) return@use NdsData(null, null, null)

            val bannerOffset = NdsBanner.bannerOffset(headerBytes)
            if (bannerOffset == null || bannerOffset < HEADER_SIZE) {
                return@use decode(headerBytes, null)
            }
            if (!skipFully(rom, bannerOffset - HEADER_SIZE)) return@use decode(headerBytes, null)

            decode(headerBytes, readAtMost(rom, BANNER_READ_SIZE))
        } ?: NdsData(null, null, null)
    }.getOrElse { NdsData(null, null, null) }

    private fun decode(header: ByteArray, banner: ByteArray?): NdsData {
        val key = NdsBanner.cacheKey(header)
        val internalTitle = NdsBanner.internalTitle(header)
        if (banner == null || banner.size < NdsBanner.MIN_BANNER_SIZE) {
            return NdsData(null, internalTitle, key)
        }

        val icon = NdsBanner.decodeIcon(banner)?.let {
            Bitmap.createBitmap(it, NdsBanner.ICON_DIM, NdsBanner.ICON_DIM, Bitmap.Config.ARGB_8888)
        }
        return NdsData(icon, NdsBanner.pickTitle(banner) ?: internalTitle, key)
    }

    private fun readAt(channel: FileChannel, position: Long, buffer: ByteBuffer): Int {
        channel.position(position)
        var total = 0
        while (buffer.hasRemaining()) {
            val n = channel.read(buffer)
            if (n < 0) break
            total += n
        }
        return total
    }
}

/** At most [count] bytes, short only at the end of [stream]. */
internal fun readAtMost(stream: InputStream, count: Int): ByteArray {
    val buffer = ByteArray(count)
    var total = 0
    while (total < count) {
        val read = stream.read(buffer, total, count - total)
        if (read < 0) break
        total += read
    }
    return buffer.copyOf(total)
}

/** False if [stream] ends before [count] bytes have been passed over. */
internal fun skipFully(stream: InputStream, count: Long): Boolean {
    var left = count
    while (left > 0) {
        val skipped = stream.skip(left)
        if (skipped > 0) {
            left -= skipped
            continue
        }
        // Skipping nothing does not mean the end of the stream; a read tells.
        if (stream.read() < 0) return false
        left--
    }
    return true
}
