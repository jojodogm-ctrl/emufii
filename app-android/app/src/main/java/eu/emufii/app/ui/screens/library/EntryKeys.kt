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
    if (SecondScreen.gallery.value != null) {
        if (event.type == KeyEventType.KeyDown) {
            when (event.key) {
                Key.DirectionLeft -> { Sfx.hover(); SecondScreen.moveGallery(-1) }
                Key.DirectionRight -> { Sfx.hover(); SecondScreen.moveGallery(1) }
                Key.ButtonB, Key.Back, Key.ButtonX, Key.ButtonA, Key.Enter ->
                    { Sfx.click(); SecondScreen.closeGallery() }
                Key.ButtonR1 -> SecondScreen.flipPage()
                else -> Unit
            }
        }
        return@keys true
    }
    if (event.key in CONFIRM_KEYS) {
        val entry = entries.getOrNull(cursorIndex())
        return@keys when (event.type) {
            KeyEventType.KeyDown -> {
                // Auto-repeat resends KeyDown; only the first starts the hold timer.
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
        Key.ButtonY ->
            (entries.getOrNull(cursorIndex()) as? Entry.Game)
                ?.let { Sfx.click(); onLongPress(it.rom); true } ?: false
        Key.ButtonB, Key.Back -> if (canGoBack) {
            onBack(); true
        } else false
        Key.ButtonR1 -> {
            SecondScreen.flipPage(); true
        }
        Key.ButtonX -> SecondScreen.openGallery().also { if (it) Sfx.click() }

        else -> false
    }
}
