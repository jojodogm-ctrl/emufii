package eu.emufii.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import eu.emufii.app.ps2.Ps2NetworkProfile

/** isReady costs ~175 ms of file reads: draw with a cached verdict, check off the main thread. */
@Composable
fun rememberPs2Ready(): Boolean {
    val context = LocalContext.current
    val ready by produceState(initialValue = Ps2NetworkProfile.isReadyQuick(context), context) {
        value = Ps2NetworkProfile.verifyReady(context)
    }
    return ready
}
