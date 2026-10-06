package eu.emufii.app.ps2

import android.content.Context
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import eu.emufii.app.azahar.NetplayPlan
import eu.emufii.app.library.DiscImageReader
import eu.emufii.app.library.Ps2DiscIdentity
import eu.emufii.app.session.RomRef
import eu.emufii.app.wg.WgConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object Ps2GameSettings {

    sealed interface Outcome {
        data class Success(val filename: String) : Outcome
        data object MissingFolderGrant : Outcome
        data object MissingPreparedCard : Outcome
        data object UnknownDiscIdentity : Outcome
        data class WriteFailed(val detail: String) : Outcome
    }

    fun canConfigure(context: Context, rom: RomRef): Boolean =
        identity(rom) != null &&
            Ps2NetworkProfile.rootUri(context) != null &&
            Ps2NetworkProfile.receipt(context) != null

    /** Libraries scanned by older builds lack the ELF CRC, so read the disc on demand. */
    suspend fun canConfigureNow(context: Context, rom: RomRef): Boolean = withContext(Dispatchers.IO) {
        rootAndCardPresent(context) && resolvedIdentity(context, rom) != null
    }

    private fun rootAndCardPresent(context: Context): Boolean =
        Ps2NetworkProfile.rootUri(context) != null && Ps2NetworkProfile.receipt(context) != null

    private fun resolvedIdentity(context: Context, rom: RomRef): Ps2DiscIdentity? =
        identity(rom) ?: DiscImageReader(context).read(rom.uri)?.ps2Identity

    /** Per-game layer loaded after ARMSX2's globals; only network and Slot 1 are set. */
    fun apply(context: Context, rom: RomRef, plan: NetplayPlan): Outcome {
        val room = plan.password.orEmpty().filter { it.isLetterOrDigit() && it.code < 128 }
            .take(Ps2Target.ROOM_CODE_LENGTH.last)
            .takeIf { it.length >= Ps2Target.ROOM_CODE_LENGTH.first }
            ?: return Outcome.WriteFailed("the ARMSX2 room code is invalid")
        val host = plan.role == NetplayPlan.Role.Host
        return write(
            context, rom,
            linkedMapOf(
                "EthEnable" to "true",
                "EthApi" to "Local Link",
                "LocalLinkHost" to host.toString(),
                "LocalLinkAddress" to if (host) null else WgConfig.PS2_HOST_NAME,
                "LocalLinkPort" to plan.port.toString(),
                "LocalLinkRoomCode" to room,
            ),
        )
    }

    /** Sockets mode forces ARMSX2's internal DHCP, which hands the game ModeDNS1/DNS1. */
    fun applyOnline(context: Context, rom: RomRef, dns: String): Outcome {
        if (!Ps2Revival.isUsable(dns)) return Outcome.WriteFailed("the revival DNS $dns is invalid")
        return write(
            context, rom,
            linkedMapOf(
                "EthEnable" to "true",
                "EthApi" to "Sockets",
                "EthDevice" to "Auto",
                "InterceptDHCP" to "true",
                "ModeDNS1" to "Manual",
                "DNS1" to dns,
                "ModeDNS2" to "Manual",
                "DNS2" to dns,
            ),
        )
    }

    private fun write(context: Context, rom: RomRef, eth: LinkedHashMap<String, String?>): Outcome = runCatching {
        val identity = resolvedIdentity(context, rom) ?: return Outcome.UnknownDiscIdentity
        val rootUri = Ps2NetworkProfile.rootUri(context) ?: return Outcome.MissingFolderGrant
        val receipt = Ps2NetworkProfile.receipt(context) ?: return Outcome.MissingPreparedCard
        val root = DocumentFile.fromTreeUri(context, rootUri)?.takeIf { it.isDirectory && it.canWrite() }
            ?: return Outcome.MissingFolderGrant
        val memcards = root.child("memcards") ?: return Outcome.MissingPreparedCard
        if (memcards.child(receipt.cardName)?.isFile != true) return Outcome.MissingPreparedCard
        val settings = root.child("gamesettings")
            ?: root.createDirectory("gamesettings")
            ?: return Outcome.WriteFailed("ARMSX2 did not create gamesettings")
        if (!settings.canWrite()) return Outcome.MissingFolderGrant

        val filename = identity.settingsFilename
        val target = settings.child(filename)
        val original = target?.let { readText(context, it) }.orEmpty()
        val merged = merge(
            original,
            linkedMapOf(
                "DEV9/Eth" to eth,
                "MemoryCards" to linkedMapOf(
                    "Slot1_Enable" to "true",
                    "Slot1_Filename" to receipt.cardName,
                ),
            ),
        )

        // Some SAF providers ack a write then publish a short file: stage and verify first.
        val tempName = ".emufii-${identity.serial}-${identity.elfCrc}.tmp"
        settings.child(tempName)?.delete()
        val temp = settings.createFile("application/octet-stream", tempName)
            ?: return Outcome.WriteFailed("ARMSX2 did not create the staging settings file")
        if (!writeAndVerify(context, temp, merged)) {
            temp.delete()
            return Outcome.WriteFailed("the staging settings file did not verify")
        }

        val published = target ?: createExactFile(context, settings, filename)
            ?: run {
                temp.delete()
                return Outcome.WriteFailed("ARMSX2 did not create $filename")
            }
        val verified = writeAndVerify(context, published, merged)
        temp.delete()
        if (!verified) return Outcome.WriteFailed("$filename did not verify after writing")
        Outcome.Success(filename)
    }.getOrElse { Outcome.WriteFailed(it.message ?: it.javaClass.simpleName) }

    /** Some providers rename text/plain to .ini.txt, which ARMSX2 ignores. */
    private fun createExactFile(context: Context, dir: DocumentFile, filename: String): DocumentFile? {
        val created = dir.createFile("application/octet-stream", filename) ?: return null
        if (created.name.equals(filename, ignoreCase = true)) {
            dir.listFiles().forEach { stale ->
                val name = stale.name ?: return@forEach
                if (stale.isFile && !name.equals(filename, ignoreCase = true) &&
                    name.startsWith(filename, ignoreCase = true)
                ) stale.delete()
            }
            return created
        }
        val renamed = runCatching {
            DocumentsContract.renameDocument(context.contentResolver, created.uri, filename)
        }.getOrNull() ?: run {
            created.delete()
            return null
        }
        val exact = DocumentFile.fromSingleUri(context, renamed)
        if (exact?.name?.equals(filename, ignoreCase = true) != true) {
            exact?.delete()
            return null
        }
        return exact
    }

    internal fun identity(rom: RomRef): Ps2DiscIdentity? = identity(rom.productCode, rom.ps2ElfCrc)

    internal fun identity(productCode: String?, elfCrc: String?): Ps2DiscIdentity? {
        val serial = productCode?.uppercase()?.takeIf { it.matches(SERIAL) } ?: return null
        val crc = elfCrc?.uppercase()?.takeIf { it.matches(CRC) } ?: return null
        return Ps2DiscIdentity(serial, crc)
    }

    internal fun merge(
        original: String,
        changes: LinkedHashMap<String, LinkedHashMap<String, String?>>,
    ): String {
        val newline = if (original.contains("\r\n")) "\r\n" else "\n"
        val lines = original.replace("\r\n", "\n").split('\n').toMutableList()
        if (lines.size == 1 && lines[0].isEmpty()) lines.clear()

        for ((section, wanted) in changes) {
            var start = lines.indexOfFirst { sectionName(it)?.equals(section, true) == true }
            if (start < 0) {
                if (lines.isNotEmpty() && lines.last().isNotBlank()) lines += ""
                lines += "[$section]"
                start = lines.lastIndex
            }
            var end = (start + 1 until lines.size).firstOrNull { sectionName(lines[it]) != null }
                ?: lines.size

            for ((key, value) in wanted) {
                val existing = (start + 1 until end).firstOrNull { lineKey(lines[it])?.equals(key, true) == true }
                if (existing != null) {
                    if (value == null) {
                        lines.removeAt(existing)
                        end--
                    } else {
                        lines[existing] = "$key = $value"
                    }
                } else if (value != null) {
                    lines.add(end, "$key = $value")
                    end++
                }
            }
        }
        return lines.joinToString(newline).trimEnd() + newline
    }

    private fun sectionName(line: String): String? {
        val trimmed = line.trim()
        return trimmed.takeIf { it.length >= 2 && it.first() == '[' && it.last() == ']' }
            ?.substring(1, trimmed.length - 1)?.trim()
    }

    private fun lineKey(line: String): String? {
        val trimmed = line.trimStart()
        if (trimmed.startsWith(';') || trimmed.startsWith('#')) return null
        val equals = trimmed.indexOf('=')
        if (equals <= 0) return null
        return trimmed.substring(0, equals).trim().takeIf { it.isNotEmpty() }
    }

    private fun DocumentFile.child(name: String): DocumentFile? =
        listFiles().firstOrNull { it.name.equals(name, ignoreCase = true) }

    private fun readText(context: Context, file: DocumentFile): String =
        context.contentResolver.openInputStream(file.uri)?.bufferedReader()?.use { it.readText() }.orEmpty()

    private fun writeAndVerify(context: Context, file: DocumentFile, text: String): Boolean {
        val bytes = text.toByteArray(Charsets.UTF_8)
        val resolver = context.contentResolver
        resolver.openOutputStream(file.uri, "wt")?.use { it.write(bytes) } ?: return false
        val readBack = resolver.openInputStream(file.uri)?.use { it.readBytes() } ?: return false
        return readBack.contentEquals(bytes)
    }

    private val SERIAL = Regex("^[A-Z0-9]{4}-[A-Z0-9]{5}$")
    private val CRC = Regex("^[0-9A-F]{8}$")
}
