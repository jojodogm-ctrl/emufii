package eu.emufii.app.tunnel

import eu.emufii.app.wfc.WfcState
import eu.emufii.app.wg.WgState

/** Android runs one VpnService at a time; the second establish() wins silently. */
enum class TunnelHolder { NONE, SESSION, WFC }

fun tunnelHolder(
    session: WgState,
    wfc: WfcState
): TunnelHolder = when {
    session is WgState.Starting || session is WgState.Online || session is WgState.Offline ->
        TunnelHolder.SESSION
    wfc is WfcState.Active -> TunnelHolder.WFC
    else -> TunnelHolder.NONE
}

fun slotIsFree(
    session: WgState,
    wfc: WfcState,
    want: TunnelHolder
): Boolean {
    val held = tunnelHolder(session, wfc)
    return held == TunnelHolder.NONE || held == want
}
