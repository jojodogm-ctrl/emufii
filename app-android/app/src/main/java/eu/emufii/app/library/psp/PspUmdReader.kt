package eu.emufii.app.library.psp

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

/**
 * What a PSP game consents to say about itself.
 *
 * [recognised] says something other than "there is an icon": that the file really
 * is a PSP game. A PS2 or Xbox `.iso` carries the same extension and used to end
 * up in the grid under its filename, next to games Emufii really can put into a
 * game.
 */
data class PspData(
    val icon: Bitmap?,
    val title: String?,
    val cacheKey: String?,
    val recognised: Boolean = false
)

/**
 * Reads a PSP game's icon and title, without reading the game.
 *
 * Five small reads are enough, the volume descriptor, the root, the `PSP_GAME`
 * directory, then the two files, that is about ten kilobytes on a disc weighing
 * a million of them. That is what makes it possible during the library scan, as
 * for the DS and the 3DS.
 *
 * Two containers are served. The UMD (`.iso`) files its contents in an ordinary
 * ISO9660, decoded by [UmdIso]. The `EBOOT.PBP`, the form taken by downloaded
 * games and homebrew, instead carries an address table up front, so the icon is
 * found without walking anything. `.cso` and `.chd` are compressed: their table
 * of contents is not readable as it stands, and those tiles keep their initials.
 */
class PspUmdReader(private val context: Context) {

    private companion object {
        val ICON_PATH = listOf("PSP_GAME", "ICON0.PNG")
        val SFO_PATH = listOf("PSP_GAME", "PARAM.SFO")

        /** `\0PBP`: an EBOOT's signature. */
        const val PBP_MAGIC = 0x50425000

        /** Inside a PBP, the offsets of `PARAM.SFO` and `ICON0.PNG`. */
        const val PBP_SFO_AT = 0x08
        const val PBP_ICON_AT = 0x0C

        const val MAX_ICON = 512 * 1024
        const val MAX_SFO = 64 * 1024

        /**
         * A UMD's icon is 144x80, and the grid draws without smoothing, which is
         * right for a DS's 32 pixels, where every pixel is an artist's choice,
         * and wrong for PSP artwork, which would then be seen as large squares.
         * So we scale it up here, once, with smoothing.
         */
        const val UPSCALE = 3
    }

    fun read(uri: Uri): PspData = runCatching {
        context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
            FileInputStream(pfd.fileDescriptor).channel.use { channel ->
                val source = UmdIso.Source { offset, length -> readAt(channel, offset, length) }
                val payload = pbpPayload(source) ?: isoPayload(source) ?: return@use empty
                decode(payload)
            }
        } ?: empty
    }.getOrElse { empty }

    private val empty get() = PspData(null, null, null)

    /** The two files we care about, once located. */
    private data class Payload(val sfo: ByteArray?, val icon: ByteArray?)

    private fun decode(payload: Payload): PspData {
        val fields = payload.sfo?.let(ParamSfo::read).orEmpty()
        val bitmap = payload.icon?.let { bytes ->
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.let(::upscale)
        }
        // A `PARAM.SFO` title often wraps to fit the console's thumbnail
        // ("WipEout\nPulse"): in a grid, those breaks become holes in the middle
        // of the name.
        val title = fields["TITLE"]
            ?.replace(Regex("\\s+"), " ")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
        val id = fields["DISC_ID"]?.filter { it.isLetterOrDigit() }?.takeIf { it.isNotBlank() }

        return PspData(
            icon = bitmap,
            title = title,
            cacheKey = id?.let { "PSP-$it" },
            // We only get here after finding `PSP_GAME` in an ISO9660 or an
            // EBOOT header: in both cases this is a PSP game.
            recognised = true
        )
    }

    private fun upscale(bitmap: Bitmap): Bitmap {
        val w = bitmap.width * UPSCALE
        val h = bitmap.height * UPSCALE
        if (w <= 0 || h <= 0 || w > 2048 || h > 2048) return bitmap
        return runCatching { Bitmap.createScaledBitmap(bitmap, w, h, true) }.getOrDefault(bitmap)
    }

    private fun isoPayload(source: UmdIso.Source): Payload? {
        val icon = UmdIso.find(source, ICON_PATH)
        val sfo = UmdIso.find(source, SFO_PATH)
        if (icon == null && sfo == null) return null
        return Payload(
            sfo = sfo?.let { source.read(it.offset, minOf(it.size, MAX_SFO)) },
            icon = icon?.let { source.read(it.offset, minOf(it.size, MAX_ICON)) }
        )
    }

    /**
     * A PBP says where its pieces are itself: every offset is followed by the
     * next piece's, so the size follows without looking for anything.
     */
    private fun pbpPayload(source: UmdIso.Source): Payload? {
        val header = source.read(0, 40) ?: return null
        if (header.size < 40) return null
        val buf = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        if (buf.getInt(0) != PBP_MAGIC) return null

        fun slice(at: Int): ByteArray? {
            val start = buf.getInt(at)
            val end = buf.getInt(at + 4)
            val size = end - start
            if (start <= 0 || size <= 0 || size > MAX_ICON) return null
            return source.read(start.toLong(), size)
        }

        return Payload(sfo = slice(PBP_SFO_AT), icon = slice(PBP_ICON_AT))
    }

    private fun readAt(channel: FileChannel, position: Long, length: Int): ByteArray? {
        if (length <= 0 || position < 0) return null
        val buffer = ByteBuffer.allocate(length)
        channel.position(position)
        while (buffer.hasRemaining()) {
            if (channel.read(buffer) < 0) break
        }
        val read = buffer.position()
        // A short read is not a usable partial read here: every structure read
        // has an announced size, so a truncated dump has to say so rather than be
        // guessed at.
        return if (read == length) buffer.array() else null
    }
}
