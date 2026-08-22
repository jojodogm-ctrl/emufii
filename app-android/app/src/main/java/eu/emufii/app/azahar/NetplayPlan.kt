package eu.emufii.app.azahar

import eu.emufii.app.netplay.NetplayUi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What Emufii wants the emulator's netplay dialog to end up configured as. */
data class NetplayPlan(
    val role: Role,
    val ip: String,
    val port: Int = NetplayUi.DEFAULT_PORT,
    val roomName: String? = null,
    /**
     * The nickname to write into the form, or null to leave it alone.
     *
     * Filled for Eden, never for Azahar: two players with the same nickname
     * cannot share an Eden room, and Eden ships the same default nickname to
     * everybody. Azahar keeps its own, see `NetplayNames`.
     */
    val username: String? = null,
    /**
     * Which game the room is for. Eden makes this mandatory when hosting,
     * its own dropdown, filled from the library, and Azahar has no equivalent.
     */
    val preferredGame: String? = null,
    /**
     * The room's password, or null when there is none.
     *
     * Filled for the rooms the VPS holds: they listen on a public port, so with
     * no password a stranger would walk into the game. It is the session code,
     * the secret the players already share, and which the app knows on both sides
     * without anything extra having to be transmitted.
     */
    val password: String? = null
) {
    enum class Role { Host, Guest }
}

sealed class NetplayProgress {
    /** Nothing requested, the service stays out of the way. */
    data object Idle : NetplayProgress()
    data object OpeningMenu : NetplayProgress()
    data object ChoosingMode : NetplayProgress()
    data object FillingForm : NetplayProgress()
    data object Confirming : NetplayProgress()
    data object Done : NetplayProgress()

    /**
     * The automation could not finish. [reason] is user-facing: the fallback is
     * always "do it by hand", so it has to say what to type.
     */
    data class Failed(val reason: String) : NetplayProgress()
}

/**
 * Hand-off between the Emufii UI and [AzaharNetplayService].
 *
 * An accessibility service is instantiated by the system, not by us, so there
 * is no constructor to pass the plan through, this object is the channel.
 * Deliberately process-global and single-slot: there is only ever one Azahar in
 * the foreground, so a second plan means the user restarted the flow and the
 * previous one is stale.
 */
object NetplayAutomation {

    private val _plan = MutableStateFlow<NetplayPlan?>(null)
    val plan: StateFlow<NetplayPlan?> = _plan.asStateFlow()

    private val _progress = MutableStateFlow<NetplayProgress>(NetplayProgress.Idle)
    val progress: StateFlow<NetplayProgress> = _progress.asStateFlow()

    /**
     * Arm the automation. Call right before launching the ROM.
     *
     * [store] is what lets the plan survive Emufii being killed while the
     * emulator eats the device's memory, see [PlanStore]. It is optional only
     * so that a caller with no context can still arm in-memory.
     */
    fun arm(plan: NetplayPlan, store: PlanStore? = null) {
        _plan.value = plan
        _progress.value = NetplayProgress.OpeningMenu
        store?.save(plan)
    }

    /** Disarm, user cancelled, left the session, or we're done. */
    fun clear(store: PlanStore? = null) {
        _plan.value = null
        _progress.value = NetplayProgress.Idle
        store?.clear()
    }

    /**
     * Brings back a plan armed before the process died, if it is still fresh.
     *
     * Called by the accessibility service when it starts: the system restarts
     * the service after killing us, and without this it would come back with
     * nothing to do and no way to know it had ever been asked.
     */
    fun restore(store: PlanStore) {
        if (_plan.value != null) return
        store.load()?.let {
            _plan.value = it
            _progress.value = NetplayProgress.OpeningMenu
        }
    }

    internal fun report(progress: NetplayProgress) {
        _progress.value = progress
        if (progress is NetplayProgress.Done || progress is NetplayProgress.Failed) {
            _plan.value = null
        }
    }
}
