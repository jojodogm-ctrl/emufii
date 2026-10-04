package eu.emufii.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.runtime.withFrameNanos

@Composable
fun LandOn(target: FocusRequester, key: Any? = Unit, enabled: Boolean = true) {
    val inputMode = LocalInputModeManager.current
    LaunchedEffect(key, enabled) {
        if (!enabled) return@LaunchedEffect
        repeat(LANDING_FRAMES) {
            withFrameNanos { }
            inputMode.requestInputMode(InputMode.Keyboard)
            runCatching { target.requestFocus() }
        }
    }
}

/** About 100 ms: the target is placed only after the layer's entrance animation. */
private const val LANDING_FRAMES = 6
