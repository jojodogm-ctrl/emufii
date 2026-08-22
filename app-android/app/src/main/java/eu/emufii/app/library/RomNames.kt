package eu.emufii.app.library

import android.content.Context
import androidx.core.content.edit

/**
 * The names the player has given their games themselves.
 *
 * The file on disk is never touched. Renaming here renames only inside Emufii:
 * the ROM keeps its name, its saves stay paired with it, and a third-party
 * emulator that knows it by path sees nothing change. A library app that
 * rewrites a player's files is an app that breaks what it does not know about.
 *
 * Serves as the last resort for everything automatic reading misses. Titles come
 * from the SMDH or the banner, and those fields are whatever the publisher saw
 * fit to put there, sometimes truncated, sometimes in Japanese, sometimes the
 * tagline rather than the title. No rule catches every case; a text box does.
 */
class RomNames(context: Context) {

    private val prefs =
        context.applicationContext.getSharedPreferences("rom_names", Context.MODE_PRIVATE)

    /**
     * The key is the title id when the console carries one, the filename
     * otherwise. On no account the displayed name: that changes at the first
     * rename, and the key would be lost with it, the chosen name having
     * disappeared by the second launch.
     */
    private fun key(rom: Rom): String = rom.sessionId ?: rom.filename

    fun nameFor(rom: Rom): String? =
        prefs.getString(key(rom), null)?.takeIf { it.isNotBlank() }

    /** An empty name clears the choice and gives the game its original title back. */
    fun setName(rom: Rom, name: String) {
        val cleaned = name.trim()
        prefs.edit {
            if (cleaned.isEmpty()) remove(key(rom)) else putString(key(rom), cleaned)
        }
    }

    /** Applies the chosen name, when there is one. */
    fun apply(rom: Rom): Rom =
        nameFor(rom)?.let { rom.copy(displayName = it) } ?: rom
}
