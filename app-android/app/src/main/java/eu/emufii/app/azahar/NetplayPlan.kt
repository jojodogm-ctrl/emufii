package eu.emufii.app.azahar

import eu.emufii.app.netplay.NetplayUi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class NetplayPlan(
    val role: Role,
    val ip: String,
    val port: Int = NetplayUi.DEFAULT_PORT,
    val roomName: String? = null,
    val username: String? = null,
    val usernameOverDefaultOnly: Boolean = false,
    /** Eden makes this mandatory when hosting, its own dropdown; Azahar has no equivalent. */
    val preferredGame: String? = null,
    val password: String? = null
) {
    enum class Role { Host, Guest }
}

sealed class NetplayProgress {
    data object Idle : NetplayProgress()
    data object OpeningMenu : NetplayProgress()
    data object ChoosingMode : NetplayProgress()
    data object FillingForm : NetplayProgress()
    data object Confirming : NetplayProgress()
    data object Done : NetplayProgress()

    /** [reason] is user-facing: the fallback is always "do it by hand", so it says what to type. */
    data class Failed(val reason: String) : NetplayProgress()
}

/** Global single slot: the accessibility service is system-instantiated, so there is no constructor to pass it. */
object NetplayAutomation {

    private var armedAtMs = 0L

    private val _plan = MutableStateFlow<NetplayPlan?>(null)
    val plan: StateFlow<NetplayPlan?> = _plan.asStateFlow()

    private val _progress = MutableStateFlow<NetplayProgress>(NetplayProgress.Idle)
    val progress: StateFlow<NetplayProgress> = _progress.asStateFlow()

    fun arm(plan: NetplayPlan, store: PlanStore? = null) {
        _plan.value = plan
        _progress.value = NetplayProgress.OpeningMenu
        armedAtMs = System.currentTimeMillis()
        store?.save(plan)
    }

    fun clear(store: PlanStore? = null) {
        _plan.value = null
        _progress.value = NetplayProgress.Idle
        armedAtMs = 0L
        store?.clear()
    }

    /** The system restarts the service after killing us; without this it forgets the plan. */
    fun restore(store: PlanStore) {
        if (_plan.value != null) return
        store.load()?.let {
            _plan.value = it
            _progress.value = NetplayProgress.OpeningMenu
            armedAtMs = System.currentTimeMillis()
        }
    }

    /** Armed but never heard from: usually a bound but mute service, as left by `install -r`. */
    fun neverStarted(now: Long = System.currentTimeMillis()): Boolean =
        _plan.value != null &&
            _progress.value == NetplayProgress.OpeningMenu &&
            armedAtMs > 0L &&
            now - armedAtMs > SILENCE_MS

    internal fun report(progress: NetplayProgress) {
        _progress.value = progress
        if (progress is NetplayProgress.Done || progress is NetplayProgress.Failed) {
            _plan.value = null
            armedAtMs = 0L
        }
    }

    private const val SILENCE_MS = 8_000L
}
