package eu.emufii.app.wg

import android.content.Context
import android.content.Intent
import android.net.VpnService
import kotlinx.coroutines.flow.StateFlow

/**
 * How the rest of the app talks to the session tunnel.
 *
 * A state flow, `prepare`, `start`, `stop`, that is the whole surface the
 * screens and `TunnelSlot` see. Starting a tunnel provisions nothing anywhere:
 * the coordinator hands back an address and how to reach the relay, and that is
 * all it takes.
 */
object EmufiiWgManager {

    val state: StateFlow<WgState> get() = EmufiiWgService.state

    /** Returns null if VPN permission is already granted, else the Intent to launch. */
    fun prepare(ctx: Context): Intent? = VpnService.prepare(ctx)

    /**
     * Brings the tunnel up for [code] using what the coordinator returned.
     *
     * Started as a foreground service, which is the whole point, see the note in
     * [EmufiiWgService] about `GoBackend` starting its own in the background.
     */
    /**
     * [announceDns] is true for the PS2 only; see [WgConfig.render]'s `dns`
     * parameter for what that commits to.
     */
    fun start(ctx: Context, code: String, info: WgTunnelInfo, announceDns: Boolean = false) {
        val configText = WgConfig.render(
            info,
            WgKeys.privateKeyBase64(ctx),
            dns = if (announceDns) WgConfig.RELAY_ADDRESS else null
        )
        ctx.startForegroundService(
            EmufiiWgService.startIntent(ctx, code, configText, info.address)
        )
    }

    fun stop(ctx: Context) {
        ctx.startService(EmufiiWgService.stopIntent(ctx))
    }

    /** What the coordinator is asked to assign an address to. */
    fun publicKey(ctx: Context): String = WgKeys.publicKeyBase64(ctx)
}
