package eu.emufii.app.ui.screens.session

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import eu.emufii.app.R
import eu.emufii.app.azahar.AzaharLauncher
import eu.emufii.app.azahar.LaunchResult
import eu.emufii.app.azahar.NetplayAutomation
import eu.emufii.app.azahar.NetplayProgress
import eu.emufii.app.azahar.PlanStore
import eu.emufii.app.dolphin.DolphinLauncher
import eu.emufii.app.eden.EdenLauncher
import eu.emufii.app.library.Backend
import eu.emufii.app.library.Rom
import eu.emufii.app.library.RomsRepository
import eu.emufii.app.network.CoordinatorClient
import eu.emufii.app.network.CoordinatorError
import eu.emufii.app.network.Member
import eu.emufii.app.profile.Profile
import eu.emufii.app.ps2.Ps2GameSettings
import eu.emufii.app.ps2.Ps2Launcher
import eu.emufii.app.ps2.Ps2NetworkProfile
import eu.emufii.app.ps2.Ps2ProvisioningPlan
import eu.emufii.app.psp.PpssppConfigStore
import eu.emufii.app.psp.PpssppLauncher
import eu.emufii.app.session.Session
import eu.emufii.app.session.netplayPlan
import eu.emufii.app.ui.copyToClipboard
import eu.emufii.app.util.combineAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds

private const val PRESENCE_MS = 5000L
private const val MAX_PRESENCE_MISSES = 3

/**
 * Flat single-source view for [eu.emufii.app.ui.screens.SessionScreen]. Every mutable field
 * is owned by a [MutableStateFlow] inside [SessionScreenState]; the composable reads this
 * and calls actions on the holder.
 */
internal data class SessionUiState(
    val status: String? = null,
    val members: List<Member> = emptyList(),
    val myHandle: String? = null,
    val netplayPrepared: Boolean = false,
    val netplayDone: Boolean = false,
    val pspOpened: Boolean = false,
    val offline: Boolean = false,
    val hostReady: Boolean = true,
    val automationOn: Boolean = false,
    val launched: Boolean = false,
    val sessionArt: Rom? = null,
    val confirmingLeave: Boolean = false,
    val ps2Automatic: Boolean = false,
    val others: List<Member> = emptyList(),
    val waitingForHost: Boolean = false,
)

/**
 * Plain state-holder for [eu.emufii.app.ui.screens.SessionScreen]. No ViewModel and no DI —
 * MutableStateFlows, an init block that runs the orchestration effects (heartbeat, host-ready
 * publish, netplay progress latch, session artwork, ps2 refinement), and action methods the
 * composable calls.
 *
 * Session state is not process-death-durable, so no Saver: the factory keys the holder on
 * [Session.code] and a fresh holder + scope replace it when the session changes.
 *
 * pourquoi : docs/decisions/session.md § Two proofs that a room exists, and the second is knowingly weaker
 */
@Suppress("LongParameterList")
internal class SessionScreenState(
    private val context: Context,
    private val session: Session,
    private val client: CoordinatorClient,
    private val profile: Profile,
    private val roms: RomsRepository,
    private val azahar: AzaharLauncher,
    private val eden: EdenLauncher,
    private val ppsspp: PpssppLauncher,
    private val scope: CoroutineScope,
    private val onSessionEnded: () -> Unit,
) {

    val pspAutomatic: Boolean = session.rom?.let { rom ->
        PpssppConfigStore(context).canApply(rom.productCode, rom.filename, rom.displayName)
    } == true

    private val hasHostStep = session.backend.hasNetplay
    private val weHostTheRoom = hasHostStep && session.role == Session.Role.HOST

    private val _status = MutableStateFlow<String?>(null)
    private val _members = MutableStateFlow<List<Member>>(emptyList())
    private val _myHandle = MutableStateFlow<String?>(null)
    private val _netplayPrepared = MutableStateFlow(false)
    private val _netplayDone = MutableStateFlow(false)
    private val _pspOpened = MutableStateFlow(false)
    private val _offline = MutableStateFlow(false)
    private val _hostReady = MutableStateFlow(true)
    private val _automationOn = MutableStateFlow(azahar.isNetplayAutomationEnabled())
    private val _launched = MutableStateFlow(false)
    private val _sessionArt = MutableStateFlow<Rom?>(null)
    private val _confirmingLeave = MutableStateFlow(false)
    private val _ps2Automatic = MutableStateFlow(
        session.rom != null && session.backend == Backend.ARMSX2 &&
            Ps2GameSettings.canConfigure(context, session.rom)
    )
    private val _returns = MutableStateFlow(0)

    val uiState: StateFlow<SessionUiState> = combineAll(
        _status,
        _members,
        _myHandle,
        _netplayPrepared,
        _netplayDone,
        _pspOpened,
        _offline,
        _hostReady,
        _automationOn,
        _launched,
        _sessionArt,
        _confirmingLeave,
        _ps2Automatic,
    ) { status, members, myHandle, netplayPrepared, netplayDone,
        pspOpened, offline, hostReady, automationOn, launched,
        sessionArt, confirmingLeave, ps2Automatic ->
        SessionUiState(
            status = status,
            members = members,
            myHandle = myHandle,
            netplayPrepared = netplayPrepared,
            netplayDone = netplayDone,
            pspOpened = pspOpened,
            offline = offline,
            hostReady = hostReady,
            automationOn = automationOn,
            launched = launched,
            sessionArt = sessionArt,
            confirmingLeave = confirmingLeave,
            ps2Automatic = ps2Automatic,
            others = members.filter { it.id != myHandle && it.id != profile.id },
            waitingForHost = hasHostStep &&
                session.role == Session.Role.GUEST && !hostReady,
        )
    }.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = SessionUiState(
            hostReady = _hostReady.value,
            automationOn = _automationOn.value,
            ps2Automatic = _ps2Automatic.value,
            waitingForHost = hasHostStep && session.role == Session.Role.GUEST,
        ),
    )

    init {
        // Latched, not read off the progress flow, which starting the game resets.
        // pourquoi : docs/decisions/session.md § Two proofs that a room exists, and the second is knowingly weaker
        scope.launch {
            NetplayAutomation.progress.collect { p ->
                if (p is NetplayProgress.Done) _netplayDone.value = true
            }
        }

        // Publish our room once it exists; two signals count as proof. `_netplayPrepared` is
        // read from within (not a combine input), mirroring the original LaunchedEffect keys.
        // pourquoi : docs/decisions/session.md § Two proofs that a room exists, and the second is knowingly weaker
        if (weHostTheRoom) {
            scope.launch {
                combine(_netplayDone, _returns) { done, ret -> done to ret }
                    .collect { (done, ret) ->
                        if (done || (_netplayPrepared.value && ret > 0)) {
                            client.setHostReady(session.code, true, session.token)
                        }
                    }
            }
        }

        // Announce on a timer and read back who else is here; one loop for both roles.
        scope.launch {
            var gone = 0
            var mute = 0
            while (true) {
                client.heartbeat(session.code, profile.id, profile.name)
                    .onSuccess { beat -> beat.memberHandle?.let { _myHandle.value = it } }
                client.getSession(session.code)
                    .onSuccess {
                        _members.value = it.members
                        _hostReady.value = it.hostReady
                        gone = 0; mute = 0; _offline.value = false
                    }
                    .onFailure { err ->
                        if (err is CoordinatorError.NotFound) gone++ else mute++
                    }

                // Only a coordinator that *answers* 404 proves the room is gone; a silent one
                // proves only that we cannot reach it.
                // pourquoi : docs/decisions/session.md § Only a 404 proves a room is closed
                if (gone >= MAX_PRESENCE_MISSES && session.role == Session.Role.GUEST) {
                    onSessionEnded()
                    return@launch
                }
                if (mute >= MAX_PRESENCE_MISSES) _offline.value = true
                delay(PRESENCE_MS.milliseconds)
            }
        }

        // The session carries only a ROM reference, no icon and no extracted colour.
        // pourquoi : docs/decisions/session.md § The game is shown in the space the panel left
        scope.launch {
            val uri = session.rom?.uri ?: return@launch
            _sessionArt.value = withContext(Dispatchers.IO) {
                runCatching { roms.cachedOrScan() }
                    .getOrDefault(emptyList())
                    .firstOrNull { it.uri == uri }
            }
        }

        // First frame from the library scan, then the disc itself: an old scan can carry a
        // stale serial.
        scope.launch {
            _ps2Automatic.value = session.rom != null && session.backend == Backend.ARMSX2 &&
                Ps2GameSettings.canConfigureNow(context, session.rom)
        }
    }

    /** One definition for both layouts, which drifted apart once. */
    fun onNetplayStep() {
        // Only an image whose boot ELF cannot be read reaches this branch: it needs the one
        // legacy global assignment per-game files avoid.
        // pourquoi : docs/decisions/session.md § What each backend receives at launch
        if (session.backend == Backend.ARMSX2 && !_ps2Automatic.value) {
            val receipt = Ps2NetworkProfile.receipt(context)
            if (receipt != null && !receipt.assigned) {
                if (!_automationOn.value) {
                    _status.value = context.getString(R.string.session_ps2_fallback_accessibility)
                    return
                }
                _status.value = when (val result = Ps2Launcher(context).openForProvisioning(
                    Ps2ProvisioningPlan(
                        receipt.cardName,
                        receipt.cardSha256,
                        receipt.sourceCardForSlot2,
                    )
                )) {
                    LaunchResult.Success -> context.getString(R.string.session_ps2_fallback_assigning)
                    LaunchResult.NotInstalled -> context.getString(
                        R.string.err_not_installed,
                        "ARMSX2"
                    )

                    is LaunchResult.Error -> context.getString(R.string.err_generic, result.message)
                    is LaunchResult.NoNetplayUi -> context.getString(
                        R.string.err_not_installed,
                        "ARMSX2"
                    )
                }
                return
            }
        }
        _netplayDone.value = false
        // Setting up again destroys the previous room, so guests go back to waiting.
        if (weHostTheRoom && _netplayPrepared.value) {
            scope.launch { client.setHostReady(session.code, false, session.token) }
        }
        val msg = runPrepareNetplay(profile.name)
        _status.value = msg
        if (msg == null) _netplayPrepared.value = true
    }

    /** ARMSX2's direct path performs its former two steps behind one launch. */
    fun onLaunchStep() {
        scope.launch {
            _status.value = runLaunch(
                onPs2Started = {
                    if (_ps2Automatic.value) {
                        _netplayPrepared.value = true
                        _netplayDone.value = true
                    }
                },
                onLaunched = { _launched.value = true }
            )
        }
    }

    /** Not a step of [runLaunch]: it only opens PPSSPP, and returns the message to show. */
    fun openPspSetup() {
        _status.value = when (val result = ppsspp.openApp()) {
            LaunchResult.Success -> {
                _pspOpened.value = true; null
            }

            LaunchResult.NotInstalled -> context.getString(R.string.err_not_installed, "PPSSPP")
            is LaunchResult.Error -> context.getString(R.string.err_generic, result.message)
            // PPSSPP exposes no netplay to drive: this case cannot come from it.
            is LaunchResult.NoNetplayUi -> null
        }
    }

    // Only the code is still copyable: it is what you send a friend in another app.
    // pourquoi : docs/decisions/session.md § Copying the address stopped making sense once Emufii fills it in
    fun onCopyCode() {
        copyToClipboard(context, "Emufii", session.code)
        _status.value = context.getString(R.string.common_copied, session.code)
    }

    fun confirmLeave() {
        _confirmingLeave.value = true
    }

    fun dismissLeave() {
        _confirmingLeave.value = false
    }

    fun clearStatus() {
        _status.value = null
    }

    /** Called from the composable's ON_RESUME observer: it owns the LifecycleOwner. */
    fun onResumed() {
        _automationOn.value = azahar.isNetplayAutomationEnabled()
        // The moment to notice the automation was never heard from.
        // pourquoi : docs/decisions/session.md § Two proofs that a room exists, and the second is knowingly weaker
        if (NetplayAutomation.neverStarted()) {
            NetplayAutomation.report(
                NetplayProgress.Failed(context.getString(R.string.netplay_automation_silent))
            )
        }
        _returns.value += 1
    }

    private suspend fun runLaunch(
        onPs2Started: () -> Unit,
        onLaunched: () -> Unit,
    ): String {
        val rom = session.rom ?: return context.getString(R.string.session_no_rom_attached)
        // An armed plan that stays armed made the automation fight the player for the in-game
        // drawer; here is the one moment we know for certain it is spent.
        if (session.backend.hasNetplay) NetplayAutomation.clear(PlanStore(context))
        val (result, emulator) = when (session.backend) {
            Backend.AZAHAR -> azahar.launchGame(rom.uri, plan = null) to "Azahar"
            Backend.EDEN -> eden.launchGame(
                rom.uri,
                plan = null,
                automationOn = azahar.isNetplayAutomationEnabled()
            ) to "Eden"

            Backend.PPSSPP -> ppsspp.launchPrivateGame(rom) to "PPSSPP"
            // A return, not a launch: resume the existing task, and NO armed plan, which would
            // refill the form over a running game.
            // pourquoi : docs/decisions/session.md § What each backend receives at launch
            Backend.DOLPHIN -> {
                val result = DolphinLauncher(context).launch()
                return if (result == LaunchResult.Success) {
                    onLaunched()
                    context.getString(R.string.session_dolphin_lobby_opened)
                } else {
                    context.getString(R.string.err_not_installed, "Dolphin")
                }
            }
            // A real launch, unlike Dolphin: ARMSX2's MainActivity is exported with a VIEW filter
            // on `content`. Still no armed plan.
            // pourquoi : docs/decisions/session.md § What each backend receives at launch
            Backend.ARMSX2 -> {
                val launcher = Ps2Launcher(context)
                val plan = session.netplayPlan(profileName = null)
                val result = if (plan != null && Ps2GameSettings.canConfigureNow(context, rom)) {
                    launcher.launchPrivateGame(rom, plan)
                } else {
                    // Unsupported CHD codec or a pre-migration profile: keep the proven
                    // accessibility setup as the fallback.
                    launcher.launchGame(rom.uri)
                }
                if (result == LaunchResult.Success) onPs2Started()
                result to "ARMSX2"
            }

            Backend.MELONDS_WFC ->
                return context.getString(R.string.session_wfc_launch_from_library)

            Backend.NONE -> return context.getString(R.string.session_unsupported_console)
        }
        return when (result) {
            LaunchResult.Success -> {
                onLaunched()
                context.getString(R.string.session_launching, rom.displayName)
            }

            LaunchResult.NotInstalled -> context.getString(R.string.err_not_installed, emulator)
            is LaunchResult.NoNetplayUi -> context.getString(
                R.string.err_no_netplay_ui,
                emulator,
                result.versionName ?: "?"
            )

            is LaunchResult.Error -> context.getString(R.string.err_generic, result.message)
        }
    }

    /** Opens the emulator on its multiplayer screen with the plan armed. */
    private fun runPrepareNetplay(profileName: String?): String? {
        val plan = session.netplayPlan(profileName)
            ?: return context.getString(R.string.session_netplay_no_address)
        val (result, emulator) = when (session.backend) {
            Backend.AZAHAR -> azahar.openForNetplay(plan) to "Azahar"
            Backend.EDEN -> eden.openForNetplay(plan) to "Eden"
            // No multiplayer-less Dolphin to detect: a build too old simply lacks the menu entry,
            // at which point the driver stops and the card says what to type.
            Backend.DOLPHIN -> DolphinLauncher(context).openForNetplay(
                plan,
                automationOn = azahar.isNetplayAutomationEnabled()
            ) to "Dolphin"
            // The game is not passed here: Local Link is set in the app's settings and the DEV9
            // adapter initialises at boot, so a port set afterwards is not read back; step two.
            Backend.ARMSX2 -> Ps2Launcher(context).openForLocalLink(
                plan,
                automationOn = azahar.isNetplayAutomationEnabled()
            ) to "ARMSX2"

            else -> return null
        }
        return when (result) {
            LaunchResult.Success -> null
            LaunchResult.NotInstalled -> context.getString(R.string.err_not_installed, emulator)
            is LaunchResult.NoNetplayUi -> context.getString(
                R.string.err_no_netplay_ui, emulator, result.versionName ?: "?"
            )

            is LaunchResult.Error -> context.getString(R.string.err_generic, result.message)
        }
    }
}

@Composable
internal fun rememberSessionScreenState(
    session: Session,
    client: CoordinatorClient,
    profile: Profile,
    onSessionEnded: () -> Unit,
): SessionScreenState {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    return remember(session.code) {
        SessionScreenState(
            context = context.applicationContext,
            session = session,
            client = client,
            profile = profile,
            roms = RomsRepository.get(context),
            azahar = AzaharLauncher(context),
            eden = EdenLauncher(context),
            ppsspp = PpssppLauncher(context),
            scope = scope,
            onSessionEnded = onSessionEnded,
        )
    }
}
