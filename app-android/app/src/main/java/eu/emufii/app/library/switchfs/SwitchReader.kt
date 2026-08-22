package eu.emufii.app.library.switchfs

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import eu.emufii.app.library.TitleLanguage
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

/** Title and icon of a Switch dump, or nothing at all, both are ordinary. */
data class SwitchData(val icon: Bitmap?, val title: String?, val cacheKey: String?)

/**
 * Reads what a Switch dump will admit to, given the player's own console keys.
 *
 * Both containers are handled: `.nsp` for a download, `.xci` for a cartridge
 * dump. They differ only in how the NCAs are wrapped, so everything past that
 * is shared.
 *
 * Two things are read at very different costs, and the split matters:
 *
 * - a title id can be had off an NSP's plaintext table of contents, with no
 *   keys at all, one small read. A cartridge carries no ticket, so there it
 *   only comes from the decrypted NCA header;
 * - the icon and title need `prod.keys`, a few megabytes of AES, and a
 *   filesystem walk. Without keys they are simply absent, and the tile keeps
 *   its initials like any unrecognised file.
 */
class SwitchReader(private val context: Context) {

    /**
     * The title id an NSP gives away for free, e.g. `0100CD801CE5E000`.
     *
     * Read off the ticket or certificate entry name, which an NSP carries in
     * clear: `0100cd801ce5e0000000000000000011.tik`. Not from the file name on
     * disk, which is whoever-dumped-it's opinion. A cartridge dump has no such
     * entry, there the id comes from the NCA header, once keys are available.
     */
    fun titleId(uri: Uri): String? = open(uri) { source ->
        Pfs0.entries(source)
            ?.asSequence()
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

    fun read(uri: Uri, keys: SwitchKeys?): SwitchData {
        val id = titleId(uri)
        if (keys == null || !keys.isUsable) return SwitchData(null, null, id)
        val control = open(uri) { source ->
            runCatching { SwitchControl.read(source, keys, preferredLanguages()) }.getOrNull()
        } ?: return SwitchData(null, null, id)

        val bitmap = control.iconJpeg?.let {
            runCatching { BitmapFactory.decodeByteArray(it, 0, it.size) }.getOrNull()
        }
        // The id off the NCA header beats the one off a file name, and is the
        // only one a cartridge dump offers at all.
        return SwitchData(bitmap, control.title, control.titleId ?: id)
    }

    /**
     * Which language to read the title and icon in, see [TitleLanguage], which
     * holds that decision for every console at once so the library cannot end
     * up half in one language and half in the other.
     */
    private fun preferredLanguages(): List<String> = TitleLanguage.switch

    private fun <T> open(uri: Uri, block: (SwitchControl.RandomAccess) -> T?): T? = runCatching {
        context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
            FileInputStream(pfd.fileDescriptor).channel.use { channel ->
                block(ChannelAccess(channel))
            }
        }
    }.getOrNull()

    private class ChannelAccess(private val channel: FileChannel) : SwitchControl.RandomAccess {
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
