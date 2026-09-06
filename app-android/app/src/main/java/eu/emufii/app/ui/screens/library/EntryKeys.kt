package eu.emufii.app.ui.screens.library

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import eu.emufii.app.library.Rom
import eu.emufii.app.secondscreen.SecondScreen
import eu.emufii.app.ui.CONFIRM_KEYS
import eu.emufii.app.ui.Sfx

/**
 * Shared by all three layouts, so a fix in one cannot leave the others broken.
 * pourquoi : docs/decisions/bibliotheque.md § Three layouts, one cursor contract
 */
internal fun entryKeys(
    entries: List<Entry>,
    cursorIndex: () -> Int,
    onSelect: (Entry) -> Unit,
    onLongPress: (Rom) -> Unit,
    onBack: () -> Unit,
    canGoBack: Boolean,
    hold: ConfirmHold,
    directions: (Key) -> Boolean?,
): (KeyEvent) -> Boolean = keys@{ event ->
    // The one key read on the way up as well as down: that separates a press from a
    // hold, everything else being decided on KeyDown.
    if (event.key in CONFIRM_KEYS) {
        val entry = entries.getOrNull(cursorIndex())
        return@keys when (event.type) {
            KeyEventType.KeyDown -> {
                // Auto-repeat sends KeyDown again while held; only the first starts the timer,
                // or the menu fires on the last repeat instead of on time.
                if (!hold.down) {
                    hold.press { (entry as? Entry.Game)?.let { Sfx.click(); onLongPress(it.rom) } }
                }
                true
            }

            KeyEventType.KeyUp -> {
                // A hold that opened the menu must not also launch on release.
                if (hold.release() && entry != null) {
                    Sfx.click(); onSelect(entry)
                }
                true
            }

            else -> false
        }
    }
    if (event.type != KeyEventType.KeyDown) return@keys false
    directions(event.key)?.let { return@keys it }
    when (event.key) {
        // Y opens the menu outright, with no wait; the hold is what someone coming from
        // touch tries first.
        Key.ButtonY ->
            (entries.getOrNull(cursorIndex()) as? Entry.Game)
                ?.let { Sfx.click(); onLongPress(it.rom); true } ?: false
        // B goes up a folder, as on every console; `false` when there is nowhere to go
        // lets the system close the screen.
        Key.ButtonB, Key.Back -> if (canGoBack) {
            onBack(); true
        } else false
        // Keyboard focus goes to a window, and on a one-screen machine this is the one
        // that has it.
        // pourquoi : docs/decisions/second-ecran.md § R turns the page from both screens
        Key.ButtonR1 -> {
            SecondScreen.flipPage(); true
        }

        else -> false
    }
}
