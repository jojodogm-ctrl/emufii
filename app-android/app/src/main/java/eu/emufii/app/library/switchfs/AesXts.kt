package eu.emufii.app.library.switchfs

import android.annotation.SuppressLint
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * AES-128-XTS as the Switch uses it, which is not quite as the standard writes
 * it.
 *
 * Two departures, both load-bearing:
 *
 * - the tweak is the sector index big-endian, where XTS-AES specifies
 *   little-endian. Get this wrong and the header decrypts to noise that looks
 *   exactly like a wrong key;
 * - the "sector" is 0x200 bytes and the tweak restarts at every sector, so a
 *   caller decrypting from the middle of a file has to say which sector it
 *   started at.
 *
 * Java has no XTS provider, so the mode is built here out of AES-ECB: that is
 * all XTS is, a tweaked ECB with a Galois-field doubling between blocks.
 */
object AesXts {

    private const val SECTOR = 0x200
    private const val BLOCK = 16

    /**
     * Decrypts [data] in place of a copy, treating its first byte as the start
     * of sector [firstSector].
     *
     * [key] is 32 bytes: the data key followed by the tweak key.
     */
    fun decrypt(data: ByteArray, key: ByteArray, firstSector: Int = 0): ByteArray {
        require(key.size == 32) { "XTS wants a 32-byte key, got ${key.size}" }
        val dataCipher = ecb(key.copyOfRange(0, 16), Cipher.DECRYPT_MODE)
        val tweakCipher = ecb(key.copyOfRange(16, 32), Cipher.ENCRYPT_MODE)

        val out = ByteArray(data.size)
        var sector = firstSector
        var pos = 0
        while (pos + SECTOR <= data.size) {
            var tweak = tweakCipher.doFinal(sectorTweak(sector))
            var i = 0
            while (i < SECTOR) {
                val block = ByteArray(BLOCK)
                for (b in 0 until BLOCK) block[b] = (data[pos + i + b].toInt() xor tweak[b].toInt()).toByte()
                val plain = dataCipher.doFinal(block)
                for (b in 0 until BLOCK) out[pos + i + b] = (plain[b].toInt() xor tweak[b].toInt()).toByte()
                tweak = double(tweak)
                i += BLOCK
            }
            pos += SECTOR
            sector++
        }
        // A trailing partial sector is never part of an NCA header; leaving it
        // untouched is more honest than pretending to have decrypted it.
        if (pos < data.size) data.copyInto(out, pos, pos, data.size)
        return out
    }

    /** The sector number, big-endian, right-aligned in 16 bytes. */
    private fun sectorTweak(sector: Int): ByteArray {
        val t = ByteArray(BLOCK)
        var v = sector.toLong()
        for (i in BLOCK - 1 downTo 0) {
            t[i] = (v and 0xFF).toByte()
            v = v ushr 8
        }
        return t
    }

    /** Multiply by x in GF(2^128), little-endian representation. */
    private fun double(t: ByteArray): ByteArray {
        val out = ByteArray(BLOCK)
        var carry = 0
        for (i in 0 until BLOCK) {
            val v = (t[i].toInt() and 0xFF) shl 1 or carry
            out[i] = (v and 0xFF).toByte()
            carry = (v ushr 8) and 1
        }
        if (carry != 0) out[0] = (out[0].toInt() xor 0x87).toByte()
        return out
    }

    /**
     * Raw AES, which is XTS's block primitive rather than a mode we chose.
     *
     * Lint flags `AES/ECB` on sight, and it is right to in general: ECB leaks
     * structure when it encrypts a message. Here it never encrypts a message.
     * XTS is built *out of* single-block AES, once for the tweak and once per
     * block, and the tweak is what removes the weakness lint is warning about.
     * Asking for CBC or GCM here would not harden anything, it would produce a
     * different algorithm that cannot read a Switch dump.
     */
    @SuppressLint("GetInstance")
    private fun ecb(key: ByteArray, mode: Int): Cipher =
        Cipher.getInstance("AES/ECB/NoPadding").apply {
            init(mode, SecretKeySpec(key, "AES"))
        }
}
