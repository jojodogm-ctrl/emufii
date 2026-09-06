package eu.emufii.app.ui.screens.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import eu.emufii.app.compat.LocalCompatDb
import eu.emufii.app.library.RomTagReader
import eu.emufii.app.library.compatKeys
import eu.emufii.app.meta.LocalGameMetaDb
import eu.emufii.app.secondscreen.SecondScreen
import eu.emufii.app.secondscreen.SecondScreenModel
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

/**
 * Publishing wakes the second window, and a run down the grid fired one per tile.
 * pourquoi : docs/decisions/bibliotheque.md § What wakes the second screen has a threshold, and it was too short
 */
private const val SECOND_SCREEN_SETTLE_MS = 200L

/**
 * One place, called by all three cursor owners; restores the resting face.
 * pourquoi : docs/decisions/bibliotheque.md § What is published to the second screen
 */
@Composable
internal fun PublishHovered(entries: List<Entry>, cursor: State<Int>) {
    // The only place outside the tiles subscribing to the cursor: it renders nothing,
    // so its recomposition costs only itself.
    val entry = entries.getOrNull(cursor.value)
    val hovered = (entry as? Entry.Game)?.rom
    val folder = (entry as? Entry.Folder)?.console
    val db = LocalCompatDb.current
    val meta = LocalGameMetaDb.current
    LaunchedEffect(hovered, folder, db, meta) {
        // Cancelled and restarted on each move: only what the player stopped on is
        // announced.
        // pourquoi : docs/decisions/bibliotheque.md § What is published to the second screen
        delay(SECOND_SCREEN_SETTLE_MS.milliseconds)
        SecondScreen.publish(
            folder?.let { SecondScreenModel.ConsoleFolder(it) } ?: hovered?.let { rom ->
                SecondScreenModel.Browsing(
                    rom = rom,
                    rating = db.ratingFor(rom.compatKeys())?.rating,
                    // Off the ROM the cursor is on, never off the disc: a move must not
                    // cost a file read.
                    tags = RomTagReader.read(rom),
                    meta = meta.metaFor(rom.compatKeys()),
                )
            } ?: SecondScreenModel.Idle
        )
    }
    DisposableEffect(Unit) { onDispose { SecondScreen.clear() } }
}
