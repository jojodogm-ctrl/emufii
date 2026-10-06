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
import kotlinx.coroutines.Job
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

/** WatermelonDS netplay handshake: a guest booting alongside the host misses it. */
private const val DS_GUEST_DELAY_MS = 5000L

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

    private val dsGuestGated = session.backend == Backend.MELONDS && session.role == Session.Role.GUEST
    private var dsGateJob: Job? = null

    private val _status = MutableStateFlow<String?>(null)
    private val _members = MutableStateFlow<List<Member>>(emptyList())
    private val _myHandle = MutableStateFlow<String?>(null)
    private val _netplayPrepared = MutableStateFlow(false)
    private val _netplayDone = MutableStateFlow(false)
    private val _pspOpened = MutableStateFlow(false)
    private val _offline = MutableStateFlow(false)
    private val _hostReady = MutableStateFlow(!dsGuestGated)
    private val _automationOn = MutableStateFlow(azahar.isNetplayAutomationEnabled())
    private val _launched = MutableStateFlow(false)
    private val _sessionArt = MutableStateFlow<Rom?>(null)
    private val _confirmingLeave = MutableStateFlow(false)
    private val _ps2Automatic = MutableStateFlow(
        session.rom != null && session.backend == Backend.ARMSX2 &&
            Ps2GameSettings.canConfigure(context, session.rom)
    )
    private val _returns = MutableStateFlow(0)

    private val _netplayBusy = MutableStateFlow(false)
    val netplayBusy: StateFlow<Boolean> = _netplayBusy

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
            waitingForHost = (hasHostStep || dsGuestGated) &&
                session.role == Session.Role.GUEST && !hostReady,
        )
    }.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = SessionUiState(
            hostReady = _hostReady.value,
            automationOn = _automationOn.value,
            ps2Automatic = _ps2Automatic.value,
            waitingForHost = (hasHostStep || dsGuestGated) && session.role == Session.Role.GUEST,
        ),
    )

    init {
        scope.launch {
            NetplayAutomation.progress.collect { p ->
                if (p is NetplayProgress.Done) _netplayDone.value = true
                _netplayBusy.value = when (p) {
                    NetplayProgress.OpeningMenu, NetplayProgress.ChoosingMode,
                    NetplayProgress.FillingForm, NetplayProgress.Confirming -> true
                    else -> false
                }
            }
        }

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

        scope.launch {
            var gone = 0
            var mute = 0
            while (true) {
                client.heartbeat(session.code, profile.id, profile.name)
                    .onSuccess { beat -> beat.memberHandle?.let { _myHandle.value = it } }
                client.getSession(session.code)
                    .onSuccess {
                        _members.value = it.members
                        eu.emufii.app.profile.AvatarSync.get(context).syncMembers(client, session.code, it.members)
                        if (!dsGuestGated) _hostReady.value = it.hostReady
                        else if (it.hostReady && dsGateJob == null) dsGateJob = scope.launch {
                            delay(DS_GUEST_DELAY_MS.milliseconds)
                            _hostReady.value = true
                        }
                        gone = 0; mute = 0; _offline.value = false
                    }
                    .onFailure { err ->
                        if (err is CoordinatorError.NotFound) gone++ else mute++
                    }

                // Only an actual 404 proves the room is gone, not silence.
                if (gone >= MAX_PRESENCE_MISSES && session.role == Session.Role.GUEST) {
                    onSessionEnded()
                    return@launch
                }
                if (mute >= MAX_PRESENCE_MISSES) _offline.value = true
                delay(PRESENCE_MS.milliseconds)
            }
        }

        scope.launch {
            val uri = session.rom?.uri ?: return@launch
            _sessionArt.value = withContext(Dispatchers.IO) {
                runCatching { roms.cachedOrScan() }
                    .getOrDefault(emptyList())
                    .firstOrNull { it.uri == uri }
            }
        }

        scope.launch {
            _ps2Automatic.value = session.rom != null && session.backend == Backend.ARMSX2 &&
                Ps2GameSettings.canConfigureNow(context, session.rom)
        }
    }

    fun onNetplayStep() {
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
        if (weHostTheRoom && _netplayPrepared.value) {
            scope.launch { client.setHostReady(session.code, false, session.token) }
        }
        val msg = runPrepareNetplay(profile.name)
        _status.value = msg
        if (msg == null) _netplayPrepared.value = true
        if (msg == null && _automationOn.value) {
            _netplayBusy.value = true
            scope.launch {
                delay(BUSY_GIVE_UP_MS)
                if (!_netplayDone.value) _netplayBusy.value = false
            }
        }
    }

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

    fun openPspSetup() {
        _status.value = when (val result = ppsspp.openApp()) {
            LaunchResult.Success -> {
                _pspOpened.value = true; null
            }

            LaunchResult.NotInstalled -> context.getString(R.string.err_not_installed, "PPSSPP")
            is LaunchResult.Error -> context.getString(R.string.err_generic, result.message)
            is LaunchResult.NoNetplayUi -> null
        }
    }

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

    fun onResumed() {
        _automationOn.value = azahar.isNetplayAutomationEnabled()
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
        // An armed plan left over makes the automation fight the player in-game.
        if (session.backend.hasNetplay) NetplayAutomation.clear(PlanStore(context))
        val (result, emulator) = when (session.backend) {
            Backend.AZAHAR -> azahar.launchGame(rom.uri, plan = null) to "Azahar"
            Backend.EDEN -> eden.launchGame(
                rom.uri,
                plan = null,
                automationOn = azahar.isNetplayAutomationEnabled()
            ) to "Eden"

            Backend.PPSSPP -> ppsspp.launchPrivateGame(rom) to "PPSSPP"
            // Resume the existing task, no armed plan: it would refill the form over a running game.
            Backend.DOLPHIN -> {
                val result = DolphinLauncher(context).launch()
                return if (result == LaunchResult.Success) {
                    onLaunched()
                    context.getString(R.string.session_dolphin_lobby_opened)
                } else {
                    context.getString(R.string.err_not_installed, "Dolphin")
                }
            }
            Backend.ARMSX2 -> {
                // No fallback launch: skipping the per-game layer boots with the previous session's room and role.
                val plan = session.netplayPlan(profileName = null)
                    ?: return context.getString(R.string.session_netplay_no_address)
                val result = Ps2Launcher(context).launchPrivateGame(rom, plan)
                if (result == LaunchResult.Success) onPs2Started()
                result to "ARMSX2"
            }

            Backend.MELONDS -> {
                if (session.hostIp.isBlank()) return context.getString(R.string.session_netplay_no_address)
                eu.emufii.app.wfc.MelonDs(context).launchNetplay(
                    rom.uri,
                    isHost = session.role == Session.Role.HOST,
                    hostAddress = session.hostIp,
                    players = _members.value.size
                ).also {
                    if (it == LaunchResult.Success && session.role == Session.Role.HOST) {
                        scope.launch { client.setHostReady(session.code, true, session.token) }
                    }
                } to "WatermelonDS"
            }

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

    private fun runPrepareNetplay(profileName: String?): String? {
        val plan = session.netplayPlan(profileName)
            ?: return context.getString(R.string.session_netplay_no_address)
        val (result, emulator) = when (session.backend) {
            Backend.AZAHAR -> azahar.openForNetplay(plan) to "Azahar"
            Backend.EDEN -> eden.openForNetplay(plan) to "Eden"
            Backend.DOLPHIN -> DolphinLauncher(context).openForNetplay(
                plan,
                automationOn = azahar.isNetplayAutomationEnabled()
            ) to "Dolphin"
            // DEV9 initialises at boot, so the port must be set before the game starts.
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

private const val BUSY_GIVE_UP_MS = 60_000L
