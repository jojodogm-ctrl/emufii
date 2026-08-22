package eu.emufii.app.wfc

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import eu.emufii.app.R
import eu.emufii.app.MainActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress

/**
 * A tunnel that exists only to answer DNS, so DS online play lands on Kaeru.
 *
 * Deliberately separate from [eu.emufii.app.wg.EmufiiWgService] rather than bolted
 * onto it. The WFC route is architecturally disjoint from the session one, no
 * network, no session code, no hub, and Android runs one VpnService at a time
 * anyway, so the two modes are mutually exclusive whether we like it or not. The
 * separation also keeps the tunnel that carries live sessions untouched.
 *
 * It is scoped to melonDS alone via [VpnService.Builder.addAllowedApplication],
 * so nothing else on the device has its name resolution moved.
 */
class WfcDnsService : VpnService() {

    companion object {
        private const val TAG = "WfcDnsService"
        private const val NOTIFICATION_ID = 5919813
        private const val CHANNEL_ID = "emufii_wfc_dns"
        private const val ACTION_START = "eu.emufii.app.wfc.START"
        private const val ACTION_STOP = "eu.emufii.app.wfc.STOP"

        /** Upstream is one UDP round trip; a game waiting on it should not hang. */
        private const val UPSTREAM_TIMEOUT_MS = 4000

        /**
         * A single timeout is weather; three in a row is a server that is gone.
         * At the timeout above that is roughly twelve seconds of silence, which
         * is shorter than the console's own patience, so Emufii can name the
         * cause before the game shows its own error code.
         */
        private const val UNREACHABLE_AFTER_FAILURES = 3

        private val _state = MutableStateFlow(WfcState.Idle as WfcState)
        val state: StateFlow<WfcState> = _state.asStateFlow()

        fun startIntent(ctx: Context): Intent =
            Intent(ctx, WfcDnsService::class.java).setAction(ACTION_START)

        fun stopIntent(ctx: Context): Intent =
            Intent(ctx, WfcDnsService::class.java).setAction(ACTION_STOP)
    }

    private var tunnel: ParcelFileDescriptor? = null
    private var upstreamSocket: DatagramSocket? = null
    private var relayThread: Thread? = null
    private var relay: DnsRelay? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopTunnel()
            stopSelf(startId)
            return START_NOT_STICKY
        }

        if (tunnel != null) {
            Log.d(TAG, "Already running")
            return START_STICKY
        }

        val melonPackage = MelonDs(this).installedPackage()
        if (melonPackage == null) {
            _state.value = WfcState.Error(getString(R.string.wfc_not_installed))
            stopSelf(startId)
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, buildNotification(getString(R.string.svc_wfc_text)))

        try {
            val socket = DatagramSocket().apply { soTimeout = UPSTREAM_TIMEOUT_MS }
            if (!protect(socket)) {
                // Without this the query would be sent back into our own tunnel.
                socket.close()
                throw IllegalStateException("protect() a échoué sur la socket amont")
            }
            upstreamSocket = socket

            val sentinel = InetAddress.getByName(KaeruWfc.SENTINEL_DNS)
            val kaeru = InetSocketAddress(InetAddress.getByName(KaeruWfc.DNS_SERVER), KaeruWfc.DNS_PORT)

            val established = Builder()
                .addAddress(KaeruWfc.TUN_ADDRESS, 32)
                // One host route, for the resolver we advertise. Everything else
                // keeps using the real network: this tunnel moves DNS, not traffic.
                .addRoute(KaeruWfc.SENTINEL_DNS, 32)
                .addDnsServer(KaeruWfc.SENTINEL_DNS)
                .addAllowedApplication(melonPackage)
                .setMtu(1500)
                .setSession("Emufii — Kaeru WFC")
                .also { it.setMetered(false) }
                .establish()
                ?: throw IllegalStateException("establish() a renvoyé null (permission VPN manquante ?)")

            tunnel = established
            relay = DnsRelay(sentinel.address) { query -> exchangeWithKaeru(socket, kaeru, query) }

            relayThread = Thread({ relayLoop(established) }, "Emufii WFC DNS").apply { start() }
            _state.value = WfcState.Active(melonPackage)
            Log.d(TAG, "WFC DNS tunnel up, scoped to $melonPackage")
        } catch (e: Exception) {
            Log.e(TAG, "start: ${e.message}", e)
            _state.value = WfcState.Error(e.message ?: "démarrage impossible")
            stopTunnel()
            stopSelf(startId)
            return START_NOT_STICKY
        }

        return START_STICKY
    }

    private fun exchangeWithKaeru(
        socket: DatagramSocket,
        kaeru: InetSocketAddress,
        query: ByteArray
    ): ByteArray? = try {
        socket.send(DatagramPacket(query, query.size, kaeru))
        val buffer = ByteArray(KaeruWfc.MAX_DNS_MESSAGE)
        val response = DatagramPacket(buffer, buffer.size)
        socket.receive(response)
        buffer.copyOfRange(0, response.length)
    } catch (e: Exception) {
        Log.w(TAG, "upstream: ${e.message}")
        null
    }

    private fun relayLoop(tun: ParcelFileDescriptor) {
        val input = FileInputStream(tun.fileDescriptor)
        val output = FileOutputStream(tun.fileDescriptor)
        val buffer = ByteArray(32 * 1024)
        Log.d(TAG, "relay loop started")
        try {
            while (!Thread.interrupted()) {
                val read = input.read(buffer)
                if (read <= 0) continue
                val active = relay ?: continue
                val reply = active.handle(buffer, read)
                publishReachability(active)
                if (reply == null) continue
                output.write(reply)
                // Loud on the first one, then sparse: enough to tell "the console
                // is going through Kaeru" from "nothing is reaching the relay",
                // without a log line per lookup.
                val count = active.queriesRelayed
                if (count == 1L || count % 25L == 0L) {
                    Log.d(TAG, "relayed $count queries to ${KaeruWfc.DNS_SERVER}")
                }
            }
        } catch (e: Exception) {
            // Closing the descriptor to stop the service lands here; that is the
            // normal way out, not a failure worth surfacing.
            Log.d(TAG, "relay loop ended: ${e.message}")
        }
        Log.d(TAG, "relay loop stopped")
    }

    /**
     * Turns a run of unanswered lookups into something the player can read.
     *
     * Called after every packet the relay looked at, including the ones it
     * dropped, so the state follows the run rather than the last write. Only the
     * transitions touch the flow and the notification, a lookup that changes
     * nothing must not repost a notification on every DNS query of a race.
     *
     * The notification matters more than the screen here: while a game is on,
     * Emufii is in the background and the shade is the only surface the player
     * still sees.
     */
    private fun publishReachability(active: DnsRelay) {
        val scoped = when (val s = _state.value) {
            is WfcState.Active -> s.scopedTo
            is WfcState.Unreachable -> s.scopedTo
            // Stopping, Idle or Error: the tunnel is not in a steady state and
            // reachability is not the interesting fact about it.
            else -> return
        }
        val down = active.consecutiveUpstreamFailures >= UNREACHABLE_AFTER_FAILURES
        val wasDown = _state.value is WfcState.Unreachable
        if (down == wasDown) return

        _state.value = if (down) WfcState.Unreachable(scoped) else WfcState.Active(scoped)
        val text = getString(if (down) R.string.svc_wfc_text_unreachable else R.string.svc_wfc_text)
        runCatching {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(NOTIFICATION_ID, buildNotification(text))
        }
        Log.d(TAG, if (down) "Kaeru silencieux sur ${active.consecutiveUpstreamFailures} requêtes" else "Kaeru répond de nouveau")
    }

    private fun stopTunnel() {
        _state.value = WfcState.Stopping
        relayThread?.let { if (it.isAlive) it.interrupt() }
        try { tunnel?.close() } catch (_: Exception) {}
        tunnel = null
        relayThread?.let { runCatching { it.join(1500) } }
        relayThread = null
        try { upstreamSocket?.close() } catch (_: Exception) {}
        upstreamSocket = null
        relay = null
        _state.value = WfcState.Idle
        try { stopForeground(STOP_FOREGROUND_REMOVE) } catch (_: Exception) {}
    }

    /**
     * The user swiped Emufii out of the recents list: take the tunnel down.
     *
     * Without this the tunnel outlived the app indefinitely. `START_STICKY` made
     * it worse than merely forgotten, kill the process and Android brings the
     * service back, tunnel and all, with no Emufii on screen to stop it. The key
     * icon stays in the status bar and the only way out is Android's own VPN
     * settings.
     *
     * [onDestroy] was not enough on its own: swiping the task away does not
     * destroy a started foreground service, which is the whole point of one.
     * This callback is the only signal Android gives for "the user is done with
     * this app", so it is where the decision belongs.
     *
     * Deliberately unconditional. Swiping the app away while melonDS is still
     * mid-session will cut WFC name resolution under it, but a tunnel nothing
     * can reach is the worse failure, and the emulator keeps its own task in
     * recents, so the gesture is aimed at Emufii specifically.
     *
     * `stopSelf` matters as much as [stopTunnel]: it clears the sticky restart,
     * so the service stays down instead of being resurrected.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.d(TAG, "Emufii swiped away, taking the WFC tunnel down")
        stopTunnel()
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        stopTunnel()
        super.onDestroy()
    }

    override fun onRevoke() {
        stopTunnel()
        super.onRevoke()
    }

    private fun buildNotification(text: String): Notification {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.svc_wfc_channel),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.svc_wfc_channel_desc)
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
            .setContentTitle(getString(R.string.svc_wfc_channel))
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(pi)
            .build()
    }
}

sealed interface WfcState {
    data object Idle : WfcState
    data class Active(val scopedTo: String) : WfcState

    /**
     * The tunnel is up and the console is asking, but Kaeru has stopped
     * answering.
     *
     * A separate state rather than [Error], because nothing on this side is
     * broken: the redirection still works and will start serving again the
     * moment the server comes back. What it changes is what the player should
     * conclude, the game's own "could not connect" is about to appear, and it
     * is not their network.
     */
    data class Unreachable(val scopedTo: String) : WfcState
    data object Stopping : WfcState
    data class Error(val message: String) : WfcState
}
