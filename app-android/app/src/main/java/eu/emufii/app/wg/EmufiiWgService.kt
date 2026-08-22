package eu.emufii.app.wg

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.wireguard.android.backend.Backend
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config
import eu.emufii.app.MainActivity
import eu.emufii.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.ByteArrayInputStream

/**
 * The session tunnel, carried by a foreground service.
 *
 * ## Why this class exists at all
 *
 * `GoBackend` already ships a `VpnService`, so on the face of it Emufii needs none.
 * But it starts it like this, read in the library's source before writing a line:
 *
 * ```java
 * context.startService(new Intent(context, VpnService.class));
 * ```
 *
 * `startService`, and `startForeground` is never called anywhere in the library.
 * The tunnel would therefore live in a background service, which Android is free
 * to kill the moment Emufii leaves the foreground, which is precisely when the
 * player switches to the emulator to actually play. This app has already paid that
 * exact bill once, on `DolphinLanService`: without the foreground, the LAN segment
 * dropped.
 *
 * `GoBackend` is `final`, so it cannot be subclassed. `GoBackend.VpnService` is
 * not, and the `onCreate()` it inherits completes a static future that
 * `GoBackend` consults before starting anything of its own. So: subclass it,
 * declare the subclass in the manifest, and start it ourselves in the foreground.
 * `GoBackend` then finds the future already completed, skips its own
 * `startService`, and works through our instance.
 *
 * That hangs on an internal detail of the library, which is why the service owns
 * the tunnel's lifecycle rather than the manager: doing the `setState` from inside
 * `onStartCommand` makes the ordering, `onCreate`, then `startForeground`, then
 * `setState`, a property of the code rather than a hope about timing.
 */
class EmufiiWgService : GoBackend.VpnService() {

    companion object {
        private const val TAG = "EmufiiWgService"
        private const val NOTIFICATION_ID = 5919813
        private const val CHANNEL_ID = "emufii_wg_vpn"
        private const val ACTION_START = "eu.emufii.app.wg.START"
        private const val ACTION_STOP = "eu.emufii.app.wg.STOP"
        private const val EXTRA_CODE = "code"
        private const val EXTRA_CONFIG = "config"
        private const val EXTRA_IP = "ip"

        /** Must match WireGuard's own name rules: `[a-zA-Z0-9_=+.-]{1,15}`. */
        private const val TUNNEL_NAME = "emufii"

        private val _state = MutableStateFlow(WgState.Idle as WgState)
        val state: StateFlow<WgState> = _state.asStateFlow()

        fun startIntent(ctx: Context, code: String, configText: String, ip: String): Intent =
            Intent(ctx, EmufiiWgService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_CODE, code)
                .putExtra(EXTRA_CONFIG, configText)
                .putExtra(EXTRA_IP, ip)

        fun stopIntent(ctx: Context): Intent =
            Intent(ctx, EmufiiWgService::class.java).setAction(ACTION_STOP)
    }

    private var backend: Backend? = null
    private var tunnel: SessionTunnel? = null
    private var scope: CoroutineScope? = null

    /**
     * Keeps the Wi-Fi radio from falling asleep for the duration of the session.
     *
     * Measured between two remote Thors, through the relay: 25 % loss at one ping
     * per second, 0 % at three pings per second, and jitter from 46 to 369 ms.
     * That is the signature of Wi-Fi power saving; sparse traffic lets the radio
     * doze, and rare packets pay the wait or get lost.
     *
     * This is no comfort detail here: the Switch's LDN, which Eden's upstream
     * describes as "extremely sensitive to latency and loss", does its handshake
     * with precisely those rare packets. A game that connects then gives up after
     * seven seconds, twice in a row, is exactly what a handshake losing one packet
     * in four produces.
     *
     * `WIFI_MODE_FULL_LOW_LATENCY` does two things more than the old `HIGH_PERF`:
     * it turns power saving off and asks the driver to favour latency over
     * throughput. It only acts while the screen is on and the app is in the
     * foreground, which during a game describes the emulator and not us; the lock
     * is therefore held, and the system applies it when it can.
     */
    private var wifiLock: WifiManager.WifiLock? = null

    /**
     * The library reports handshake progress through this.
     *
     * `Tunnel.State` only distinguishes up from down, so `Online` here means the
     * interface exists, not that another player has joined, and not that a
     * handshake has landed. The app confirms real reachability by pinging the
     * relay, which is what its address is returned for.
     */
    private inner class SessionTunnel(val code: String, val ip: String) : Tunnel {
        override fun getName(): String = TUNNEL_NAME

        override fun onStateChange(newState: Tunnel.State) {
            Log.d(TAG, "tunnel → $newState")
            _state.value = when (newState) {
                Tunnel.State.UP -> WgState.Online(code, ip)
                Tunnel.State.DOWN -> WgState.Offline(code)
                else -> _state.value
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand action=${intent?.action}")

        if (intent?.action == ACTION_STOP) {
            stopTunnel()
            return START_NOT_STICKY
        }

        val code = intent?.getStringExtra(EXTRA_CODE)
        val configText = intent?.getStringExtra(EXTRA_CONFIG)
        val ip = intent?.getStringExtra(EXTRA_IP)
        if (code == null || configText == null || ip == null) {
            // START_STICKY had the system restart us with a null intent; there is
            // no session to rejoin, so go away rather than sit on the VPN slot.
            Log.w(TAG, "démarrage sans configuration — arrêt")
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, buildNotification(getString(R.string.svc_wg_connecting, code)))
        holdWifiAwake()
        _state.value = WgState.Starting(code)

        val s = scope ?: CoroutineScope(Dispatchers.IO + SupervisorJob()).also { scope = it }
        s.launch {
            try {
                val config = Config.parse(ByteArrayInputStream(configText.toByteArray()))
                val b = backend ?: GoBackend(applicationContext).also { backend = it }
                val t = SessionTunnel(code, ip).also { tunnel = it }
                // Blocking, and deliberately off the main thread: the library
                // re-resolves the endpoint with one-second waits between attempts,
                // so this can sit for several seconds on a cold network.
                b.setState(t, Tunnel.State.UP, config)
                notify(getString(R.string.svc_wg_online, ip))
            } catch (e: Exception) {
                Log.e(TAG, "montage du tunnel: ${e.message}", e)
                _state.value = WgState.Error(e.message ?: "échec du tunnel")
                stopSelf()
            }
        }

        // Not START_STICKY: a session is brokered by the coordinator and its peers
        // expire, so a tunnel resurrected blindly after a process death would
        // point at a game that no longer exists.
        return START_NOT_STICKY
    }

    /** Taken when the tunnel comes up, released when it falls. Never twice. */
    private fun holdWifiAwake() {
        if (wifiLock?.isHeld == true) return
        val wifi = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            ?: return
        // Low-latency mode has existed since Android 10 and minSdk is 33: there
        // is no fallback to write.
        val lock = runCatching {
            wifi.createWifiLock(WifiManager.WIFI_MODE_FULL_LOW_LATENCY, TAG)
        }.getOrNull() ?: return
        // Without this, a lock taken twice would need releasing twice, and a
        // tunnel brought back up after a fall would leave the radio locked for
        // good.
        lock.setReferenceCounted(false)
        runCatching { lock.acquire() }
            .onSuccess { Log.d(TAG, "verrou Wi-Fi basse latence pris") }
            .onFailure { Log.w(TAG, "verrou Wi-Fi refusé", it) }
        wifiLock = lock
    }

    private fun releaseWifi() {
        wifiLock?.let { lock ->
            runCatching { if (lock.isHeld) lock.release() }
                .onFailure { Log.w(TAG, "libération du verrou Wi-Fi", it) }
        }
        wifiLock = null
    }

    private fun stopTunnel() {
        _state.value = WgState.Stopping
        val b = backend
        val t = tunnel
        val s = scope
        if (b != null && t != null && s != null) {
            s.launch {
                runCatching { b.setState(t, Tunnel.State.DOWN, null) }
                    .onFailure { Log.w(TAG, "arrêt du tunnel: ${it.message}") }
                stopSelf()
            }
        } else {
            stopSelf()
        }
    }

    /**
     * The user swiped Emufii out of recents: bring the session tunnel down.
     *
     * Same reasoning as the WFC service. A foreground service survives its task
     * being dismissed by design, so without this the tunnel, and the VPN key in
     * the status bar, outlived the app with nothing left on screen to stop it.
     *
     * [stopTunnel] rather than a bare [stopSelf], so the peer is torn down in
     * order; it calls `stopSelf` itself once the backend has settled.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.d(TAG, "Emufii swiped away, taking the session tunnel down")
        stopTunnel()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy")
        releaseWifi()
        scope?.cancel()
        scope = null
        tunnel = null
        backend = null
        // The library's own onDestroy turns the tunnel off and resets the static
        // future that lets GoBackend find this service. Skipping it would leave a
        // future pointing at a dead instance, and the next tunnel would never come
        // up.
        super.onDestroy()
        _state.value = WgState.Idle
    }

    private fun notify(text: String) {
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun buildNotification(text: String): Notification {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.svc_wg_channel),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.svc_wg_channel_desc)
            }
        )
        val pi = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle(getString(R.string.svc_wg_title))
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(pi)
            .build()
    }
}
