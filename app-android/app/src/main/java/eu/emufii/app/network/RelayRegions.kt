package eu.emufii.app.network

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/** Picks the relay nearest the host (or the player's manual pick); null leaves the coordinator's default. */
object RelayRegions {

    data class Option(val region: RelayRegion, val rttMs: Double?)

    /** A relay must beat the default by this much to take a session away from it: ping jitters. */
    private const val MARGIN_MS = 15.0

    /** Regions do not move, and a measurement costs a second on the launch card. */
    private const val CACHE_MS = 10 * 60 * 1000L

    private const val PREFS = "relay_region"
    private const val KEY_MANUAL = "manual"

    private var prefs: SharedPreferences? = null
    private val lock = Mutex()
    private var measuredAt = 0L

    private val _options = MutableStateFlow<List<Option>>(emptyList())
    val options: StateFlow<List<Option>> = _options.asStateFlow()

    private val _manual = MutableStateFlow<String?>(null)
    val manual: StateFlow<String?> = _manual.asStateFlow()

    private val _auto = MutableStateFlow<String?>(null)
    val auto: StateFlow<String?> = _auto.asStateFlow()

    fun init(context: Context) {
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        _manual.value = p.getString(KEY_MANUAL, null)
    }

    /** Null goes back to automatic. Kept across launches: a player who moved stays moved. */
    fun setManual(id: String?) {
        _manual.value = id
        prefs?.edit()?.apply { if (id == null) remove(KEY_MANUAL) else putString(KEY_MANUAL, id) }?.apply()
    }

    fun resolved(options: List<Option>, manual: String?, auto: String?): String? =
        manual?.takeIf { m -> options.any { it.region.id == m } } ?: auto

    suspend fun refresh(client: CoordinatorClient, force: Boolean = false) = lock.withLock {
        if (!force && System.currentTimeMillis() - measuredAt < CACHE_MS) return@withLock
        val relays = client.listRelays().getOrNull() ?: return@withLock
        // A single relay is shown, but not pinged: there is nothing to choose.
        val measured = if (relays.size < 2) relays.map { Option(it, null) } else coroutineScope {
            relays.map { r -> async(Dispatchers.IO) { Option(r, ping(r.pingHost)) } }.awaitAll()
        }
        _options.value = measured
        _auto.value = if (relays.size < 2) null else autoPick(measured.map { it.region.id to it.rttMs })
        measuredAt = System.currentTimeMillis()
    }

    suspend fun pick(client: CoordinatorClient): String? {
        refresh(client)
        return resolved(_options.value, _manual.value, _auto.value)
    }

    internal fun autoPick(rtts: List<Pair<String, Double?>>): String? {
        val measured = rtts.filter { it.second != null }
        if (measured.isEmpty()) return null
        val default = rtts.first()
        val best = measured.minBy { it.second!! }
        val defaultRtt = default.second ?: return best.first
        return if (best.second!! + MARGIN_MS < defaultRtt) best.first else default.first
    }

    /** Mean round trip in ms from Android's `ping`, which needs no root for ICMP echo. */
    private suspend fun ping(host: String): Double? = withContext(Dispatchers.IO) {
        runCatching {
            val p = ProcessBuilder("ping", "-c", "3", "-i", "0.2", "-W", "1", host)
                .redirectErrorStream(true)
                .start()
            if (!p.waitFor(3, TimeUnit.SECONDS)) {
                p.destroy()
                return@runCatching null
            }
            parseAvg(p.inputStream.bufferedReader().readText())
        }.getOrNull()
    }

    /** `rtt min/avg/max/mdev = 11.2/12.8/14.1/1.2 ms` → 12.8. */
    internal fun parseAvg(output: String): Double? =
        Regex("""= [\d.]+/([\d.]+)/""").find(output)?.groupValues?.get(1)?.toDoubleOrNull()
}
