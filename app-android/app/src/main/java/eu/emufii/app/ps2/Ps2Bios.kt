package eu.emufii.app.ps2

import java.nio.ByteBuffer
import java.nio.ByteOrder

object Ps2Bios {

    /** Reads ROMVER from ROMDIR like ARMSX2's BiosTools.cpp: 16-byte records, 16-byte aligned. */
    fun version(bytes: ByteArray): Int? {
        val directory = findResetRecord(bytes) ?: return null
        var record = directory
        var fileOffset = 0
        while (record + RECORD_SIZE <= bytes.size) {
            val name = asciiName(bytes, record)
            if (name.isEmpty()) return null
            val size = le32(bytes, record + 12) ?: return null
            if (size < 0 || fileOffset < 0 || fileOffset > bytes.size) return null
            if (name == "ROMVER") {
                if (fileOffset + 14 > bytes.size) return null
                val text = String(bytes, fileOffset, 14, Charsets.US_ASCII)
                val major = text.substring(0, 2).toIntOrNull() ?: return null
                val minor = text.substring(2, 4).toIntOrNull() ?: return null
                return (major shl 8) or minor
            }
            val padded = (size.toLong() + 15L) and -16L
            if (padded > Int.MAX_VALUE || fileOffset.toLong() + padded > Int.MAX_VALUE) return null
            fileOffset += padded.toInt()
            record += RECORD_SIZE
        }
        return null
    }

    /** Matches ARMSX2's getNvmLayout(). */
    fun ilinkOffset(version: Int): Int = if (version >= NEW_LAYOUT_VERSION) 0x1E0 else 0x1C0

    private fun findResetRecord(bytes: ByteArray): Int? {
        val end = minOf(bytes.size - RECORD_SIZE, 512 * 1024 * RECORD_SIZE)
        var at = 0
        while (at <= end) {
            if (asciiName(bytes, at) == "RESET") return at
            at += RECORD_SIZE
        }
        return null
    }

    private fun asciiName(bytes: ByteArray, at: Int): String {
        if (at < 0 || at + 10 > bytes.size) return ""
        var end = at
        while (end < at + 10 && bytes[end].toInt() != 0) end++
        return String(bytes, at, end - at, Charsets.US_ASCII)
    }

    private fun le32(bytes: ByteArray, at: Int): Int? =
        if (at < 0 || at + 4 > bytes.size) null
        else ByteBuffer.wrap(bytes, at, 4).order(ByteOrder.LITTLE_ENDIAN).int

    private const val RECORD_SIZE = 16
    private const val NEW_LAYOUT_VERSION = 0x146
}
