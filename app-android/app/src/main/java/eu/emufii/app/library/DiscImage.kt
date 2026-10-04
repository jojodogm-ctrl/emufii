package eu.emufii.app.library

import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.FileInputStream

/** Only promotes a file; anything unidentified keeps what [Console.forExtension] said. */
object DiscImage {

    const val HEADER_BYTES = 0x80

    private const val WII_MAGIC = 0x5D1C9EA3
    private const val WII_MAGIC_OFFSET = 0x18

    private const val GC_MAGIC = 0xC2339F3D.toInt()
    private const val GC_MAGIC_OFFSET = 0x1C

    private const val WIA_DISC_TYPE_OFFSET = 0x48
    private const val WIA_DISC_HEADER_OFFSET = 0x58
    private const val WIA_TYPE_GAMECUBE = 1
    private const val WIA_TYPE_WII = 2

    private const val PVD_OFFSET = 0x8000
    private const val PVD_MAGIC_OFFSET = PVD_OFFSET + 1
    private const val PVD_SYSTEM_ID_OFFSET = PVD_OFFSET + 8
    private const val PVD_VOLUME_ID_OFFSET = PVD_OFFSET + 40
    private const val PVD_ID_LENGTH = 32

    private const val PS_SYSTEM_ID = "PLAYSTATION"

    private const val PSP_SYSTEM_ID = "PSP GAME"

    const val PVD_BYTES = PVD_VOLUME_ID_OFFSET + PVD_ID_LENGTH

    fun identify(head: ByteArray): Console? {
        if (head.size >= 4) {
            // Three bytes: the fourth of an RVZ/WIA magic is a format version.
            val tag = String(head, 0, 3, Charsets.ISO_8859_1)
            if (tag == "RVZ" || tag == "WIA") return compressed(head)
            if (String(head, 0, 4, Charsets.ISO_8859_1) == "WBFS") return Console.WII
        }
        return raw(head, 0) ?: playstation(head)
    }

    private fun playstation(head: ByteArray): Console? = playstationAt(head, PVD_OFFSET)

    private fun playstationAt(bytes: ByteArray, base: Int): Console? {
        if (base + PVD_ID_LENGTH * 2 + 8 > bytes.size) return null
        if (String(bytes, base + 1, 5, Charsets.ISO_8859_1) != "CD001") return null
        val system = ascii(bytes, base + 8)
        return when {
            system.equals(PS_SYSTEM_ID, ignoreCase = true) -> Console.PS2
            system.equals(PSP_SYSTEM_ID, ignoreCase = true) -> Console.PSP
            else -> null
        }
    }

    /** Raw sector layouts: plain, MODE1 (16) and MODE2 FORM1 (24). */
    private val SECTOR_USER_DATA_OFFSETS = intArrayOf(0, 16, 24)

    fun fromSector(sector: ByteArray): Pair<Console, String?>? {
        for (base in SECTOR_USER_DATA_OFFSETS) {
            val console = playstationAt(sector, base) ?: continue
            return console to volumeId(sector, base + 40)
        }
        return null
    }

    fun ps2Serial(reader: (Long, ByteArray) -> Int): String? = runCatching {
        val pvd = ByteArray(SECTOR)
        if (reader(PVD_OFFSET.toLong(), pvd) < SECTOR) return null
        if (String(pvd, 1, 5, Charsets.ISO_8859_1) != "CD001") return null

        val rootLba = leInt(pvd, ROOT_RECORD_OFFSET + 2)
        val rootSize = leInt(pvd, ROOT_RECORD_OFFSET + 10)
        if (rootLba <= 0 || rootSize <= 0 || rootSize > MAX_ROOT_BYTES) return null

        val dir = ByteArray(rootSize)
        if (reader(rootLba.toLong() * SECTOR, dir) < rootSize) return null

        var at = 0
        while (at < dir.size) {
            val length = dir[at].toInt() and 0xFF
            if (length == 0) {
                // Records never straddle a sector; the rest is padding.
                at = (at / SECTOR + 1) * SECTOR
                continue
            }
            if (at + length > dir.size) break
            val nameLength = dir[at + 32].toInt() and 0xFF
            val name = String(dir, at + 33, nameLength, Charsets.ISO_8859_1)
            // `;1` is the ISO9660 version suffix, always present on a file.
            if (name.substringBefore(';').equals("SYSTEM.CNF", ignoreCase = true)) {
                val lba = leInt(dir, at + 2)
                val size = leInt(dir, at + 10).coerceAtMost(MAX_CNF_BYTES)
                if (lba <= 0 || size <= 0) return null
                val cnf = ByteArray(size)
                if (reader(lba.toLong() * SECTOR, cnf) < size) return null
                return bootSerial(String(cnf, Charsets.ISO_8859_1))
            }
            at += length
        }
        null
    }.getOrNull()

    fun bootSerial(cnf: String): String? {
        val line = cnf.lineSequence().firstOrNull { it.trimStart().startsWith("BOOT2", true) }
            ?: return null
        val path = line.substringAfter('=', "").trim()
        val file = path.substringAfterLast('\\').substringAfterLast('/')
            .substringAfterLast(':').substringBefore(';')
        val serial = file.replace(".", "").replace('_', '-').uppercase().trim()
        return serial.takeIf { it.matches(SERIAL_SHAPE) }
    }

    private fun leInt(bytes: ByteArray, at: Int): Int =
        (bytes[at].toInt() and 0xFF) or
            ((bytes[at + 1].toInt() and 0xFF) shl 8) or
            ((bytes[at + 2].toInt() and 0xFF) shl 16) or
            ((bytes[at + 3].toInt() and 0xFF) shl 24)

    private const val SECTOR = 2048
    /** Relative to the descriptor, not absolute. */
    private const val ROOT_RECORD_OFFSET = 156
    private const val MAX_ROOT_BYTES = 1 shl 20
    private const val MAX_CNF_BYTES = 4096
    private val SERIAL_SHAPE = Regex("^[A-Z]{4}-\\d{5}$")

    private fun volumeId(bytes: ByteArray, at: Int): String? {
        if (at + PVD_ID_LENGTH > bytes.size) return null
        return ascii(bytes, at)
            .replace('_', '-')
            .takeIf { it.isNotEmpty() && it.all { c -> c.isLetterOrDigit() || c == '-' } }
    }

    private fun ascii(head: ByteArray, at: Int): String =
        String(head, at, PVD_ID_LENGTH, Charsets.ISO_8859_1).trim { it <= ' ' }

    private fun raw(head: ByteArray, base: Int): Console? = when {
        beInt(head, base + WII_MAGIC_OFFSET) == WII_MAGIC -> Console.WII
        beInt(head, base + GC_MAGIC_OFFSET) == GC_MAGIC -> Console.GAMECUBE
        else -> null
    }

    private fun compressed(head: ByteArray): Console? =
        when (beInt(head, WIA_DISC_TYPE_OFFSET)) {
            WIA_TYPE_GAMECUBE -> Console.GAMECUBE
            WIA_TYPE_WII -> Console.WII
            else -> raw(head, WIA_DISC_HEADER_OFFSET)
        }

    fun gameId(head: ByteArray): String? {
        if (playstation(head) == Console.PS2) return volumeId(head, PVD_VOLUME_ID_OFFSET)
        val base = if (head.size >= 3 &&
            String(head, 0, 3, Charsets.ISO_8859_1).let { it == "RVZ" || it == "WIA" }
        ) WIA_DISC_HEADER_OFFSET else 0
        if (base + 6 > head.size) return null
        val id = String(head, base, 6, Charsets.ISO_8859_1)
        return id.takeIf { it.all { c -> c.isLetterOrDigit() } }
    }

    private fun beInt(bytes: ByteArray, at: Int): Int? {
        if (at < 0 || at + 4 > bytes.size) return null
        return (bytes[at].toInt() and 0xFF shl 24) or
            (bytes[at + 1].toInt() and 0xFF shl 16) or
            (bytes[at + 2].toInt() and 0xFF shl 8) or
            (bytes[at + 3].toInt() and 0xFF)
    }

    /** No `.gcz`: its sub-type field is unverified. */
    val SNIFFED_EXTENSIONS = setOf("iso", "gcm", "rvz", "wia", "wbfs", "chd")

    val AMBIGUOUS_EXTENSIONS = setOf("iso", "chd")
}

class DiscImageReader(private val context: Context) {

    data class Info(
        val console: Console,
        val gameId: String?,
        val ps2Identity: Ps2DiscIdentity? = null,
    )

    private val memory = HashMap<String, Info>()

    fun read(uri: Uri, modified: Long = 0L, size: Long = 0L): Info? {
        memory[uri.toString()]?.let {
            if (modified > 0L || size > 0L) remember(uri, modified, size, it)
            return it
        }
        if (modified > 0L || size > 0L) {
            cached(uri, modified, size)?.let {
                memory[uri.toString()] = it
                return it
            }
        }
        val head = head(uri) ?: return null
        if (isChd(head)) {
            val info = chdInfo(uri) ?: return null
            remember(uri, modified, size, info)
            return info
        }
        val console = DiscImage.identify(head) ?: return null
        if (console == Console.PS2) {
            val identity = ps2Identity(uri) ?: return null
            Log.i("DiscImage", "PS2 ${identity.serial} ELF CRC ${identity.elfCrc}")
            val info = Info(console, identity.serial, identity)
            remember(uri, modified, size, info)
            return info
        }
        return Info(console, DiscImage.gameId(head)).also { memory[uri.toString()] = it }
    }

    private fun ps2Identity(uri: Uri): Ps2DiscIdentity? = runCatching {
        context.contentResolver.openFileDescriptor(uri, "r")?.use { descriptor ->
            FileInputStream(descriptor.fileDescriptor).use { stream ->
                val channel = stream.channel
                Ps2DiscIdentityReader.read { offset, into, count ->
                    channel.position(offset)
                    var done = 0
                    while (done < count) {
                        val n = stream.read(into, done, count - done)
                        if (n <= 0) break
                        done += n
                    }
                    done
                }
            }
        }
    }.onFailure { Log.w("DiscImage", "SYSTEM.CNF illisible $uri", it) }.getOrNull()

    fun identify(uri: Uri): Console? = read(uri)?.console

    private fun isChd(head: ByteArray): Boolean =
        head.size >= 8 && String(head, 0, 8, Charsets.ISO_8859_1) == "MComprHD"

    private fun chdInfo(uri: Uri): Info? = runCatching {
        context.contentResolver.openFileDescriptor(uri, "r")?.use { descriptor ->
            FileInputStream(descriptor.fileDescriptor).use { stream ->
                val channel = stream.channel
                val reader = ChdImage.open(object : ChdImage.Source {
                    override fun read(offset: Long, into: ByteArray, count: Int): Int {
                        channel.position(offset)
                        var done = 0
                        while (done < count) {
                            val n = stream.read(into, done, count - done)
                            if (n <= 0) break
                            done += n
                        }
                        return done
                    }
                }) ?: return null
                val sector = reader.readDiscSector(ChdImage.PVD_SECTOR) ?: return null
                val (console, fallbackId) = DiscImage.fromSector(sector) ?: return null
                val identity = if (console == Console.PS2) {
                    Ps2DiscIdentityReader.read(reader)
                } else null
                // No BOOT2 in SYSTEM.CNF means a PS1 disc.
                if (console == Console.PS2 && identity == null) return null
                identity?.let { Log.i("DiscImage", "PS2 CHD ${it.serial} ELF CRC ${it.elfCrc}") }
                Info(console, identity?.serial ?: fallbackId, identity)
            }
        }
    }.onFailure { Log.w("DiscImage", "CHD illisible $uri", it) }.getOrNull()

    private fun remember(uri: Uri, modified: Long, size: Long, info: Info) {
        memory[uri.toString()] = info
        val identity = info.ps2Identity ?: return
        if (modified <= 0L && size <= 0L) return
        context.getSharedPreferences(IDENTITY_PREFS, Context.MODE_PRIVATE).edit()
            .putString(uri.toString(), listOf(modified, size, identity.serial, identity.elfCrc).joinToString("|"))
            .apply()
    }

    private fun cached(uri: Uri, modified: Long, size: Long): Info? {
        val parts = context.getSharedPreferences(IDENTITY_PREFS, Context.MODE_PRIVATE)
            .getString(uri.toString(), null)?.split('|') ?: return null
        if (parts.size != 4 || parts[0].toLongOrNull() != modified || parts[1].toLongOrNull() != size) {
            return null
        }
        val serial = parts[2].takeIf { it.isNotBlank() } ?: return null
        val crc = parts[3].takeIf { it.matches(Regex("^[0-9A-F]{8}$")) } ?: return null
        val identity = Ps2DiscIdentity(serial, crc)
        return Info(Console.PS2, serial, identity)
    }

    private fun head(uri: Uri): ByteArray? = runCatching {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            val buffer = ByteArray(DiscImage.PVD_BYTES)
            var read = 0
            while (read < buffer.size) {
                val n = stream.read(buffer, read, buffer.size - read)
                if (n <= 0) break
                read += n
            }
            when {
                read < DiscImage.HEADER_BYTES -> null
                read < buffer.size -> buffer.copyOf(read)
                else -> buffer
            }
        }
    }.onFailure { Log.w("DiscImage", "cannot read $uri", it) }.getOrNull()

    private companion object {
        const val IDENTITY_PREFS = "ps2_disc_identity"
    }
}
