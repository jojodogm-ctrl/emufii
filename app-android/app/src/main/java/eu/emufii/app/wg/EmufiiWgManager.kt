package eu.emufii.app.wg

import android.content.Context
import android.content.Intent
import android.net.VpnService
import kotlinx.coroutines.flow.StateFlow

object EmufiiWgManager {

    val state: StateFlow<WgState> get() = EmufiiWgService.state

    fun prepare(ctx: Context): Intent? = VpnService.prepare(ctx)

    /** Foreground service: GoBackend would otherwise start its own in the background. */
    fun start(ctx: Context, code: String, info: WgTunnelInfo, announceDns: Boolean = false): Boolean {
        val configText = WgConfig.render(
            info,
            WgKeys.privateKeyBase64(ctx),
            dns = if (announceDns) WgConfig.RELAY_ADDRESS else null
        )
        return runCatching {
            ctx.startForegroundService(
                EmufiiWgService.startIntent(ctx, code, configText, info.address)
            )
        }.isSuccess
    }

    fun stop(ctx: Context) {
        ctx.startService(EmufiiWgService.stopIntent(ctx))
    }

    fun publicKey(ctx: Context): String = WgKeys.publicKeyBase64(ctx)
}
