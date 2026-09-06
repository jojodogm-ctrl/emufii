package eu.emufii.app.ui.screens.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

/** How long A is held before the tile's menu opens, matching touch's own delay. */
private const val HOLD_TO_MENU_MS = 480L

/**
 * A press does exactly one thing: menu on the hold, or launch on release.
 * pourquoi : docs/decisions/bibliotheque.md § Holding A, and the title that fades out
 */
internal class ConfirmHold(private val scope: CoroutineScope) {
    /** Compose state, not a plain field: the tile reads it to sink while held. */
    var down by mutableStateOf(false)
        private set
    private var fired = false
    private var job: Job? = null

    fun press(onHold: () -> Unit) {
        down = true
        fired = false
        job = scope.launch {
            delay(HOLD_TO_MENU_MS.milliseconds)
            fired = true
            onHold()
        }
    }

    fun release(): Boolean {
        down = false
        job?.cancel()
        job = null
        return !fired
    }
}

@Composable
internal fun rememberConfirmHold(): ConfirmHold {
    val scope = rememberCoroutineScope()
    return remember(scope) { ConfirmHold(scope) }
}
