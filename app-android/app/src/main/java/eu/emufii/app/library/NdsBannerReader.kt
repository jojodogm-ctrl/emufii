package eu.emufii.app.library

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

/** What a `.nds` file will tell us about itself. */
data class NdsData(val icon: Bitmap?, val title: String?, val cacheKey: String?)

/**
 * Reads a DS cartridge's banner off disk. Two small reads, the 0x200-byte
 * header, then the banner it points at, so a scan of a full library stays
 * quick; the decoding itself is in [NdsBanner].
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

                val key = NdsBanner.cacheKey(headerBytes)
                val bannerOffset = NdsBanner.bannerOffset(headerBytes)
                    ?: return@use NdsData(null, NdsBanner.internalTitle(headerBytes), key)

                val banner = ByteBuffer.allocate(BANNER_READ_SIZE)
                val read = readAt(channel, bannerOffset, banner)
                if (read < NdsBanner.MIN_BANNER_SIZE) {
                    return@use NdsData(null, NdsBanner.internalTitle(headerBytes), key)
                }
                val bannerBytes = banner.array().copyOf(read)

                val pixels = NdsBanner.decodeIcon(bannerBytes)
                val bitmap = pixels?.let {
                    Bitmap.createBitmap(it, NdsBanner.ICON_DIM, NdsBanner.ICON_DIM, Bitmap.Config.ARGB_8888)
                }
                val title = NdsBanner.pickTitle(bannerBytes) ?: NdsBanner.internalTitle(headerBytes)

                NdsData(bitmap, title, key)
            }
        } ?: NdsData(null, null, null)
    }.getOrElse { NdsData(null, null, null) }

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
