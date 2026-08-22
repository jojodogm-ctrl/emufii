package eu.emufii.app.library.switchfs

import android.annotation.SuppressLint
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * What a Switch dump will tell us about itself once its keys are known: the
 * title, and the 256×256 JPEG the console shows on its home menu.
 *
 * The road there, all of it verified against a real retail NSP rather than
 * against documentation:
 *
 * 1. an NSP is a PFS0 archive, and its header is plaintext, file names and
 *    offsets are readable with no keys at all;
 * 2. one of the NCAs inside is the *control* content. Which one is stated in
 *    the `.cnmt.xml` some dumpers include, but that file is a courtesy, not a
 *    format: instead every NCA header is unwrapped and asked its content type;
 * 3. an NCA header is AES-XTS with `header_key`;
 * 4. the section key comes from one of two places, and dumps in the wild use
 *    both: a rights id means the key is a ticket's title key, all-zeros
 *    means it is in the NCA's own key area. The Balatro dump this was written
 *    against ships a `.tik` it doesn't use, trusting the ticket's presence
 *    would have decrypted to noise;
 * 5. the section body is AES-CTR, the counter being the absolute offset;
 * 6. inside sits an IVFC tree whose last level is the RomFS, and the RomFS
 *    holds `control.nacp` and `icon_<language>.dat`.
 */
object SwitchControl {

    data class Control(val title: String?, val iconJpeg: ByteArray?, val titleId: String? = null)

    private const val MEDIA = 0x200
    private const val NCA_HEADER = 0xC00

    /** A control NCA is small; anything larger is not one and won't be read. */
    private const val MAX_SECTION = 8 * 1024 * 1024

    /**
     * Reads [source], a whole NSP, and returns what its control content says.
     *
     * Returns null when the file isn't an NSP, when no control NCA is found, or
     * when [keys] can't unwrap it. Every one of those is an ordinary outcome,
     * not an error: the library falls back to the filename.
     */
    fun read(source: RandomAccess, keys: SwitchKeys, preferredLanguages: List<String>): Control? {
        val headerKey = keys.headerKey ?: return null
        val entries = contents(source) ?: return null

        for (entry in entries) {
            if (!entry.name.endsWith(".nca", ignoreCase = true)) continue
            if (entry.size < NCA_HEADER) continue
            val header = AesXts.decrypt(source.read(entry.offset, NCA_HEADER), headerKey)
            if (String(header, 0x200, 4, Charsets.US_ASCII) != "NCA3") continue
            // 2 = control. The program NCA is the big one and holds no icon.
            if (header[0x205].toInt() != 2) continue

            val sectionKey = sectionKey(header, keys) ?: continue
            val romfs = romfs(source, entry.offset, header, sectionKey) ?: continue
            return RomFs.control(romfs, preferredLanguages)?.copy(titleId = programId(header))
        }
        return null
    }

    /**
     * The NCAs inside, whichever container this is.
     *
     * A download is a PFS0, a cartridge dump an XCI whose `secure`
     * partition is an HFS0. Past this point the two are the same file format,
     * which is why everything below knows nothing about either.
     */
    fun contents(source: RandomAccess): List<Pfs0.Entry>? =
        Pfs0.entries(source) ?: Xci.secureEntries(source)

    /**
     * The title id, read from the NCA header itself.
     *
     * More trustworthy than the ticket file name an NSP happens to carry, and
     * the only source at all on a cartridge dump, whose `secure` partition holds
     * no ticket. Costs the header decryption, so it is only known once the
     * player has supplied keys.
     */
    private fun programId(header: ByteArray): String {
        val id = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN).getLong(0x210)
        return "%016X".format(id)
    }

    /**
     * The AES-CTR key for section 0.
     *
     * The ticket path is deliberately *not* implemented here: Emufii would have to
     * find, parse and trust a `.tik`, and every dump seen so far that carries
     * one still encrypts its control NCA with the key area. Returning null keeps
     * the tile on its initials rather than showing a wrong icon.
     */
    /**
     * Unwraps the NCA key area, which the format defines as plain AES-128-ECB.
     *
     * Lint's `GetInstance` warning does not apply: the mode is not ours to pick.
     * It is the one Nintendo wrote into the container, over exactly one 0x40-byte
     * key area that is never a message. Reading the dump means using it.
     */
    @SuppressLint("GetInstance")
    private fun sectionKey(header: ByteArray, keys: SwitchKeys): ByteArray? {
        val rightsIdIsSet = (0x230 until 0x240).any { header[it].toInt() != 0 }
        if (rightsIdIsSet) return null

        val generation = maxOf(header[0x206].toInt() and 0xFF, header[0x220].toInt() and 0xFF)
            .let { if (it > 0) it - 1 else 0 }
        val kaek = keys.keyAreaKeyApplication(generation) ?: return null
        val area = Cipher.getInstance("AES/ECB/NoPadding").run {
            init(Cipher.DECRYPT_MODE, SecretKeySpec(kaek, "AES"))
            doFinal(header.copyOfRange(0x300, 0x340))
        }
        // Slot 2 is the CTR key; the others are zero on a control NCA.
        return area.copyOfRange(0x20, 0x30).takeIf { it.any { b -> b.toInt() != 0 } }
    }

    /** Decrypts section 0 and returns just the RomFS level of its IVFC tree. */
    private fun romfs(
        source: RandomAccess,
        ncaOffset: Long,
        header: ByteArray,
        key: ByteArray
    ): ByteArray? {
        val h = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        val start = (h.getInt(0x240).toLong() and 0xFFFFFFFFL) * MEDIA
        val end = (h.getInt(0x244).toLong() and 0xFFFFFFFFL) * MEDIA
        val length = end - start
        if (length <= 0 || length > MAX_SECTION) return null

        val fs = header.copyOfRange(0x400, 0x600)
        // 3 = AES-CTR. 1 means the section is already plain, which happens on
        // homebrew; anything else is a format we haven't met.
        val encryption = fs[0x04].toInt()
        if (encryption != 3 && encryption != 1) return null

        val ivfc = fs.copyOfRange(0x08, 0x08 + 0xE0)
        if (String(ivfc, 0, 4, Charsets.US_ASCII) != "IVFC") return null
        val levels = ByteBuffer.wrap(ivfc).order(ByteOrder.LITTLE_ENDIAN).getInt(0x0C)
        if (levels < 2) return null
        // The last data level is the filesystem itself; the ones above it are
        // only the hash tree that proves it.
        val last = 0x10 + (levels - 2) * 0x18
        val lb = ByteBuffer.wrap(ivfc).order(ByteOrder.LITTLE_ENDIAN)
        val romfsOffset = lb.getLong(last)
        val romfsSize = lb.getLong(last + 8)
        if (romfsOffset < 0 || romfsSize <= 0 || romfsOffset + romfsSize > length) return null

        val body = source.read(ncaOffset + start, length.toInt())
        val plain = if (encryption == 1) body else {
            ctrDecrypt(body, key, fs.copyOfRange(0x140, 0x148), start)
        }
        return plain.copyOfRange(romfsOffset.toInt(), (romfsOffset + romfsSize).toInt())
    }

    /**
     * AES-CTR over a section.
     *
     * The counter is the section's own byte offset divided by the block size,
     * with the eight bytes at 0x140 of the FS header, reversed, above it.
     */
    private fun ctrDecrypt(data: ByteArray, key: ByteArray, ctrHigh: ByteArray, offset: Long): ByteArray {
        val iv = ByteArray(16)
        for (i in 0 until 8) iv[i] = ctrHigh[7 - i]
        var block = offset ushr 4
        for (i in 15 downTo 8) {
            iv[i] = (block and 0xFF).toByte()
            block = block ushr 8
        }
        return Cipher.getInstance("AES/CTR/NoPadding").run {
            init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
            doFinal(data)
        }
    }

    /** Just enough of a file to read scattered pieces of it without loading it. */
    interface RandomAccess {
        fun read(offset: Long, length: Int): ByteArray
        val size: Long
    }
}
