package eu.emufii.app.library

import android.content.Context
import androidx.core.content.edit

/**
 * The games the player has taken out of their library.
 *
 * Nothing is deleted. The file stays where it is, with its saves, and any other
 * emulator that knows it by path sees no change — the same rule as [RomNames],
 * and for the same reason: a library app that removes a player's files is an app
 * that breaks what it does not know about.
 *
 * What this answers is a grid problem. A ROMs folder holds things that are not
 * games one plays together — a BIOS dump, a homebrew test, three regional copies
 * of one title — and they take a tile each in a menu built to be crossed with a
 * stick. Hiding them costs one entry in the tile's menu and gives the grid back.
 *
 * Reversible on purpose, from the settings: a choice made in a long-press menu
 * has to be undoable somewhere a player will actually look, or hiding a game by
 * mistake means rebuilding the folder to get it back.
 */
class HiddenRoms(context: Context) {

    private val prefs =
        context.applicationContext.getSharedPreferences("rom_hidden", Context.MODE_PRIVATE)

    /**
     * Keyed like a rename, and it has to stay that way: the title id when the
     * console carries one, the filename otherwise. Never the displayed name,
     * which a rename moves out from under the key.
     */
    private fun key(rom: Rom): String = rom.sessionId ?: rom.filename

    fun isHidden(rom: Rom): Boolean = prefs.contains(key(rom))

    fun hide(rom: Rom) {
        prefs.edit { putBoolean(key(rom), true) }
    }

    /** How many are hidden, for the settings row that offers to bring them back. */
    fun count(): Int = prefs.all.size

    fun clear() {
        prefs.edit { clear() }
    }
}
