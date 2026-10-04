package eu.emufii.app.library

import android.content.Context
import androidx.core.content.edit

class RomNames(context: Context) {

    private val prefs =
        context.applicationContext.getSharedPreferences("rom_names", Context.MODE_PRIVATE)

    /** Never key on the displayed name: it changes at the first rename. */
    private fun key(rom: Rom): String = rom.sessionId ?: rom.filename

    fun nameFor(rom: Rom): String? =
        prefs.getString(key(rom), null)?.takeIf { it.isNotBlank() }

    fun setName(rom: Rom, name: String) {
        val cleaned = name.trim()
        prefs.edit {
            if (cleaned.isEmpty()) remove(key(rom)) else putString(key(rom), cleaned)
        }
    }

    fun apply(rom: Rom): Rom =
        nameFor(rom)?.let { rom.copy(displayName = it) } ?: rom
}
