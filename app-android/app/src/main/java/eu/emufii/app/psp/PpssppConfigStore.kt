package eu.emufii.app.psp

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.content.edit
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.util.Properties

sealed interface PpssppConfigResult {
    data object Success : PpssppConfigResult
    data object NotConfigured : PpssppConfigResult
    data object PermissionMissing : PpssppConfigResult
    data object InvalidRoot : PpssppConfigResult
    data object UnknownDiscId : PpssppConfigResult
    data object ActiveOverrides : PpssppConfigResult
    data class Failure(val detail: String) : PpssppConfigResult
}

/**
 * The one bridge stock PPSSPP exposes to another Android application: the player grants
 * its memory-stick tree once, and we edit only `PSP/SYSTEM/<DISC_ID>_ppsspp.ini`, which
 * PPSSPP loads as the game boots. No broad storage permission, root or fork involved.
 */
class PpssppConfigStore(context: Context) {
    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val backups = File(appContext.filesDir, BACKUP_DIR)

    fun rootUri(): Uri? = prefs.getString(KEY_ROOT, null)?.let(Uri::parse)

    fun rootLabel(): String? = rootUri()?.let { uri ->
        runCatching { DocumentsContract.getTreeDocumentId(uri) }
            .getOrNull()
            ?.substringAfter(':')
            ?.takeIf(String::isNotBlank)
            ?: uri.lastPathSegment
    }

    fun isReady(): Boolean {
        val uri = rootUri() ?: return false
        return hasPersistedWrite(uri) && resolveSystem(uri) != null
    }

    fun canApply(productCode: String?, filename: String?, displayName: String?): Boolean =
        isReady() && PpssppIni.resolveDiscId(productCode, filename, displayName) != null

    /** Call from an ACTION_OPEN_DOCUMENT_TREE result, while its grant is still attached. */
    fun configureRoot(uri: Uri): PpssppConfigResult {
        val previous = rootUri()
        if (previous != null && previous != uri && backupFiles().isNotEmpty()) {
            // Those backups belong to the old memory stick: forgetting where they came
            // from would strand its private-session values there.
            return PpssppConfigResult.ActiveOverrides
        }
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
            Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        return runCatching {
            resolver.takePersistableUriPermission(uri, flags)
            if (!hasPersistedWrite(uri)) return PpssppConfigResult.PermissionMissing
            if (resolveSystem(uri) == null) return PpssppConfigResult.InvalidRoot
            prefs.edit { putString(KEY_ROOT, uri.toString()) }
            PpssppConfigResult.Success
        }.getOrElse { PpssppConfigResult.Failure(it.message ?: it.javaClass.simpleName) }
    }

    fun applyPrivate(
        productCode: String?,
        filename: String?,
        displayName: String?,
    ): PpssppConfigResult = apply(productCode, filename, displayName, PpssppIni::privateConfig)

    /** Public ad hoc on PPSSPP's default server; restored like the private values. */
    fun applyPublic(
        productCode: String?,
        filename: String?,
        displayName: String?,
        host: String,
    ): PpssppConfigResult = apply(productCode, filename, displayName) { PpssppIni.publicConfig(it, host) }

    private fun apply(
        productCode: String?,
        filename: String?,
        displayName: String?,
        edit: (String) -> String,
    ): PpssppConfigResult {
        val id = PpssppIni.resolveDiscId(productCode, filename, displayName)
            ?: return PpssppConfigResult.UnknownDiscId
        val uri = rootUri() ?: return PpssppConfigResult.NotConfigured
        if (!hasPersistedWrite(uri)) return PpssppConfigResult.PermissionMissing
        val system = resolveSystem(uri) ?: return PpssppConfigResult.InvalidRoot
        return runCatching {
            val name = gameConfigName(id)
            var target = system.child(name)
            val existed = target != null
            val current = target?.let(::readText).orEmpty()
            val backup = backupFile(id)
            if (!backup.exists()) {
                writeBackup(backup, uri, PpssppIni.snapshot(current, existed))
            }
            if (target == null) {
                target = system.createFile("application/octet-stream", name)
                    ?: error("PPSSPP game config could not be created")
            }
            writeVerified(target, edit(current))
            PpssppConfigResult.Success
        }.getOrElse { PpssppConfigResult.Failure(it.message ?: it.javaClass.simpleName) }
    }

    /** Only the four values we borrowed, so changes made during the game survive. */
    fun restorePublic(
        productCode: String?,
        filename: String?,
        displayName: String?,
    ): PpssppConfigResult {
        val id = PpssppIni.resolveDiscId(productCode, filename, displayName)
            // applyPrivate() refuses an unknown ID, so none can have an override.
            ?: return PpssppConfigResult.Success
        val backup = backupFile(id)
        // Never put in private mode: nothing to restore, and no folder grant needed.
        if (!backup.exists()) return PpssppConfigResult.Success
        val uri = rootUri() ?: return PpssppConfigResult.NotConfigured
        if (!hasPersistedWrite(uri)) return PpssppConfigResult.PermissionMissing
        val system = resolveSystem(uri) ?: return PpssppConfigResult.InvalidRoot
        return runCatching {
            val saved = readBackup(backup)
            check(saved.rootUri == uri.toString()) { "PPSSPP memory stick changed" }
            val name = gameConfigName(id)
            var target = system.child(name)
            val current = target?.let(::readText).orEmpty()
            val restored = PpssppIni.restore(current, saved.snapshot)
            if (!saved.snapshot.fileExisted && PpssppIni.hasNoAssignments(restored)) {
                target?.let { check(it.delete()) { "PPSSPP game config could not be removed" } }
            } else {
                if (target == null) {
                    target = system.createFile("application/octet-stream", name)
                        ?: error("PPSSPP game config could not be restored")
                }
                writeVerified(target, restored)
            }
            check(backup.delete()) { "PPSSPP backup could not be retired" }
            PpssppConfigResult.Success
        }.getOrElse { PpssppConfigResult.Failure(it.message ?: it.javaClass.simpleName) }
    }

    fun activeOverrideCount(): Int = backupFiles().size

    private data class StoredBackup(
        val rootUri: String,
        val snapshot: PpssppNetworkSnapshot,
    )

    private fun resolveSystem(uri: Uri): DocumentFile? {
        val root = DocumentFile.fromTreeUri(appContext, uri)?.takeIf { it.isDirectory }
            ?: return null
        val psp = root.child("PSP")?.takeIf { it.isDirectory } ?: return null
        val system = psp.child("SYSTEM")?.takeIf { it.isDirectory } ?: return null
        val global = system.child("ppsspp.ini")?.takeIf { it.isFile } ?: return null
        // Stronger than DocumentFile's capability flags, which some providers report
        // optimistically.
        runCatching { resolver.openInputStream(global.uri)?.use { it.read() } }.getOrNull()
            ?: return null
        return system
    }

    private fun hasPersistedWrite(uri: Uri): Boolean = resolver.persistedUriPermissions.any {
        it.uri == uri && it.isReadPermission && it.isWritePermission
    }

    private fun readText(file: DocumentFile): String = resolver.openInputStream(file.uri)
        ?.bufferedReader(Charsets.UTF_8)
        ?.use { it.readText() }
        ?: error("PPSSPP game config could not be read")

    private fun writeVerified(file: DocumentFile, text: String) {
        resolver.openOutputStream(file.uri, "wt")
            ?.bufferedWriter(Charsets.UTF_8)
            ?.use { writer ->
                writer.write(text)
                writer.flush()
            }
            ?: error("PPSSPP game config could not be written")
        check(readText(file) == text) { "PPSSPP game config verification failed" }
    }

    private fun writeBackup(file: File, root: Uri, snapshot: PpssppNetworkSnapshot) {
        backups.mkdirs()
        val properties = Properties().apply {
            setProperty(BACKUP_ROOT, root.toString())
            setProperty(BACKUP_EXISTED, snapshot.fileExisted.toString())
            PRIVATE_NETWORK.keys.forEach { key ->
                val value = snapshot.values.getValue(key)
                setProperty("$key.present", value.present.toString())
                if (value.present) setProperty("$key.value", value.value)
            }
        }
        val temporary = File(backups, ".${file.name}.tmp")
        temporary.outputStream().use { properties.store(it, null) }
        check(temporary.renameTo(file)) { "PPSSPP backup could not be published" }
    }

    private fun readBackup(file: File): StoredBackup {
        val properties = Properties().apply { file.inputStream().use(::load) }
        val values = PRIVATE_NETWORK.keys.associateWith { key ->
            val present = properties.getProperty("$key.present")?.toBooleanStrictOrNull() ?: false
            PpssppIniValue(present, properties.getProperty("$key.value").orEmpty())
        }
        return StoredBackup(
            rootUri = properties.getProperty(BACKUP_ROOT) ?: error("PPSSPP backup has no root"),
            snapshot = PpssppNetworkSnapshot(
                fileExisted = properties.getProperty(BACKUP_EXISTED)?.toBooleanStrictOrNull() ?: false,
                values = values,
            ),
        )
    }

    private fun backupFile(id: String): File = File(backups, "$id.properties")
    private fun backupFiles(): List<File> = backups.listFiles { file ->
        file.isFile && file.extension == "properties"
    }?.toList().orEmpty()

    private fun gameConfigName(id: String) = "${id}_ppsspp.ini"

    private fun DocumentFile.child(name: String): DocumentFile? =
        listFiles().firstOrNull { it.name.equals(name, ignoreCase = true) }

    private companion object {
        const val PREFS = "emufii_ppsspp"
        const val KEY_ROOT = "memory_stick_root"
        const val BACKUP_DIR = "ppsspp-network-backups"
        const val BACKUP_ROOT = "rootUri"
        const val BACKUP_EXISTED = "fileExisted"
    }
}
