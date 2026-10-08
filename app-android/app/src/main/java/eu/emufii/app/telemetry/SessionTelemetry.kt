package eu.emufii.app.telemetry

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import eu.emufii.app.network.CoordinatorClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

// What happens after a launch never reached the server: a session that went quiet
// could be a race in progress or two players who never met.
object SessionTelemetry {

    data class Active(val code: String, val console: String, val role: String)

    @Volatile
    var active: Active? = null

    // An exported receiver can be spammed: a handful per session is all a session tells.
    private const val MAX_PER_SESSION = 25
    private val sent = ConcurrentHashMap<String, AtomicInteger>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun report(event: String, detail: String? = null, emu: String? = null) {
        val a = active ?: return
        val count = sent.getOrPut(a.code) { AtomicInteger() }
        if (count.incrementAndGet() > MAX_PER_SESSION) return
        scope.launch {
            runCatching { CoordinatorClient().reportEvent(event, a.console, a.code, a.role, detail, emu) }
        }
    }
}

// The Edition tells its netplay events by an explicit broadcast; values from NetplayAndroid.h.
class NetplayEventReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val name = when (intent.getIntExtra("event", 0)) {
            1 -> "np_started"
            2 -> "np_rejected"
            3 -> "np_never_got_in"
            4 -> "np_nobody_came"
            5 -> "np_turned_away_game"
            6 -> "np_turned_away_full"
            7 -> "np_player_left"
            8 -> "np_exchange_failed"
            9 -> "np_desync"
            10 -> "np_slow_device"
            else -> return
        }
        SessionTelemetry.report(name, intent.getStringExtra("detail")?.take(64), intent.getStringExtra("emu")?.take(48))
    }
}
