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

class WfcDnsService : VpnService() {

    companion object {
        private const val TAG = "WfcDnsService"
        private const val NOTIFICATION_ID = 5919813
        private const val CHANNEL_ID = "emufii_wfc_dns"
        private const val ACTION_START = "eu.emufii.app.wfc.START"
        private const val ACTION_STOP = "eu.emufii.app.wfc.STOP"

        private const val UPSTREAM_TIMEOUT_MS = 4000

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
                // Without this the query is sent back into our own tunnel.
                socket.close()
                throw IllegalStateException("protect() failed on the upstream socket")
            }
            upstreamSocket = socket

            val sentinel = InetAddress.getByName(KaeruWfc.SENTINEL_DNS)
            val kaeru = InetSocketAddress(InetAddress.getByName(KaeruWfc.DNS_SERVER), KaeruWfc.DNS_PORT)

            val established = Builder()
                .addAddress(KaeruWfc.TUN_ADDRESS, 32)
                // Only the resolver is routed: this tunnel moves DNS, not traffic.
                .addRoute(KaeruWfc.SENTINEL_DNS, 32)
                .addDnsServer(KaeruWfc.SENTINEL_DNS)
                .addAllowedApplication(melonPackage)
                .setMtu(1500)
                .setSession("Emufii: Kaeru WFC")
                .also { it.setMetered(false) }
                .establish()
                ?: throw IllegalStateException("establish() returned null (VPN permission missing?)")

            tunnel = established
            relay = DnsRelay(sentinel.address) { query -> exchangeWithKaeru(socket, kaeru, query) }

            relayThread = Thread({ relayLoop(established) }, "Emufii WFC DNS").apply { start() }
            _state.value = WfcState.Active(melonPackage)
            Log.d(TAG, "WFC DNS tunnel up, scoped to $melonPackage")
        } catch (e: Exception) {
            Log.e(TAG, "start: ${e.message}", e)
            _state.value = WfcState.Error(e.message ?: "could not start")
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
                val count = active.queriesRelayed
                if (count == 1L || count % 25L == 0L) {
                    Log.d(TAG, "relayed $count queries to ${KaeruWfc.DNS_SERVER}")
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "relay loop ended: ${e.message}")
        }
        Log.d(TAG, "relay loop stopped")
    }

    private fun publishReachability(active: DnsRelay) {
        val scoped = when (val s = _state.value) {
            is WfcState.Active -> s.scopedTo
            is WfcState.Unreachable -> s.scopedTo
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
        Log.d(TAG, if (down) "Kaeru silent over ${active.consecutiveUpstreamFailures} queries" else "Kaeru answering again")
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

    /** stopSelf also clears the sticky restart. */
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

    data class Unreachable(val scopedTo: String) : WfcState
    data object Stopping : WfcState
    data class Error(val message: String) : WfcState
}
