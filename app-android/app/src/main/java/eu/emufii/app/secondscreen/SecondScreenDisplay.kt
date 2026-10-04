package eu.emufii.app.secondscreen

import android.content.Context
import android.hardware.display.DisplayManager
import android.view.Display
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

// Found by FLAG_PRESENTATION, never by id: firmware may renumber the display.
@Composable
fun rememberPresentationDisplay(): State<Display?> {
    val context = LocalContext.current
    val manager = remember(context) {
        context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
    }
    val state = remember { mutableStateOf(manager.presentationDisplay()) }

    DisposableEffect(manager) {
        val listener = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) {
                state.value = manager.presentationDisplay()
            }

            override fun onDisplayRemoved(displayId: Int) {
                state.value = manager.presentationDisplay()
            }

            override fun onDisplayChanged(displayId: Int) {
                // A panel switched off reports as changed, not removed.
                state.value = manager.presentationDisplay()
            }
        }
        manager.registerDisplayListener(listener, null)
        onDispose { manager.unregisterDisplayListener(listener) }
    }
    return state
}

/** Skip STATE_OFF displays: showing a Presentation on one throws. */
private fun DisplayManager.presentationDisplay(): Display? =
    getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
        .firstOrNull { it.isValid && it.state == Display.STATE_ON }
