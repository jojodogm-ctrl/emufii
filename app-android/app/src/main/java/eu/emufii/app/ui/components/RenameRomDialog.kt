package eu.emufii.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import eu.emufii.app.R
import eu.emufii.app.library.Rom
import eu.emufii.app.library.RomNames

/**
 * Renaming a game, inside Emufii only.
 *
 * The file on disk is not touched: the ROM keeps its name, its saves stay paired
 * with it, and a third-party emulator that knows it by path sees nothing change.
 *
 * This is the last resort when automatic reading fails. Titles come from the
 * SMDH or the banner, that is, from whatever the publisher saw fit to write
 * there: sometimes truncated, sometimes in Japanese, sometimes a tagline rather
 * than a title. No rule catches every case; a text box does.
 */
@Composable
fun RenameRomDialog(
    rom: Rom,
    onRenamed: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val names = remember(context) { RomNames(context.applicationContext) }
    var name by remember(rom.uri) { mutableStateOf(rom.displayName) }

    PadDialog(
        title = stringResource(R.string.rename_title),
        onDismiss = onDismiss,
        actions = {
            // Clearing the field gives the game its original title back: no need
            // for a third button to say "undo my rename".
            GhostButton(
                label = stringResource(R.string.rename_cancel),
                onClick = onDismiss
            )
            PrimaryButton(
                label = stringResource(R.string.rename_save),
                onClick = {
                    names.setName(rom, name)
                    onRenamed()
                }
            )
        }
    ) {
        PadDialogText(stringResource(R.string.rename_body))
        // A `PadTextField`, and not the bare `OutlinedTextField` that was here:
        // a field that merely takes focus opens the soft keyboard, so on a pad
        // the cursor passing over it made the keyboard cover the dialog and
        // swallow the directions. The frame is the step in the traversal; A
        // goes in, B comes back out.
        PadTextField(
            value = name,
            onValueChange = { name = it },
            singleLine = true
        )
    }
}
