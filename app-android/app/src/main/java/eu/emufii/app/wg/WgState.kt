package eu.emufii.app.wg

/**
 * Who holds the tunnel, and where it is in coming up.
 *
 * `Starting` is a state of its own on purpose: `TunnelSlot` derives Android's
 * single-VPN-slot occupancy from this, and treating a tunnel that is coming up
 * as "not yet held" is exactly the window where two tunnels collide.
 */
sealed interface WgState {
    data object Idle : WgState

    /** `establish()` may already have happened, this counts as holding the slot. */
    data class Starting(val code: String) : WgState

    /**
     * The tunnel is up. [ip] is this device's address on the session subnet.
     *
     * Note that "up" here means the interface exists and the handshake completed,
     * not that another player has joined.
     */
    data class Online(val code: String, val ip: String) : WgState

    /** The interface exists but no handshake has landed yet, or it went stale. */
    data class Offline(val code: String) : WgState

    data object Stopping : WgState

    data class Error(val message: String) : WgState
}
