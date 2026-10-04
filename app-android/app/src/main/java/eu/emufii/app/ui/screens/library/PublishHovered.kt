package eu.emufii.app.ui.screens.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import eu.emufii.app.compat.LocalCompatDb
import eu.emufii.app.library.RomTagReader
import eu.emufii.app.library.compatKeys
import eu.emufii.app.meta.LocalGameMetaDb
import eu.emufii.app.secondscreen.SecondScreen
import eu.emufii.app.secondscreen.SecondScreenModel
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

/** Debounced: publishing wakes the second window, and a run down the grid fired one per tile. */
private const val SECOND_SCREEN_SETTLE_MS = 200L

@Composable
internal fun PublishHovered(entries: List<Entry>, cursor: State<Int>) {
    val entry = entries.getOrNull(cursor.value)
    val hovered = (entry as? Entry.Game)?.rom
    val folder = (entry as? Entry.Folder)?.console
    val db = LocalCompatDb.current
    val meta = LocalGameMetaDb.current
    val published = remember { mutableStateOf<SecondScreenModel?>(null) }
    LaunchedEffect(hovered, folder, db, meta) {
        delay(SECOND_SCREEN_SETTLE_MS.milliseconds)
        val face =
            folder?.let { SecondScreenModel.ConsoleFolder(it) } ?: hovered?.let { rom ->
                SecondScreenModel.Browsing(
                    rom = rom,
                    rating = db.ratingFor(rom.compatKeys())?.rating,
                    tags = RomTagReader.read(rom),
                    meta = meta.metaFor(rom.compatKeys()),
                )
            } ?: SecondScreenModel.Idle
        published.value = face
        SecondScreen.publish(face)
    }
    DisposableEffect(Unit) { onDispose { SecondScreen.clearIfShowing(published.value) } }
}
