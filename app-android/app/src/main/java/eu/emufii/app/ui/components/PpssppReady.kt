package eu.emufii.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import eu.emufii.app.psp.PpssppConfigStore

/** The SAF walk is not main-thread work: answer cheaply first, then correct. */
@Composable
fun rememberPpssppReady(): Boolean {
    val context = LocalContext.current
    val ready by produceState(
        initialValue = PpssppConfigStore(context).rootUri() != null,
        context
    ) {
        value = PpssppConfigStore(context).isReady()
    }
    return ready
}

/** DS local wireless only runs on the Emufii Edition of WatermelonDS (or its dev build). */
@Composable
fun rememberDsEditionPicked(): Boolean {
    val context = LocalContext.current
    return androidx.compose.runtime.remember(context) {
        eu.emufii.app.library.EmulatorPick.packageFor(context, eu.emufii.app.library.Console.DS) in
            setOf(eu.emufii.app.wfc.MelonDsPackage.DUALS_EMUFII, eu.emufii.app.wfc.MelonDsPackage.DUALS_DEV)
    }
}
