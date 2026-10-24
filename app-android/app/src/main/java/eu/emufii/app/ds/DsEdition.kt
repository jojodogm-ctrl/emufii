package eu.emufii.app.ds

import android.content.Context
import eu.emufii.app.library.Console
import eu.emufii.app.library.Rom
import java.util.Locale

/** A game Emufii ships its own edition of: the cartridge is patched for public Emufii rooms.
 * Unticking the edition gives back the original game, with Download Play and Kaeru WFC. */
enum class DsEdition(private val gameCode: String) {
    MARIO_KART_DS("AMC"),
    MARIO_BROS("A2D");

    companion object {
        private const val PREFS = "ds_edition"

        /** Product code only: it names the game, and the GOLD profile still decides whether the edition runs. */
        fun of(rom: Rom): DsEdition? {
            if (rom.console != Console.DS) return null
            val code = rom.productCode?.trim()?.uppercase(Locale.ROOT) ?: return null
            val match = Regex("(?:NDS-([A-Z0-9]{4})-[A-Z0-9]{2}|([A-Z0-9]{4}))").matchEntire(code) ?: return null
            val gameCode = match.groupValues[1].ifEmpty { match.groupValues[2] }
            return entries.firstOrNull { it.gameCode == gameCode.take(3) }
        }

        /** On until the player unticks it, then remembered for that game. */
        fun wanted(context: Context, rom: Rom): Boolean {
            if (of(rom) == null) return false
            return prefs(context).getBoolean(key(rom), true)
        }

        fun remember(context: Context, rom: Rom, on: Boolean) {
            if (of(rom) == null) return
            prefs(context).edit().putBoolean(key(rom), on).apply()
        }

        // Per game, not per file: a second dump of the same game keeps the choice.
        private fun key(rom: Rom): String = rom.sessionId ?: rom.filename

        private fun prefs(context: Context) =
            context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }
}
