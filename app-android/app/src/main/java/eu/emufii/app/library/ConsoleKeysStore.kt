package eu.emufii.app.library

import android.content.Context
import android.net.Uri
import android.util.Log
import eu.emufii.app.library.switchfs.SwitchKeys
import java.io.File

/**
 * The player's own `prod.keys`, kept where Emufii can read it.
 *
 * Until now the file was only ever picked up if it happened to sit in the ROM
 * folder, which is where the Switch emulators look, convenient when it is
 * true, and a dead end otherwise, with nothing in the app to say why a Switch
 * tile showed initials. This lets the player point at the file wherever it
 * lives, once.
 *
 * The contents are copied into Emufii's private storage rather than kept as a
 * SAF uri: a uri permission can be revoked, and the folder it came from may be
 * removable. What is stored is only what [SwitchKeys] keeps, the three key
 * families needed to open an NCA header, never the whole file, because
 * everything else in it is a console secret Emufii has no use for.
 *
 * Nothing here is ever uploaded, and the file is only read to draw an icon.
 */
class ConsoleKeysStore(private val context: Context) {

    private val file: File get() = File(context.filesDir, FILE)

    val hasKeys: Boolean get() = file.exists() && file.length() > 0

    /** Keeps the usable subset of the picked file, and says whether it was any good. */
    fun import(uri: Uri): Boolean {
        val text = runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                input.bufferedReader().readText().take(MAX_CHARS)
            }
        }.onFailure { Log.w(TAG, "keys file unreadable", it) }.getOrNull() ?: return false

        val parsed = SwitchKeys.parse(text)
        if (!parsed.isUsable) return false
        runCatching { file.writeText(parsed.toKeyFile()) }
            .onFailure { Log.w(TAG, "could not store keys", it); return false }
        return true
    }

    fun clear() {
        runCatching { file.delete() }
    }

    fun load(): SwitchKeys? {
        if (!hasKeys) return null
        return runCatching { SwitchKeys.parse(file.readText()) }.getOrNull()?.takeIf { it.isUsable }
    }

    private companion object {
        const val TAG = "ConsoleKeys"
        const val FILE = "console.keys"

        /** A real prod.keys is ~30 KB; anything far larger is not one. */
        const val MAX_CHARS = 512 * 1024
    }
}
