package eu.emufii.app.secondscreen

import eu.emufii.app.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/** UNKNOWN is drawn grey: a failed probe may be the player's connection, not ours. */
enum class VpsState { UNKNOWN, ONLINE, OFFLINE }

object VpsStatus {

    private val _state = MutableStateFlow(VpsState.UNKNOWN)
    val state: StateFlow<VpsState> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var watchers = 0
    private var job: Job? = null

    suspend fun keepPolling() {
        synchronized(this) {
            if (watchers++ == 0) job = scope.launch { poll() }
        }
        try {
            awaitCancellation()
        } finally {
            synchronized(this) {
                if (--watchers == 0) {
                    job?.cancel()
                    job = null
                }
            }
        }
    }

    suspend fun poll(baseUrl: String = BuildConfig.COORDINATOR_BASE_URL) {
        while (true) {
            _state.value = probe(baseUrl)
            delay(if (_state.value == VpsState.ONLINE) ONLINE_PERIOD_MS else RETRY_PERIOD_MS)
        }
    }

    private suspend fun probe(baseUrl: String): VpsState = withContext(Dispatchers.IO) {
        runCatching {
            val conn = (URL("$baseUrl/health").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 3000
                readTimeout = 3000
            }
            try {
                conn.responseCode
            } finally {
                conn.disconnect()
            }
        }.fold(
            onSuccess = { if (it in 200..599) VpsState.ONLINE else VpsState.UNKNOWN },
            onFailure = { VpsState.OFFLINE }
        )
    }

    private const val ONLINE_PERIOD_MS = 60_000L
    private const val RETRY_PERIOD_MS = 15_000L
}
