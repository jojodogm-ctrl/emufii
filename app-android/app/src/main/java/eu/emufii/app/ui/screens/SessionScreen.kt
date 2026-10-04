package eu.emufii.app.ui.screens

import androidx.compose.material3.MaterialTheme
import eu.emufii.app.ui.screens.session.LocalNetplayBusy
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import eu.emufii.app.ui.EntryScroll
import eu.emufii.app.ui.LocalEntryScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.emufii.app.R
import eu.emufii.app.profile.playerDisplayName
import eu.emufii.app.ui.theme.LocalEmufiiDarkTheme
import eu.emufii.app.ui.theme.Coral
import androidx.compose.foundation.layout.Box
import eu.emufii.app.ui.components.EventToast
import eu.emufii.app.library.Backend
import eu.emufii.app.network.CoordinatorClient
import eu.emufii.app.profile.Profile
import eu.emufii.app.secondscreen.PanelStep
import eu.emufii.app.secondscreen.SecondScreen
import eu.emufii.app.secondscreen.rememberPresentationDisplay
import eu.emufii.app.session.Session
import eu.emufii.app.session.netplayPlan
import eu.emufii.app.settings.SettingsStore
import eu.emufii.app.ui.screens.session.CodeCard
import eu.emufii.app.ui.screens.session.ConnectionCard
import eu.emufii.app.ui.screens.session.EmulatorHintCard
import eu.emufii.app.ui.screens.session.LaunchButton
import eu.emufii.app.ui.screens.session.LeaveButton
import eu.emufii.app.ui.screens.session.AutoSetupNetplayButton
import eu.emufii.app.ui.screens.session.OfflineCard
import eu.emufii.app.ui.screens.session.PresenceCard
import eu.emufii.app.ui.screens.session.PspHintCard
import eu.emufii.app.ui.screens.session.PspSetupButton
import eu.emufii.app.ui.screens.session.SessionCodeChip
import eu.emufii.app.ui.screens.session.SessionLandscapeLayout
import eu.emufii.app.ui.screens.session.SessionManualDialog
import eu.emufii.app.ui.screens.session.StatusLine
import eu.emufii.app.ui.screens.session.danger
import eu.emufii.app.ui.screens.session.launchEnabled
import eu.emufii.app.ui.screens.session.launchLabel
import eu.emufii.app.ui.screens.session.launchWaits
import eu.emufii.app.ui.screens.session.rememberSessionScreenState
import eu.emufii.app.ui.CONFIRM_KEYS
import eu.emufii.app.ui.LocalRingTone
import eu.emufii.app.ui.RingTone
import eu.emufii.app.ui.Sfx
import eu.emufii.app.ui.components.CrossIcon
import eu.emufii.app.ui.components.EmufiiScaffold
import eu.emufii.app.ui.components.GhostButton
import eu.emufii.app.ui.components.LocalScaffoldFocus
import eu.emufii.app.ui.components.PadDialog
import eu.emufii.app.ui.components.PadDialogText
import eu.emufii.app.ui.components.ScaffoldFocus
import eu.emufii.app.ui.components.padEntry
import eu.emufii.app.ui.screens.session.ManualSetupNetplayButton

@Composable
fun SessionScreen(
    session: Session,
    profile: Profile,
    client: CoordinatorClient,
    onLeave: () -> Unit,
    onSessionEnded: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val state = rememberSessionScreenState(
        session = session,
        client = client,
        profile = profile,
        onSessionEnded = onSessionEnded,
    )
    val ui by state.uiState.collectAsStateWithLifecycle()
    val status = ui.status
    val netplayPrepared = ui.netplayPrepared
    val netplayDone = ui.netplayDone
    val pspOpened = ui.pspOpened
    val offline = ui.offline
    val automationOn = ui.automationOn
    val launched = ui.launched
    val sessionArt = ui.sessionArt
    val confirmingLeave = ui.confirmingLeave
    val ps2Automatic = ui.ps2Automatic
    val pspAutomatic = state.pspAutomatic
    val waitingForHost = ui.waitingForHost

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) state.onResumed()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val onNetplayStep = state::onNetplayStep
    val onLaunchStep = state::onLaunchStep

    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val others = ui.others

    val localWindowInfo = LocalWindowInfo.current
    val landscape = localWindowInfo.containerSize.width > localWindowInfo.containerSize.height

    val psp = session.backend == Backend.PPSSPP
    val room = session.room
    val shownAddress = session.shownAddress
    val shownPort = session.shownPort
    val addressLabel = stringResource(
        when {
            room != null -> R.string.session_room_address
            psp -> R.string.session_psp_address
            else -> R.string.session_host_address
        }
    )
    val onCopyCode = state::onCopyCode

    val panelDisplay by rememberPresentationDisplay()
    val panelWanted by remember(context) { SettingsStore.get(context).secondScreen }
        .collectAsStateWithLifecycle()
    val panelLive = panelWanted && panelDisplay != null

    val showNetplayStep = session.backend.hasNetplay && !ps2Automatic
    val showPspStep = session.backend == Backend.PPSSPP && !pspAutomatic
    val netplayBusy by state.netplayBusy.collectAsStateWithLifecycle()
    val netplayLabel = stringResource(
        when {
            waitingForHost -> R.string.session_netplay_waiting_host
            netplayDone -> R.string.session_netplay_done
            netplayBusy -> R.string.session_netplay_busy
            netplayPrepared -> R.string.session_netplay_again
            else -> R.string.session_netplay_open
        },
        session.backend.emulatorName
    )
    val pspLabel = stringResource(
        if (pspOpened) R.string.session_psp_setup_again else R.string.session_psp_setup
    )
    val launchedLabel = stringResource(
        if (session.backend.hasNetplay && !ps2Automatic) R.string.session_launch_done_step2
        else R.string.session_launch_done
    )
    val launchLabel = launchLabel(
        session = session,
        directPs2 = ps2Automatic,
        waitingForHost = launchWaits(session, ps2Automatic, waitingForHost)
    )
    val panelSteps = buildPanelSteps(
        session = session,
        showNetplayStep = showNetplayStep,
        netplayLabel = netplayLabel,
        netplayDone = netplayDone,
        netplayPrepared = netplayPrepared,
        waitingForHost = waitingForHost,
        netplayBusy = netplayBusy,
        onNetplayStep = onNetplayStep,
        showPspStep = showPspStep,
        pspLabel = pspLabel,
        pspOpened = pspOpened,
        onPspSetup = state::openPspSetup,
        launched = launched,
        launchedLabel = launchedLabel,
        launchLabel = launchLabel,
        ps2Automatic = ps2Automatic,
        onLaunchStep = onLaunchStep,
    )
    // Clear the lambdas on dispose, or the panel keeps a dead session.
    DisposableEffect(panelLive, panelSteps) {
        SecondScreen.publishSteps(if (panelLive) panelSteps else emptyList())
        onDispose { SecondScreen.publishSteps(emptyList()) }
    }

    val panelCursor by SecondScreen.stepCursor.collectAsStateWithLifecycle()

    var joinedName by remember { mutableStateOf<String?>(null) }
    var seenMembers by remember { mutableStateOf<Set<String>?>(null) }
    val memberIds = others.map { it.id }.toSet()
    LaunchedEffect(memberIds) {
        val before = seenMembers
        seenMembers = memberIds
        if (before != null) {
            others.firstOrNull { it.id !in before }?.let {
                Sfx.pop()
                joinedName = it.name
            }
        }
    }

    CompositionLocalProvider(
        LocalRingTone provides RingTone.CORAL,
        LocalNetplayBusy provides netplayBusy
    ) {
      Box(Modifier.fillMaxSize()) {
        EmufiiScaffold(
            title = if (session.role == Session.Role.HOST) stringResource(R.string.session_mine) else stringResource(
                R.string.session_joined
            ),
            modifier = modifier,
            onBack = state::confirmLeave,
            backIcon = { CrossIcon(size = 20.dp, color = MaterialTheme.colorScheme.onSurface) },
            trailing = if (landscape && !panelLive) {
                { SessionCodeChip(code = session.code, onCopy = onCopyCode) }
            } else null,
            contentScrolls = !landscape
        ) { topPadding ->
            val scaffoldFocus = LocalScaffoldFocus.current

            LaunchedEffect(panelLive, panelSteps) {
                if (panelLive &&
                    panelSteps.isNotEmpty() &&
                    SecondScreen.stepCursor.value == null
                ) {
                    SecondScreen.selectStep(0)
                }
            }

            // One `focusRequester`, the scaffold's: two stacked and the node never took focus.
            val pilotFocus = remember(scaffoldFocus) { scaffoldFocus?.first ?: FocusRequester() }

            // Frame by frame: a single delayed request loses to Compose's initial focus.
            LaunchedEffect(panelLive) {
                if (!panelLive) return@LaunchedEffect
                repeat(PILOT_FOCUS_FRAMES) {
                    withFrameNanos { }
                    runCatching { pilotFocus.requestFocus() }
                }
            }

            val panelPilot = if (!panelLive) Modifier else Modifier
                .focusRequester(pilotFocus)
                // Before `focusable()`, never after: `onFocusChanged` observes what follows it.
                .onFocusChanged { state ->
                    if (state.isFocused &&
                        SecondScreen.stepCursor.value == null &&
                        SecondScreen.steps.value.isNotEmpty()
                    ) {
                        SecondScreen.selectStep(0)
                    }
                }
                .focusable()

                .onKeyEvent { handlePanelKey(it, panelCursor, scaffoldFocus) }
            if (landscape) {
                SessionLandscapeLayout(
                    session = session,
                    profile = profile,
                    topPadding = topPadding,
                    bottomInset = bottomInset,
                    modifier = panelPilot,
                    panelLive = panelLive,
                    others = others,
                    offline = offline,
                    shownAddress = shownAddress,
                    shownPort = shownPort,
                    addressLabel = addressLabel,
                    sessionArt = sessionArt,
                    automationOn = automationOn,
                    pspAutomatic = pspAutomatic,
                    ps2Automatic = ps2Automatic,
                    netplayDone = netplayDone,
                    netplayPrepared = netplayPrepared,
                    pspOpened = pspOpened,
                    waitingForHost = waitingForHost,
                    status = status,
                    onNetplayStep = onNetplayStep,
                    onLaunchStep = onLaunchStep,
                    onPspSetup = state::openPspSetup,
                )
                return@EmufiiScaffold
            }

            val pageScroll = rememberScrollState()
            val page = remember(pageScroll) { EntryScroll(pageScroll) }
            CompositionLocalProvider(LocalEntryScroll provides page) {
            Column(
                modifier = panelPilot
                    .fillMaxSize()
                    .verticalScroll(pageScroll)
                    .padding(
                        top = topPadding,
                        bottom = bottomInset + 24.dp,
                        start = 20.dp,
                        end = 20.dp
                    ),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                CodeCard(code = session.code, isHost = session.role == Session.Role.HOST)

                if (session.backend == Backend.PPSSPP) PspHintCard(pspAutomatic, players = others.size + 1)

                if (offline) OfflineCard()

                PresenceCard(
                    youName = profile.name,
                    others = others,
                    isHost = session.role == Session.Role.HOST,
                    live = !offline
                )

                if (!panelLive) {
                    ConnectionCard(
                        hostIp = shownAddress,
                        addressLabel = addressLabel,
                        port = shownPort,
                        romName = session.rom?.displayName
                    )
                }

                // Before the buttons: Azahar refuses the room over the nickname while blaming the address.
                EmulatorHintCard(
                    session = session,
                    automationOn = automationOn,
                    players = others.size + 1,
                )

                if (!panelLive) {
                    // Emulator order: join the room from its main menu first, then boot the game.
                    if (session.backend.hasNetplay && !ps2Automatic) {
                        var showManualDialog by remember { mutableStateOf(false) }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AutoSetupNetplayButton(
                                session = session,
                                netplayDone = netplayDone,
                                netplayPrepared = netplayPrepared,
                                waitingForHost = waitingForHost,
                                onClick = onNetplayStep,
                                modifier = Modifier
                                    .weight(0.9f)
                                    .padEntry()
                            )

                            Spacer(Modifier.width(12.dp))

                            ManualSetupNetplayButton(
                                onClick = { showManualDialog = true },
                                modifier = Modifier.weight(0.1f)
                            )
                        }
                        if (showManualDialog) {
                            session.netplayPlan(profile.name)?.let { plan ->
                                SessionManualDialog(
                                    plan = plan,
                                    addressLabel = addressLabel,
                                    emulatorName = session.backend.emulatorName,
                                    onDismiss = { showManualDialog = false },
                                )
                            }
                        }
                    }

                    if (session.backend == Backend.PPSSPP && !pspAutomatic) {
                        PspSetupButton(
                            pspOpened = pspOpened,
                            onClick = {
                                state.openPspSetup()
                            },
                            modifier = if (session.backend.hasNetplay) Modifier else Modifier.padEntry()
                        )
                    }

                    LaunchButton(
                        session = session,
                        netplayPrepared = netplayPrepared,
                        directPs2 = ps2Automatic,
                        waitingForHost = launchWaits(session, ps2Automatic, waitingForHost),
                        onClick = onLaunchStep,
                        modifier = if ((session.backend.hasNetplay && !ps2Automatic) ||
                            (session.backend == Backend.PPSSPP && !pspAutomatic)
                        ) Modifier
                        else Modifier.padEntry()
                    )
                }

                status?.let { StatusLine(it) }

                LeaveButton(session = session, onLeave = state::confirmLeave)
            }
            }
        }
    
        val joinedShown = joinedName?.let { playerDisplayName(it) }
        EventToast(
            message = joinedShown?.let { stringResource(R.string.session_player_joined, it) },
            who = joinedShown,
            onGone = { joinedName = null }
        )
      }
    }

    if (confirmingLeave) {
        val host = session.role == Session.Role.HOST
        PadDialog(
            title = stringResource(if (host) R.string.session_close else R.string.session_leave),
            onDismiss = state::dismissLeave,
            panelDetail = stringResource(
                if (host) R.string.session_close_confirm else R.string.session_leave_confirm
            ),
            panelSocial = true,
            actions = {
                GhostButton(
                    label = stringResource(R.string.common_cancel),
                    onClick = state::dismissLeave
                )
                GhostButton(
                    label = stringResource(if (host) R.string.session_close else R.string.session_leave),
                    onClick = {
                        state.dismissLeave()
                        onLeave()
                    },
                    tint = danger()
                )
            }
        ) {
            PadDialogText(
                stringResource(
                    if (host) R.string.session_close_confirm else R.string.session_leave_confirm
                )
            )
        }
    }
}


@Suppress("LongParameterList")
private fun buildPanelSteps(
    session: Session,
    showNetplayStep: Boolean,
    netplayLabel: String,
    netplayDone: Boolean,
    netplayPrepared: Boolean,
    waitingForHost: Boolean,
    netplayBusy: Boolean,
    onNetplayStep: () -> Unit,
    showPspStep: Boolean,
    pspLabel: String,
    pspOpened: Boolean,
    onPspSetup: () -> Unit,
    launched: Boolean,
    launchedLabel: String,
    launchLabel: String,
    ps2Automatic: Boolean,
    onLaunchStep: () -> Unit,
): List<PanelStep> = buildList {
    if (showNetplayStep) {
        add(
            PanelStep(
                label = netplayLabel,
                done = netplayDone,
                enabled = session.rom != null && !waitingForHost && !netplayBusy,
                onPress = onNetplayStep,
                busy = netplayBusy && !netplayDone,
                waiting = waitingForHost
            )
        )
    }
    if (showPspStep) {
        add(
            PanelStep(
                label = pspLabel,
                done = pspOpened,
                enabled = true,
                onPress = onPspSetup
            )
        )
    }
    add(
        PanelStep(
            label = if (launched) launchedLabel else launchLabel,
            done = launched,
            enabled = launchEnabled(
                session = session,
                netplayPrepared = netplayPrepared,
                directPs2 = ps2Automatic,
                waitingForHost = launchWaits(session, ps2Automatic, waitingForHost)
            ),
            onPress = onLaunchStep
        )
    )
}

/** Focus doesn't cross windows: the front pad drives the panel. True consumes the event. */
private fun handlePanelKey(
    event: KeyEvent,
    panelCursor: Int?,
    scaffoldFocus: ScaffoldFocus?,
): Boolean {
    if (event.type != KeyEventType.KeyDown && event.type != KeyEventType.KeyUp) return false

    fun leavePanel() {
        SecondScreen.clearStepCursor()
        scaffoldFocus?.header?.let { runCatching { it.requestFocus() } }
    }

    if (panelCursor == null) {
        return if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown &&
            SecondScreen.steps.value.isNotEmpty()
        ) {
            SecondScreen.selectStep(0)
            true
        } else {
            false
        }
    }

    val steps = SecondScreen.steps.value
    return when {
        event.key in CONFIRM_KEYS -> {
            if (event.type == KeyEventType.KeyUp) {
                steps.getOrNull(panelCursor)?.takeIf { it.enabled }
                    ?.let { Sfx.click(); it.onPress() }
            }
            true
        }

        event.type == KeyEventType.KeyDown -> when (event.key) {
            Key.DirectionLeft -> {
                SecondScreen.moveStep(-1); true
            }

            Key.DirectionRight -> {
                SecondScreen.moveStep(1); true
            }

            Key.DirectionUp -> {
                if (panelCursor == 0) leavePanel() else SecondScreen.moveStep(-1)
                true
            }
            Key.DirectionDown -> true
            Key.ButtonB, Key.Back -> {
                leavePanel(); true
            }

            else -> false
        }

        else -> false
    }
}

private const val PILOT_FOCUS_FRAMES = 6
