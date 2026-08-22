package eu.emufii.app.library

import android.content.Context
import android.net.Uri
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

private const val MEDIA_UNIT = 0x200L

data class RomHeader(
    val titleIdHex: String,
    val productCode: String?,
    val ncchOffset: Long,
    val exefsOffset: Long,
    val exefsSize: Long,
    val isDecrypted: Boolean
)

class RomHeaderReader(private val context: Context) {

    fun read(uri: Uri): RomHeader? = runCatching {
        context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
            FileInputStream(pfd.fileDescriptor).channel.use { ch -> parse(ch) }
        }
    }.getOrNull()

    private fun parse(ch: FileChannel): RomHeader? {
        val header = ByteBuffer.allocate(0x200).order(ByteOrder.LITTLE_ENDIAN)
        if (readAt(ch, 0L, header) < 0x200) return null
        val magicAt100 = String(header.array(), 0x100, 4)

        val ncchOffset: Long = when (magicAt100) {
            "NCSD" -> {
                val partOffMediaUnits = header.getInt(0x120).toLong() and 0xFFFFFFFFL
                partOffMediaUnits * MEDIA_UNIT
            }
            "NCCH" -> 0L
            else -> return null
        }

        val ncchHeader = ByteBuffer.allocate(0x200).order(ByteOrder.LITTLE_ENDIAN)
        if (readAt(ch, ncchOffset, ncchHeader) < 0x200) return null
        if (String(ncchHeader.array(), 0x100, 4) != "NCCH") return null

        val partitionId = ncchHeader.getLong(0x108)
        val titleIdHex = String.format("%016X", partitionId)

        val productBytes = ByteArray(16)
        System.arraycopy(ncchHeader.array(), 0x150, productBytes, 0, 16)
        val productCode = String(productBytes)
            .substringBefore('\u0000')
            .trim()
            .ifBlank { null }

        val flag7 = ncchHeader.get(0x188 + 7).toInt() and 0xFF
        val isDecrypted = (flag7 and 0x04) != 0

        val exefsOffMediaUnits = ncchHeader.getInt(0x1A0).toLong() and 0xFFFFFFFFL
        val exefsSizeMediaUnits = ncchHeader.getInt(0x1A4).toLong() and 0xFFFFFFFFL
        val exefsOffset = ncchOffset + exefsOffMediaUnits * MEDIA_UNIT
        val exefsSize = exefsSizeMediaUnits * MEDIA_UNIT

        return RomHeader(
            titleIdHex = titleIdHex,
            productCode = productCode,
            ncchOffset = ncchOffset,
            exefsOffset = exefsOffset,
            exefsSize = exefsSize,
            isDecrypted = isDecrypted
        )
    }

    private fun readAt(ch: FileChannel, pos: Long, buf: ByteBuffer): Int {
        ch.position(pos)
        var total = 0
        while (buf.hasRemaining()) {
            val n = ch.read(buf)
            if (n < 0) break
            total += n
        }
        return total
    }
}
