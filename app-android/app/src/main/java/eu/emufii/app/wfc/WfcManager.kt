package eu.emufii.app.wfc

import android.content.Context
import android.content.Intent
import android.net.VpnService
import kotlinx.coroutines.flow.StateFlow

/** Same shape as [eu.emufii.app.wg.EmufiiWgManager], for the other tunnel. */
object WfcManager {

    val state: StateFlow<WfcState> get() = WfcDnsService.state

    /** Returns null if VPN permission is already granted, else the consent Intent. */
    fun prepare(ctx: Context): Intent? = VpnService.prepare(ctx)

    fun start(ctx: Context) {
        ctx.startForegroundService(WfcDnsService.startIntent(ctx))
    }

    fun stop(ctx: Context) {
        ctx.startService(WfcDnsService.stopIntent(ctx))
    }
}
