package eu.emufii.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import eu.emufii.app.R
import eu.emufii.app.library.HiddenRoms
import eu.emufii.app.library.Rom
import eu.emufii.app.ui.theme.ShellRed

/**
 * Taking a game out of the library, with the one sentence that has to be read.
 *
 * A confirmation, for an action whose effect is invisible: the tile disappears,
 * and nothing on the grid afterwards says where it went. So the dialog spends
 * its two lines on exactly that — the file is untouched, and the settings bring
 * it back — rather than on asking "are you sure?", which tells a player nothing
 * they did not already know when they opened the menu.
 */
@Composable
fun HideRomDialog(
    rom: Rom,
    onHidden: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val hidden = remember(context) { HiddenRoms(context.applicationContext) }

    PadDialog(
        title = stringResource(R.string.hide_title, rom.displayName),
        onDismiss = onDismiss,
        actions = {
            GhostButton(
                label = stringResource(R.string.hide_cancel),
                onClick = onDismiss
            )
            GhostButton(
                label = stringResource(R.string.hide_confirm),
                onClick = {
                    hidden.hide(rom)
                    onHidden()
                },
                tint = ShellRed
            )
        }
    ) {
        PadDialogText(stringResource(R.string.hide_body))
    }
}
