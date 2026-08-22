package eu.emufii.app.tunnel

import eu.emufii.app.wfc.WfcState
import eu.emufii.app.wg.WgState

/**
 * Android runs one `VpnService` at a time, and Emufii has two: the session
 * tunnel, and the DNS tunnel that sends the DS to Kaeru. Whichever calls
 * `establish()` second wins, and the other is revoked without app or player
 * being asked.
 *
 * Not a theoretical race. Leaving the WFC screen by the system back gesture
 * keeps its tunnel up, and creating a session afterwards cuts the DS game loose
 * mid-play. It happens the other way too: the session service is `START_STICKY`
 * and foregrounded, so it outlives the activity.
 *
 * Who holds the slot is derived from the states the services already publish,
 * rather than tracked separately.
 */
enum class TunnelHolder { NONE, SESSION, WFC }

/**
 * Who currently occupies Android's VPN slot.
 *
 * `Starting` counts as held: `establish()` may already have happened, and
 * treating it as free is exactly the window where two tunnels collide.
 * `Stopping` and `Error` do not, the descriptor is on its way out or never
 * opened.
 *
 * SESSION wins ties: an overlap means one is a leftover mid-teardown, and the
 * session is the one whose loss costs the player something.
 */
fun tunnelHolder(
    session: WgState,
    wfc: WfcState
): TunnelHolder = when {
    session is WgState.Starting || session is WgState.Online || session is WgState.Offline ->
        TunnelHolder.SESSION
    wfc is WfcState.Active -> TunnelHolder.WFC
    else -> TunnelHolder.NONE
}

/**
 * Whether [want] can take the slot without cutting anything.
 *
 * Asking for the slot you already hold is free: moving the session tunnel to
 * another game is a restart, not a conflict.
 */
fun slotIsFree(
    session: WgState,
    wfc: WfcState,
    want: TunnelHolder
): Boolean {
    val held = tunnelHolder(session, wfc)
    return held == TunnelHolder.NONE || held == want
}
