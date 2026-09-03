package eu.emufii.app.ui.screens

import android.icu.text.ListFormatter
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.emufii.app.R
import eu.emufii.app.azahar.AzaharLauncher
import eu.emufii.app.azahar.LaunchResult
import eu.emufii.app.azahar.NetplayAutomation
import eu.emufii.app.azahar.NetplayPlan
import eu.emufii.app.azahar.NetplayProgress
import eu.emufii.app.azahar.PlanStore
import eu.emufii.app.dolphin.DolphinLauncher
import eu.emufii.app.dolphin.DolphinTarget
import eu.emufii.app.eden.EdenLauncher
import eu.emufii.app.library.Backend
import eu.emufii.app.library.Rom
import eu.emufii.app.library.RomsRepository
import eu.emufii.app.netplay.NetplayNames
import eu.emufii.app.network.CoordinatorClient
import eu.emufii.app.network.CoordinatorError
import eu.emufii.app.network.Member
import eu.emufii.app.profile.Profile
import eu.emufii.app.profile.playerDisplayName
import eu.emufii.app.ps2.Ps2GameSettings
import eu.emufii.app.ps2.Ps2Launcher
import eu.emufii.app.ps2.Ps2NetworkProfile
import eu.emufii.app.ps2.Ps2ProvisioningPlan
import eu.emufii.app.ps2.Ps2Target
import eu.emufii.app.psp.HOST_SENTINEL
import eu.emufii.app.psp.PpssppConfigStore
import eu.emufii.app.psp.PpssppLauncher
import eu.emufii.app.secondscreen.PanelStep
import eu.emufii.app.secondscreen.SecondScreen
import eu.emufii.app.secondscreen.rememberPresentationDisplay
import eu.emufii.app.session.Session
import eu.emufii.app.settings.SettingsStore
import eu.emufii.app.ui.ActionShape
import eu.emufii.app.ui.CONFIRM_KEYS
import eu.emufii.app.ui.LocalRingTone
import eu.emufii.app.ui.RingTone
import eu.emufii.app.ui.Sfx
import eu.emufii.app.ui.components.AvatarStack
import eu.emufii.app.ui.components.CrossIcon
import eu.emufii.app.ui.components.EmufiiScaffold
import eu.emufii.app.ui.components.GhostButton
import eu.emufii.app.ui.components.LocalScaffoldFocus
import eu.emufii.app.ui.components.PadDialog
import eu.emufii.app.ui.components.PadDialogText
import eu.emufii.app.ui.components.RomArtwork
import eu.emufii.app.ui.components.SectionHeader
import eu.emufii.app.ui.components.SoftCard
import eu.emufii.app.ui.components.WarnIcon
import eu.emufii.app.ui.components.padEntry
import eu.emufii.app.ui.components.softCardFill
import eu.emufii.app.ui.controlRing
import eu.emufii.app.ui.copyToClipboard
import eu.emufii.app.ui.sounded
import eu.emufii.app.ui.tap
import eu.emufii.app.ui.theme.Coral
import eu.emufii.app.ui.theme.ErrorDark
import eu.emufii.app.ui.theme.ErrorLight
import eu.emufii.app.ui.theme.GoodDark
import eu.emufii.app.ui.theme.GoodLight
import eu.emufii.app.ui.theme.LocalEmufiiDarkTheme
import eu.emufii.app.ui.theme.LocalEmufiiOledTheme
import eu.emufii.app.ui.theme.PillShape
import eu.emufii.app.ui.theme.edgeColor
import eu.emufii.app.ui.theme.plate
import eu.emufii.app.ui.theme.socket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
private fun danger() = if (LocalEmufiiDarkTheme.current) ErrorDark else ErrorLight

@Composable
private fun good() = if (LocalEmufiiDarkTheme.current) GoodDark else GoodLight

@Composable
private fun coralText() = if (LocalEmufiiDarkTheme.current) Coral.darkBright else Coral.ink

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
    val azahar = remember { AzaharLauncher(context) }
    val eden = remember { EdenLauncher(context) }
    val ppsspp = remember { PpssppLauncher(context) }
    val pspAutomatic = remember(session.code, session.rom) {
        val rom = session.rom
        rom != null && PpssppConfigStore(context).canApply(
            rom.productCode,
            rom.filename,
            rom.displayName,
        )
    }
    // First frame from the library scan, then the disc itself: an old scan can carry a stale serial.
    val ps2Automatic by produceState(
        initialValue = session.rom != null && session.backend == Backend.ARMSX2 &&
            Ps2GameSettings.canConfigure(context, session.rom),
        session.code, session.rom
    ) {
        value = session.rom != null && session.backend == Backend.ARMSX2 &&
            Ps2GameSettings.canConfigureNow(context, session.rom)
    }
    var status by remember { mutableStateOf<String?>(null) }
    var members by remember { mutableStateOf<List<Member>>(emptyList()) }
    // Our name in this session's list, see `Heartbeat.memberHandle`; re-read every beat.
    var myHandle by remember { mutableStateOf<String?>(null) }

    var netplayPrepared by remember(session.code) { mutableStateOf(false) }

    /**
     * Latched, not read off the progress flow, which starting the game resets.
     * pourquoi : docs/decisions/session.md § Two proofs that a room exists, and the second is knowingly weaker
     */
    var netplayDone by remember(session.code) { mutableStateOf(false) }

    var pspOpened by remember(session.code) { mutableStateOf(false) }
    val netplayProgress by NetplayAutomation.progress.collectAsStateWithLifecycle()
    LaunchedEffect(netplayProgress) {
        if (netplayProgress is NetplayProgress.Done) netplayDone = true
    }

    var offline by remember { mutableStateOf(false) }

    /**
     * True from the start, which is what an ignorant coordinator answers.
     * pourquoi : docs/decisions/session.md § Host then guest is not a comfort detail
     */
    var hostReady by remember(session.code) { mutableStateOf(true) }

    /**
     * An upstream room changes nothing: the room and the game's session are two things.
     * pourquoi : docs/decisions/session.md § Host then guest is not a comfort detail
     */
    val hasHostStep = session.backend.hasNetplay
    val weHostTheRoom = hasHostStep && session.role == Session.Role.HOST

    val waitingForHost = hasHostStep && session.role == Session.Role.GUEST && !hostReady

    var automationOn by remember { mutableStateOf(azahar.isNetplayAutomationEnabled()) }

    /**
     * Returns into Emufii from outside: the second, knowingly weaker proof of a room.
     * pourquoi : docs/decisions/session.md § Two proofs that a room exists, and the second is knowingly weaker
     */
    var returns by remember(session.code) { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                automationOn = azahar.isNetplayAutomationEnabled()
                // The moment to notice the automation was never heard from.
                // pourquoi : docs/decisions/session.md § Two proofs that a room exists, and the second is knowingly weaker
                if (NetplayAutomation.neverStarted()) {
                    NetplayAutomation.report(
                        NetplayProgress.Failed(context.getString(R.string.netplay_automation_silent))
                    )
                }
                returns++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val scope = rememberCoroutineScope()

    // Publish our room once it exists; two signals count as proof.
    // pourquoi : docs/decisions/session.md § Two proofs that a room exists, and the second is knowingly weaker
    LaunchedEffect(weHostTheRoom, netplayDone, returns) {
        if (!weHostTheRoom) return@LaunchedEffect
        if (netplayDone || (netplayPrepared && returns > 0)) {
            client.setHostReady(session.code, true, session.token)
        }
    }

    /** One definition for both layouts, which drifted apart once. */
    val onNetplayStep: () -> Unit = fun() {
        // Only an image whose boot ELF cannot be read reaches this branch: it needs the one
        // legacy global assignment per-game files avoid.
        // pourquoi : docs/decisions/session.md § What each backend receives at launch
        if (session.backend == Backend.ARMSX2 && !ps2Automatic) {
            val receipt = Ps2NetworkProfile.receipt(context)
            if (receipt != null && !receipt.assigned) {
                if (!automationOn) {
                    status = context.getString(R.string.session_ps2_fallback_accessibility)
                    return
                }
                status = when (val result = Ps2Launcher(context).openForProvisioning(
                    Ps2ProvisioningPlan(
                        receipt.cardName,
                        receipt.cardSha256,
                        receipt.sourceCardForSlot2,
                    )
                )) {
                    LaunchResult.Success -> context.getString(R.string.session_ps2_fallback_assigning)
                    LaunchResult.NotInstalled -> context.getString(R.string.err_not_installed, "ARMSX2")
                    is LaunchResult.Error -> context.getString(R.string.err_generic, result.message)
                    is LaunchResult.NoNetplayUi -> context.getString(R.string.err_not_installed, "ARMSX2")
                }
                return
            }
        }
        netplayDone = false
        // Setting up again destroys the previous room, so guests go back to waiting.
        if (weHostTheRoom && netplayPrepared) {
            scope.launch { client.setHostReady(session.code, false, session.token) }
        }
        status = session.prepareNetplay(context, azahar, eden, profile.name)
        if (status == null) netplayPrepared = true
    }

    /** ARMSX2's direct path performs its former two steps behind one launch. */
    /**
     * True as soon as the emulator has left, for the rear panel above all: the front screen
     * vanishes behind the emulator, and the panel stayed on a step nothing told apart from one
     * never pressed.
     * pourquoi : docs/decisions/second-ecran.md § A panel that asserts something false is a fault
     */
    var launched by remember(session.code) { mutableStateOf(false) }
    val onLaunchStep: () -> Unit = fun() {
        scope.launch {
            status = session.launch(
                context, azahar, eden, ppsspp,
                onPs2Started = {
                    if (ps2Automatic) {
                        netplayPrepared = true
                        netplayDone = true
                    }
                },
                onLaunched = { launched = true }
            )
        }
    }


    // Announce on a timer and read back who else is here; one loop for both roles.
    LaunchedEffect(session.code, profile.id) {
        var gone = 0
        var mute = 0
        while (true) {
            client.heartbeat(session.code, profile.id, profile.name)
                .onSuccess { beat -> beat.memberHandle?.let { myHandle = it } }
            client.getSession(session.code)
                .onSuccess {
                    members = it.members
                    hostReady = it.hostReady
                    gone = 0; mute = 0; offline = false
                }
                .onFailure { err ->
                    if (err is CoordinatorError.NotFound) gone++ else mute++
                }

            // Only a coordinator that *answers* 404 proves the room is gone; a silent one
            // proves only that we cannot reach it.
            // pourquoi : docs/decisions/session.md § Only a 404 proves a room is closed
            if (gone >= MAX_PRESENCE_MISSES && session.role == Session.Role.GUEST) {
                onSessionEnded()
                return@LaunchedEffect
            }
            if (mute >= MAX_PRESENCE_MISSES) offline = true
            delay(PRESENCE_MS)
        }
    }

    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    // An up-to-date coordinator returns the handle, an old one the friend code.
    val others = members.filter { it.id != myHandle && it.id != profile.id }

    val configuration = LocalConfiguration.current
    val landscape = configuration.screenWidthDp > configuration.screenHeightDp

    // Hoisted: computed inside one column the value would be true on one side only.
    // pourquoi : docs/decisions/session.md § The address shown is the one to type, never another
    val psp = session.backend == Backend.PPSSPP
    // With a VPS room nobody hosts, so the host's address is the address of nothing.
    // pourquoi : docs/decisions/session.md § The address shown is the one to type, never another
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
    // Only the code is still copyable: it is what you send a friend in another app.
    // pourquoi : docs/decisions/session.md § Copying the address stopped making sense once Emufii fills it in
    val onCopyCode = {
        copyToClipboard(context, "Emufii", session.code)
        status = context.getString(R.string.common_copied, session.code)
    }

    // The setting is not enough: the device may have only one screen.
    // pourquoi : docs/decisions/session.md § What the rear panel carries, the front screen does not repeat
    val panelDisplay by rememberPresentationDisplay()
    val panelWanted by remember(context) { SettingsStore.get(context).secondScreen }
        .collectAsStateWithLifecycle()
    val panelLive = panelWanted && panelDisplay != null

    // The session carries only a ROM reference, no icon and no extracted colour.
    // pourquoi : docs/decisions/session.md § The game is shown in the space the panel left
    var sessionArt by remember(session.code) { mutableStateOf<Rom?>(null) }
    LaunchedEffect(session.rom?.uri) {
        val uri = session.rom?.uri ?: return@LaunchedEffect
        sessionArt = withContext(Dispatchers.IO) {
            runCatching { RomsRepository(context).cachedOrScan() }
                .getOrDefault(emptyList())
                .firstOrNull { it.uri == uri }
        }
    }

    // Resolved once and served to both screens: the labels travel already translated.
    // pourquoi : docs/decisions/second-ecran.md § What travels to the panel travels already resolved
    // pourquoi : docs/decisions/second-ecran.md § The panel takes the steps, because it is touch
    val showNetplayStep = session.backend.hasNetplay && !ps2Automatic
    val showPspStep = session.backend == Backend.PPSSPP && !pspAutomatic
    val netplayLabel = stringResource(
        when {
            waitingForHost -> R.string.session_netplay_waiting_host
            netplayDone -> R.string.session_netplay_done
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
        netplayPrepared = netplayPrepared,
        directPs2 = ps2Automatic,
        waitingForHost = ps2Automatic && waitingForHost
    )
    val panelSteps = buildList {
        if (showNetplayStep) {
            add(
                PanelStep(
                    label = netplayLabel,
                    done = netplayDone,
                    enabled = session.rom != null && !waitingForHost,
                    onPress = onNetplayStep
                )
            )
        }
        if (showPspStep) {
            add(
                PanelStep(
                    label = pspLabel,
                    done = pspOpened,
                    enabled = true,
                    onPress = {
                        status = openPpssppForSetup(context, ppsspp) { pspOpened = true }
                    }
                )
            )
        }
        add(
            PanelStep(
                // Once launched the step keeps its place and changes face, still pressable.
                label = if (launched) launchedLabel else launchLabel,
                done = launched,
                enabled = launchEnabled(
                    session = session,
                    netplayPrepared = netplayPrepared,
                    directPs2 = ps2Automatic,
                    waitingForHost = ps2Automatic && waitingForHost
                ),
                onPress = onLaunchStep
            )
        )
    }
    // The lambdas belong to this composition: clear them on the way out, or the panel keeps a dead session.
    DisposableEffect(panelLive, panelSteps) {
        SecondScreen.publishSteps(if (panelLive) panelSteps else emptyList())
        onDispose { SecondScreen.publishSteps(emptyList()) }
    }

    // Focus does not cross windows, so the front pad drives the panel's steps.
    // pourquoi : docs/decisions/second-ecran.md § R turns the page from both screens
    val panelCursor by SecondScreen.stepCursor.collectAsStateWithLifecycle()

    // Back closes the session: a red cross, and a question before cutting the tunnel.
    // pourquoi : docs/decisions/session.md § Back closes the session, so it carries a cross and it asks
    var confirmingLeave by remember { mutableStateOf(false) }

    // The social domain: the pad cursor turns coral here.
    // pourquoi : docs/decisions/theme-duotone-shelves.md § GAMEPAD FOCUS
    CompositionLocalProvider(LocalRingTone provides RingTone.CORAL) {
    EmufiiScaffold(
        title = if (session.role == Session.Role.HOST) stringResource(R.string.session_mine) else stringResource(R.string.session_joined),
        modifier = modifier,
        onBack = { confirmingLeave = true },
        backIcon = { CrossIcon(size = 20.dp, color = danger()) },
        // In landscape, leave moves into the header and the 60 dp go back to the left pane.
        // pourquoi : docs/decisions/session.md § What the panel carries, the front screen gives back in space
        trailing = if (landscape && !panelLive) {
            { SessionCodeChip(code = session.code, onCopy = onCopyCode) }
        } else null,
        // Both panes fit, so nothing rises under the header.
        contentScrolls = !landscape
    ) { topPadding ->
        // The pilot lives in the scaffold: leaving past the first step lands on its cross.
        val scaffoldFocus = LocalScaffoldFocus.current

        /**
         * Placed as soon as the steps exist, without waiting for the pilot to take focus.
         * pourquoi : docs/decisions/second-ecran.md § The panel's cursor does not depend on focus, which never arrives
         */
        LaunchedEffect(panelLive, panelSteps) {
            if (panelLive &&
                panelSteps.isNotEmpty() &&
                SecondScreen.stepCursor.value == null
            ) {
                SecondScreen.selectStep(0)
            }
        }

        // One `focusRequester`, the scaffold's: two stacked and the node never took focus.
        // pourquoi : docs/decisions/session.md § One `focusRequester` per node, and it is the shell's
        val pilotFocus = remember(scaffoldFocus) { scaffoldFocus?.first ?: FocusRequester() }

        /**
         * Frame by frame: one request after 150 ms lost against Compose's initial focus.
         * pourquoi : docs/decisions/session.md § One `focusRequester` per node, and it is the shell's
         */
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
                // Giving the cursor back to the pilot gives it back to the steps.
                if (state.isFocused &&
                    SecondScreen.stepCursor.value == null &&
                    SecondScreen.steps.value.isNotEmpty()
                ) {
                    SecondScreen.selectStep(0)
                }
            }
            .focusable()
            // Straight onto the panel's steps: an intermediate stop would be invisible.

            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown && event.type != KeyEventType.KeyUp) {
                    return@onKeyEvent false
                }
                fun leavePanel() {
                    SecondScreen.clearStepCursor()
                    scaffoldFocus?.header?.let { runCatching { it.requestFocus() } }
                }
                val cursor = panelCursor
                // Down, once the front cursor has nothing left below it.
                if (cursor == null) {
                    if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown &&
                        SecondScreen.steps.value.isNotEmpty()
                    ) {
                        SecondScreen.selectStep(0)
                        true
                    } else {
                        false
                    }
                } else {
                    val steps = SecondScreen.steps.value
                    when {
                        // A and its synonyms press the aimed step; KeyDown is swallowed so one press reads once.
                        event.key in CONFIRM_KEYS -> {
                            if (event.type == KeyEventType.KeyUp) {
                                steps.getOrNull(cursor)?.takeIf { it.enabled }
                                    ?.let { Sfx.click(); it.onPress() }
                            }
                            true
                        }
                        event.type == KeyEventType.KeyDown -> when (event.key) {
                            Key.DirectionLeft -> { SecondScreen.moveStep(-1); true }
                            Key.DirectionRight -> { SecondScreen.moveStep(1); true }
                            Key.DirectionUp -> {
                                if (cursor == 0) leavePanel() else SecondScreen.moveStep(-1)
                                true
                            }
                            // One row: down has nowhere to go, and must not hand the cursor back.
                            Key.DirectionDown -> true
                            Key.ButtonB, Key.Back -> { leavePanel(); true }
                            else -> false
                        }
                        else -> false
                    }
                }
            }
        if (landscape) {
            // State on the left, what is left to do on the right, every answer under the
            // button that produced it.
            // pourquoi : docs/decisions/session.md § Two panels, because stacked this screen does not fit
            Row(
                modifier = panelPilot
                    .fillMaxSize()
                    .padding(
                        top = topPadding,
                        bottom = bottomInset + 16.dp,
                        start = 20.dp,
                        end = 20.dp
                    ),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // No `verticalScroll`: a state pane that can hide its state is not doing its job.
                // pourquoi : docs/decisions/session.md § The state panel does not scroll, so it has to fit
                // pourquoi : docs/decisions/session.md § What the panel carries, the front screen gives back in space
                Column(
                    modifier = Modifier.width(if (panelLive) 220.dp else 272.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Presence gives way, never the address: the weight reverses Compose's
                    // measuring order to guarantee it.
                    // pourquoi : docs/decisions/session.md § The state panel does not scroll, so it has to fit
                    PresenceCard(
                        youName = profile.name,
                        youAvatar = profile.avatarFile,
                        others = others,
                        isHost = session.role == Session.Role.HOST,
                        live = !offline,
                        scrollable = true,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    // The panel already carries both address and port, unasked.
                    // pourquoi : docs/decisions/session.md § What the rear panel carries, the front screen does not repeat
                    if (!panelLive) {
                        ConnectionCard(
                            hostIp = shownAddress,
                            addressLabel = addressLabel,
                            port = shownPort,
                            romName = session.rom?.displayName
                        )
                    }

                    // Only once the panel has freed the room; without one there is no gap to fill.
                    // pourquoi : docs/decisions/session.md § What the panel carries, the front screen gives back in space
                    if (panelLive) {
                        sessionArt?.let { art ->
                            Box(
                                modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                                contentAlignment = Alignment.Center
                            ) {
                                BoxWithConstraints {
                                    val side = minOf(maxWidth, maxHeight)
                                    if (side >= 96.dp) {
                                        RomArtwork(rom = art, size = minOf(side, 208.dp))
                                    }
                                }
                            }
                        }
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // The explanation gives way, never the buttons; the fade exists on one screen only.
                    // pourquoi : docs/decisions/session.md § What the panel carries, the front screen gives back in space
                    val fade = !panelLive
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .then(
                                if (!fade) Modifier else Modifier
                                    .graphicsLayer {
                                        compositingStrategy = CompositingStrategy.Offscreen
                                    }
                                    .drawWithContent {
                                        drawContent()
                                        drawRect(
                                            brush = Brush.verticalGradient(
                                                0f to Color.Transparent,
                                                0.05f to Color.Black,
                                                0.94f to Color.Black,
                                                1f to Color.Transparent
                                            ),
                                            blendMode = BlendMode.DstIn
                                        )
                                    }
                            )
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (offline) OfflineCard()
                        if (session.backend == Backend.PPSSPP) PspHintCard(pspAutomatic)
                        EmulatorHintCard(
                            session = session,
                            automationOn = automationOn,
                        )
                    }

                    // With the panel carrying the steps, the front screen does not redraw them.
                    // pourquoi : docs/decisions/second-ecran.md § The panel takes the steps, because it is touch
                    if (!panelLive) {
                    // The first button that exists AND responds: a disabled one does not take focus.
                    // pourquoi : docs/decisions/session.md § Down aims at the first button that answers
                    Spacer(Modifier.height(2.dp))
                    if (session.backend.hasNetplay && !ps2Automatic) {
                        NetplayButton(
                            session = session,
                            netplayDone = netplayDone,
                            netplayPrepared = netplayPrepared,
                            waitingForHost = waitingForHost,
                            onClick = onNetplayStep,
                            modifier = Modifier.padEntry()
                        )
                    }
                    if (session.backend == Backend.PPSSPP && !pspAutomatic) {
                        PspSetupButton(
                            pspOpened = pspOpened,
                            onClick = {
                                status = openPpssppForSetup(context, ppsspp) { pspOpened = true }
                            },
                            modifier = if (session.backend.hasNetplay) Modifier
                                       else Modifier.padEntry()
                        )
                    }
                    LaunchButton(
                        session = session,
                        netplayPrepared = netplayPrepared,
                        directPs2 = ps2Automatic,
                        waitingForHost = ps2Automatic && waitingForHost,
                        onClick = onLaunchStep,
                        // Last resort: with no step above, this is the first button on the page.
                        modifier = if ((session.backend.hasNetplay && !ps2Automatic) ||
                                       (session.backend == Backend.PPSSPP && !pspAutomatic)) Modifier
                                   else Modifier.padEntry()
                    )
                    }
                    status?.let { StatusLine(it) }
                }
            }
            return@EmufiiScaffold
        }

        Column(
            modifier = panelPilot
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(
                    top = topPadding,
                    bottom = bottomInset + 24.dp,
                    start = 20.dp,
                    end = 20.dp
                ),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            CodeCard(code = session.code, isHost = session.role == Session.Role.HOST)

            // Before everything else: something to do in another program, once.
            // pourquoi : docs/decisions/session.md § What is done by hand is said before the button, never after
            if (session.backend == Backend.PPSSPP) PspHintCard(pspAutomatic)

            // Above the member list, which is what has gone stale: whoever is shown there was
            // here the last time we heard back.
            if (offline) OfflineCard()

            PresenceCard(
                youName = profile.name,
                youAvatar = profile.avatarFile,
                others = others,
                isHost = session.role == Session.Role.HOST,
                live = !offline
            )

            // As in landscape: what the panel reports, the front screen does not repeat.
            // pourquoi : docs/decisions/session.md § What the rear panel carries, the front screen does not repeat
            if (!panelLive) {
                ConnectionCard(
                    hostIp = shownAddress,
                    addressLabel = addressLabel,
                    // No port: the ad hoc server's is fixed and PPSSPP does not ask for it.
                    port = shownPort,
                    romName = session.rom?.displayName
                )
            }

            // Before the buttons: Azahar refuses the room over the nickname while blaming the address.
            // pourquoi : docs/decisions/session.md § What is done by hand is said before the button, never after
            EmulatorHintCard(
                session = session,
                automationOn = automationOn,
            )

            // As in landscape: the panel carries the steps when it is there.
            // pourquoi : docs/decisions/second-ecran.md § The panel takes the steps, because it is touch
            if (!panelLive) {
            // The order the emulator expects: join the room from its main menu, then boot the
            // game. One button did both, and the ROM started in an emulator that had joined nothing.
            if (session.backend.hasNetplay && !ps2Automatic) {
                NetplayButton(
                    session = session,
                    netplayDone = netplayDone,
                    netplayPrepared = netplayPrepared,
                    waitingForHost = waitingForHost,
                    onClick = onNetplayStep,
                    modifier = Modifier.padEntry()
                )
            }

            // The button does not apply the settings, it opens the emulator, and says so.
            // pourquoi : docs/decisions/session.md § The per-console cards, and what each must prevent
            if (session.backend == Backend.PPSSPP && !pspAutomatic) {
                PspSetupButton(
                    pspOpened = pspOpened,
                    onClick = { status = openPpssppForSetup(context, ppsspp) { pspOpened = true } },
                    modifier = if (session.backend.hasNetplay) Modifier else Modifier.padEntry()
                )
            }

            LaunchButton(
                session = session,
                netplayPrepared = netplayPrepared,
                directPs2 = ps2Automatic,
                waitingForHost = ps2Automatic && waitingForHost,
                onClick = onLaunchStep,
                modifier = if ((session.backend.hasNetplay && !ps2Automatic) ||
                               (session.backend == Backend.PPSSPP && !pspAutomatic)) Modifier
                           else Modifier.padEntry()
            )
            }

            // Under the button that produces it: rendered last, a refusal landed off-screen
            // and read as a dead button.
            // pourquoi : docs/decisions/session.md § What is done by hand is said before the button, never after
            status?.let { StatusLine(it) }

            LeaveButton(session = session, onLeave = { confirmingLeave = true })
        }
    }
    }

    if (confirmingLeave) {
        val host = session.role == Session.Role.HOST
        PadDialog(
            title = stringResource(if (host) R.string.session_close else R.string.session_leave),
            onDismiss = { confirmingLeave = false },
            // The dialog that made the panel most wrong: it kept showing the code while asking to leave.
            panelDetail = stringResource(
                if (host) R.string.session_close_confirm else R.string.session_leave_confirm
            ),
            panelSocial = true,
            actions = {
                GhostButton(
                    label = stringResource(R.string.common_cancel),
                    onClick = { confirmingLeave = false }
                )
                GhostButton(
                    label = stringResource(if (host) R.string.session_close else R.string.session_leave),
                    onClick = {
                        confirmingLeave = false
                        onLeave()
                    },
                    tint = danger()
                )
            }
        ) {
            // Host and guest do not risk the same thing: one closes for everyone, the other withdraws.
            PadDialogText(
                stringResource(
                    if (host) R.string.session_close_confirm else R.string.session_leave_confirm
                )
            )
        }
    }
}

/** One definition, so the two layouts cannot drift apart. */
@Composable
private fun EmulatorHintCard(
    session: Session,
    automationOn: Boolean,
) {
    if (session.rom == null) {
        MissingRomCard()
        return
    }
    when (session.backend) {
        Backend.AZAHAR -> AzaharHintCard(
            automationOn = automationOn,
            isHost = session.role == Session.Role.HOST,
            hostIp = session.hostIp,
            port = session.port
        )
        // Unreachable from the library, which routes DS to the Kaeru screen; a session joined
        // from the finder carries whatever console the host had.
        Backend.EDEN -> EdenHintCard(
            automationOn = automationOn,
            // With a room on the VPS the host joins like everyone else: "Create" would open a
            // second, empty room next to the one where their guest waits.
            isHost = session.role == Session.Role.HOST && session.room == null,
            // What the player would type by hand: the room when there is one, the host otherwise.
            hostIp = session.room?.host ?: session.hostIp,
            port = session.room?.port?.toString() ?: session.port
        )
        Backend.DOLPHIN -> DolphinHintCard(
            automationOn = automationOn,
            isHost = session.role == Session.Role.HOST,
            hostIp = session.hostIp,
            port = DolphinTarget.DEFAULT_PORT.toString()
        )
        Backend.ARMSX2 -> Ps2HintCard(
            automationOn = session.rom.let {
                Ps2GameSettings.canConfigure(LocalContext.current, it)
            } == true,
            isHost = session.role == Session.Role.HOST,
            hostIp = session.hostIp,
            port = Ps2Target.DEFAULT_PORT.toString()
        )
        Backend.PPSSPP -> Unit
        Backend.MELONDS_WFC -> WfcNotASessionCard()
        Backend.NONE -> UnsupportedHintCard(session.console?.label)
    }
}

/**
 * Green once the room is actually joined, not once the emulator was merely opened.
 * pourquoi : docs/decisions/session.md § Two proofs that a room exists, and the second is knowingly weaker
 */
@Composable
private fun NetplayButton(
    session: Session,
    netplayDone: Boolean,
    netplayPrepared: Boolean,
    /**
     * The button greys out and says so rather than sending the guest to a room that does not exist.
     * pourquoi : docs/decisions/session.md § Host then guest is not a comfort detail
     */
    waitingForHost: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val enabled = session.rom != null && !waitingForHost
    Button(
        onClick = sounded(onClick),
        enabled = enabled,
        shape = ActionShape,
        colors = if (netplayDone) {
            ButtonDefaults.buttonColors(containerColor = good())
        } else {
            ButtonDefaults.buttonColors()
        },
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .controlRing(ActionShape)
            // Greyed out but still reachable: focus says where you are, not that a click lands.
            // pourquoi : docs/decisions/session.md § Down aims at the first button that answers
            .then(if (enabled) Modifier else Modifier.focusable())
    ) {
        if (netplayDone) {
            CheckMark(color = MaterialTheme.colorScheme.onPrimary)
            Spacer(Modifier.width(10.dp))
        }
        Text(
            stringResource(
                when {
                    waitingForHost -> R.string.session_netplay_waiting_host
                    netplayDone -> R.string.session_netplay_done
                    netplayPrepared -> R.string.session_netplay_again
                    else -> R.string.session_netplay_open
                },
                // The emulator this session drives: "Azahar" was hard-coded in the string, so a
                // Switch session announced the wrong program by name.
                session.backend.emulatorName
            ),
            style = MaterialTheme.typography.titleMedium
        )
    }
}

/**
 * PPSSPP has no netplay to drive: this opens the emulator, and the label says exactly that.
 * pourquoi : docs/decisions/session.md § The per-console cards, and what each must prevent
 */
@Composable
private fun PspSetupButton(
    pspOpened: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = sounded(onClick),
        shape = ActionShape,
        colors = if (pspOpened) {
            ButtonDefaults.buttonColors(containerColor = good())
        } else {
            ButtonDefaults.buttonColors()
        },
        modifier = modifier.fillMaxWidth().height(56.dp).controlRing(ActionShape)
    ) {
        if (pspOpened) {
            CheckMark(color = MaterialTheme.colorScheme.onPrimary)
            Spacer(Modifier.width(10.dp))
        }
        Text(
            stringResource(
                if (pspOpened) R.string.session_psp_setup_again
                else R.string.session_psp_setup
            ),
            style = MaterialTheme.typography.titleMedium
        )
    }
}

@Composable
private fun LaunchButton(
    session: Session,
    netplayPrepared: Boolean,
    directPs2: Boolean = false,
    waitingForHost: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = sounded(onClick),
        enabled = launchEnabled(session, netplayPrepared, directPs2, waitingForHost),
        shape = ActionShape,
        // Material's grey-on-grey slab read as an absence rather than a button waiting for step 1.
        colors = ButtonDefaults.buttonColors(
            disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
            disabledContentColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
        ),
        modifier = modifier.fillMaxWidth().height(56.dp).controlRing(ActionShape)
    ) {
        Text(
            launchLabel(session, netplayPrepared, directPs2, waitingForHost),
            style = MaterialTheme.typography.titleMedium
        )
    }
}

/**
 * pourquoi : docs/decisions/second-ecran.md § The panel takes the steps, because it is touch
 */
@Composable
private fun launchLabel(
    session: Session,
    netplayPrepared: Boolean,
    directPs2: Boolean,
    waitingForHost: Boolean
): String = when {
    waitingForHost -> stringResource(
        R.string.session_netplay_waiting_host,
        session.backend.emulatorName,
    )
    // Joining a game you do not own is a different case from an unsupported console.
    session.rom == null -> stringResource(R.string.session_no_rom)
    session.backend == Backend.AZAHAR ||
        session.backend == Backend.EDEN ||
        session.backend == Backend.PPSSPP ||
        // The PS2 is included: ARMSX2's `MainActivity` is exported and takes a `content://`.
        session.backend == Backend.ARMSX2 ->
        // Numbered only where a step 1 sits above it.
        stringResource(
            if (session.backend.hasNetplay && !directPs2) R.string.session_launch_step2
            else R.string.session_launch_emulation
        )
    session.backend == Backend.MELONDS_WFC -> stringResource(R.string.session_wfc_not_a_session)
    // Dolphin has no step 2, and saying so beats "not yet supported".
    // pourquoi : docs/decisions/session.md § The per-console cards, and what each must prevent
    session.backend == Backend.DOLPHIN -> stringResource(R.string.session_dolphin_lobby)
    else -> stringResource(R.string.session_unsupported_short)
}

/** Greyed until the room step has run: launching first is what this pair prevents. */
private fun launchEnabled(
    session: Session,
    netplayPrepared: Boolean,
    directPs2: Boolean,
    waitingForHost: Boolean
): Boolean =
    session.rom != null && session.backend != Backend.NONE && !waitingForHost &&
        (!session.backend.hasNetplay || netplayPrepared || directPs2)

/**
 * What you read out loud to someone else, so it stays visible at all times; a tap copies.
 * pourquoi : docs/decisions/session.md § This screen's drawing decisions
 */
@Composable
private fun SessionCodeChip(code: String, onCopy: () -> Unit) {
    val dark = LocalEmufiiDarkTheme.current
    // Round with an explicit height, or `Surface(onClick)` reserves 48 dp and paints inside it.
    // pourquoi : docs/decisions/theme-duotone-shelves.md § Session / Join, coral domain
    Surface(
        onClick = sounded(onCopy),
        shape = CircleShape,
        color = if (dark) Coral.bright else Coral.deep,
        border = BorderStroke(1.dp, edgeColor(dark, oled = false)),
        shadowElevation = 4.dp,
        modifier = Modifier.controlRing(CircleShape)
    ) {
        Row(
            modifier = Modifier.heightIn(min = 48.dp).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                stringResource(R.string.session_code_label).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = if (dark) Coral.ink else Color.White.copy(alpha = 0.80f),
                letterSpacing = 1.sp
            )
            Text(
                code.ifBlank { "—" },
                fontSize = 20.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp,
                color = if (dark) Coral.ink else Color.White
            )
        }
    }
}

@Composable
private fun StatusLine(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = modifier.fillMaxWidth()
    )
}

@Composable
private fun LeaveButton(
    session: Session,
    onLeave: () -> Unit,
    modifier: Modifier = Modifier,
    /** False in the header, where the button hugs the edge. */
    fillWidth: Boolean = true
) {
    // A moulded pill like everything else pressable: the destructive control must not be the
    // one made of nothing.
    // pourquoi : docs/decisions/session.md § This screen's drawing decisions
    val dark = LocalEmufiiDarkTheme.current
    val oled = LocalEmufiiOledTheme.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        modifier = (if (fillWidth) modifier.fillMaxWidth() else modifier)
            .heightIn(min = 48.dp)
            .controlRing(PillShape)
            .plate(shape = PillShape, dark = dark, oled = oled, lift = 4.dp, pressed = pressed)
            .tap(interactionSource = interaction, indication = null, onClick = onLeave)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            if (session.role == Session.Role.HOST) stringResource(R.string.session_close)
            else stringResource(R.string.session_leave),
            style = MaterialTheme.typography.labelLarge,
            color = danger()
        )
    }
}

/**
 * Drawn rather than imported: it sits where it is put, where a glyph is centred on its line
 * box rather than its ink.
 * pourquoi : docs/decisions/session.md § This screen's drawing decisions
 */
@Composable
private fun CheckMark(color: Color, size: Dp = 18.dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val stroke = Stroke(width = w * 0.16f, cap = StrokeCap.Round)
        val path = Path().apply {
            moveTo(w * 0.16f, w * 0.55f)
            lineTo(w * 0.40f, w * 0.79f)
            lineTo(w * 0.86f, w * 0.24f)
        }
        drawPath(path, color = color, style = stroke)
    }
}

@Composable
private fun CodeCard(code: String, isHost: Boolean) {
    SoftCard {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            SectionHeader(stringResource(R.string.session_code_label))
            Text(
                code.ifBlank { "—" },
                fontSize = 44.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Black,
                letterSpacing = 4.sp,
                color = coralText()
            )
            if (isHost) {
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.session_code_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/** pourquoi : docs/decisions/session.md § This screen's drawing decisions */
@Composable
private fun PresenceCard(
    youName: String,
    youAvatar: java.io.File?,
    others: List<Member>,
    isHost: Boolean,
    live: Boolean,
    /**
     * True only in the pane: the single-column page already scrolls, and Compose throws when
     * measuring scrolling content unbounded.
     * pourquoi : docs/decisions/session.md § This screen's drawing decisions
     */
    scrollable: Boolean = false,
    modifier: Modifier = Modifier
) {
    val scroll = rememberScrollState()
    // A line cut in half reads as a rendering glitch, one fading into the card's background as
    // "there is more"; lit only when something is left below the fold.
    val fill = softCardFill()
    val fade = scrollable && scroll.canScrollForward

    SoftCard(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // BEFORE the scroll: after, it works in the unrolled content's coordinates
                // and lands below the fold, invisible.
                // pourquoi : docs/decisions/session.md § This screen's drawing decisions
                .then(
                    if (!fade) Modifier else Modifier.drawWithContent {
                        drawContent()
                        val h = FADE_HEIGHT.toPx()
                        drawRect(
                            // Opaque before the edge, not at it: a linear run left the last
                            // line legible and sliced.
                            // pourquoi : docs/decisions/session.md § This screen's drawing decisions
                            brush = Brush.verticalGradient(
                                colorStops = arrayOf(
                                    0f to Color.Transparent,
                                    0.65f to fill,
                                    1f to fill
                                ),
                                startY = size.height - h,
                                endY = size.height
                            ),
                            topLeft = Offset(0f, size.height - h),
                            size = Size(size.width, h)
                        )
                    }
                )
                .then(if (scrollable) Modifier.verticalScroll(scroll) else Modifier)
                .padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionHeader(
                    if (others.isEmpty()) stringResource(R.string.session_members_label)
                    else pluralStringResource(
                        R.plurals.session_members_count,
                        others.size + 1,
                        others.size + 1
                    )
                )
                Spacer(Modifier.weight(1f))
                // Nothing is live while we are not hearing back: the dot would vouch for a list
                // we can no longer refresh.
                if (others.isNotEmpty() && live) LiveDot()
            }
            Spacer(Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                AvatarStack(
                    names = listOf(playerDisplayName(youName)) + others.map { playerDisplayName(it.name) },
                    size = 40.dp
                )
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(
                        if (others.isEmpty()) stringResource(R.string.session_you_alone)
                        else nameList(
                            listOf(stringResource(R.string.session_you)) +
                                others.map { playerDisplayName(it.name) }
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    if (others.isEmpty() && isHost) {
                        Text(
                            stringResource(R.string.session_waiting),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = others.isNotEmpty(),
                enter = fadeIn() + expandVertically()
            ) {
                Column {
                    Spacer(Modifier.height(12.dp))
                    others.forEach { m ->
                        Text(
                            stringResource(
                                R.string.session_member_since,
                                playerDisplayName(m.name),
                                humanDuration(m.forSeconds)
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/**
 * Enough to erase a whole line and its leading: 28 dp left the cut line half legible.
 * pourquoi : docs/decisions/session.md § This screen's drawing decisions
 */
private val FADE_HEIGHT = 44.dp

@Composable
private fun LiveDot() {
    val transition = rememberInfiniteTransition(label = "live")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Reverse),
        label = "live-alpha"
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(8.dp)
                .alpha(alpha)
                .clip(CircleShape)
                .background(good())
        )
        Spacer(Modifier.size(6.dp))
        Text(
            stringResource(R.string.session_live),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ConnectionCard(
    hostIp: String,
    addressLabel: String,
    /** Null when the console does not ask for one, the column then disappears. */
    port: String?,
    romName: String?,
    /**
     * False in the pane, where its forty dp are what clipped the card, and a game name is not
     * a state you act on.
     * pourquoi : docs/decisions/session.md § This screen's drawing decisions
     */
    showGame: Boolean = true
) {
    SoftCard {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            romName?.takeIf { showGame }?.let {
                Column {
                    SectionHeader(stringResource(R.string.session_game))
                    Text(it, style = MaterialTheme.typography.titleMedium)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    SectionHeader(addressLabel)
                    Text(hostIp.ifBlank { "—" }, style = MaterialTheme.typography.titleMedium)
                }
                if (port != null) {
                    Column {
                        SectionHeader(stringResource(R.string.session_port))
                        Text(port.ifBlank { "—" }, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
            // No copy buttons: Emufii fills the form, and the clipboard holds one value at a time.
            // pourquoi : docs/decisions/session.md § Copying the address stopped making sense once Emufii fills it in
        }
    }
}

/**
 * A line the player cannot afford to skim, inside a card of lines they can.
 * pourquoi : docs/decisions/session.md § This screen's drawing decisions
 */
@Composable
private fun ImportantNote(text: String) {
    // A recess, ordinary ink, a drawn bead, never a red field: red is spent exactly twice in
    // the whole app.
    // pourquoi : docs/decisions/session.md § This screen's drawing decisions
    val dark = LocalEmufiiDarkTheme.current
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .socket(shape, dark)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(11.dp)
    ) {
        WarnIcon(
            size = 17.dp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            // Aligned to the first line's ink, not centred on the block: a mark that drifts to
            // the middle of a three-line note reads as decoration.
            modifier = Modifier.padding(top = 2.dp)
        )
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun AzaharHintCard(
    automationOn: Boolean,
    isHost: Boolean,
    hostIp: String,
    port: String,
) {
    SoftCard {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SectionHeader(stringResource(R.string.hint_azahar_title))
            // Loud on both paths: getting it wrong produces an error that accuses the address.
            // pourquoi : docs/decisions/session.md § What is done by hand is said before the button, never after
            ImportantNote(stringResource(R.string.hint_azahar_username))
            ImportantNote(stringResource(R.string.hint_same_version))
            if (automationOn) {
                Text(
                    stringResource(R.string.hint_azahar_automated),
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                Text(
                    stringResource(
                        R.string.hint_azahar_manual,
                        "$hostIp:$port"
                    ),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

/**
 * Eden's multiplayer is in the app's own settings, not a game drawer; host is told to Create
 * and guest to Join, the same words would put both on one side.
 * pourquoi : docs/decisions/session.md § The per-console cards, and what each must prevent
 */
@Composable
private fun EdenHintCard(
    automationOn: Boolean,
    isHost: Boolean,
    hostIp: String,
    port: String,
) {
    SoftCard {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SectionHeader(stringResource(R.string.hint_eden_title))
            Text(
                stringResource(
                    if (isHost) R.string.hint_eden_host else R.string.hint_eden_guest
                ),
                style = MaterialTheme.typography.bodyMedium
            )
            // A prerequisite the emulator does not mention: a differing game version lets the
            // room form, then the game never starts, and nothing points at the cause.
            ImportantNote(stringResource(R.string.hint_same_version))
            if (automationOn) {
                Text(
                    stringResource(R.string.hint_eden_automated),
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    stringResource(R.string.hint_eden_username),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    stringResource(R.string.hint_eden_manual, "$hostIp:$port"),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

/**
 * One step where the others are two, the game being picked in the lobby; both sides need the
 * same dump, byte for byte.
 * pourquoi : docs/decisions/session.md § The Dolphin prerequisite nobody checks
 */
@Composable
private fun DolphinHintCard(
    automationOn: Boolean,
    isHost: Boolean,
    hostIp: String,
    port: String,
) {
    SoftCard {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SectionHeader(stringResource(R.string.hint_dolphin_title))
            Text(
                stringResource(
                    if (isHost) R.string.hint_dolphin_host else R.string.hint_dolphin_guest
                ),
                style = MaterialTheme.typography.bodyMedium
            )
            ImportantNote(stringResource(R.string.hint_dolphin_same_dump))
            // Worse than the dump: nobody checks the save, and mismatched saves desync
            // silently. We warn, we cannot act.
            // pourquoi : docs/decisions/session.md § The Dolphin prerequisite nobody checks
            ImportantNote(stringResource(R.string.hint_dolphin_same_save))
            if (automationOn) {
                Text(
                    stringResource(R.string.hint_dolphin_automated),
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                Text(
                    stringResource(R.string.hint_dolphin_manual, "$hostIp:$port"),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

/**
 * ARMSX2 has two unrelated multiplayers and Emufii serves only the local one.
 * pourquoi : docs/decisions/session.md § The per-console cards, and what each must prevent
 */
@Composable
private fun Ps2HintCard(
    automationOn: Boolean,
    isHost: Boolean,
    hostIp: String,
    port: String,
) {
    SoftCard {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SectionHeader(stringResource(R.string.hint_ps2_title))
            ImportantNote(stringResource(R.string.hint_ps2_lan_only))
            Text(
                stringResource(if (isHost) R.string.hint_ps2_host else R.string.hint_ps2_guest),
                style = MaterialTheme.typography.bodyMedium
            )
            // Said by ARMSX2 itself: with the network adapter attached some games stop
            // responding to the pad, which reads as a frozen app.
            ImportantNote(stringResource(R.string.hint_ps2_pad))
            if (automationOn) {
                Text(
                    stringResource(R.string.hint_ps2_automated),
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                Text(
                    stringResource(R.string.hint_ps2_manual, "$hostIp:$port"),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun MissingRomCard() {
    SoftCard {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SectionHeader(stringResource(R.string.hint_missing_rom_title))
            Text(
                stringResource(R.string.hint_missing_rom_body),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

/**
 * Deliberately not an error: a running game keeps running, only the presence list stops
 * being trustworthy.
 * pourquoi : docs/decisions/session.md § Only a 404 proves a room is closed
 */
@Composable
private fun OfflineCard() {
    SoftCard {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SectionHeader(stringResource(R.string.session_offline_title))
            Text(
                stringResource(R.string.session_offline_body),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

/**
 * The address is copied on *display* rather than on tap: the player is about to leave for PPSSPP.
 * pourquoi : docs/decisions/session.md § The per-console cards, and what each must prevent
 */
@Composable
private fun PspHintCard(automatic: Boolean) {
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(automatic) {
        if (!automatic) {
            copyToClipboard(context, "Emufii", HOST_SENTINEL)
            copied = true
        }
    }
    SoftCard {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SectionHeader(stringResource(R.string.hint_psp_title))
            Text(
                stringResource(
                    if (automatic) R.string.hint_psp_automated
                    else R.string.hint_psp_body
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (automatic) {
                Text(
                    stringResource(R.string.hint_psp_automatic_ready),
                    style = MaterialTheme.typography.bodySmall,
                    color = good()
                )
                Text(
                    stringResource(R.string.hint_psp_step4),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    HOST_SENTINEL,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = coralText()
                )
                for (step in listOf(
                    stringResource(R.string.hint_psp_step1),
                    stringResource(R.string.hint_psp_step2, HOST_SENTINEL),
                    stringResource(R.string.hint_psp_step2b),
                    stringResource(R.string.hint_psp_step3),
                    stringResource(R.string.hint_psp_step4)
                )) {
                    Text("· " + step, style = MaterialTheme.typography.bodyMedium)
                }
                Text(
                    stringResource(R.string.hint_psp_relay_why),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    stringResource(R.string.hint_psp_why),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (copied) {
                    Text(
                        stringResource(R.string.hint_psp_copied),
                        style = MaterialTheme.typography.bodySmall,
                        color = good()
                    )
                }
                GhostButton(
                    label = stringResource(R.string.hint_psp_copy),
                    onClick = { copyToClipboard(context, "Emufii", HOST_SENTINEL) }
                )
            }
            Text(
                stringResource(R.string.hint_psp_exit_before_switch),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )

            // Neither an ImportantNote, reserved for what stops you playing, nor the grey
            // advisory voice.
            // pourquoi : docs/decisions/session.md § This screen's drawing decisions
            SectionHeader(stringResource(R.string.hint_psp_wifi_title))
            Text(
                stringResource(R.string.hint_psp_wifi),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun WfcNotASessionCard() {
    SoftCard {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SectionHeader(stringResource(R.string.hint_wfc_title))
            Text(
                stringResource(R.string.hint_wfc_body),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun UnsupportedHintCard(consoleLabel: String?) {
    SoftCard {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SectionHeader(consoleLabel ?: stringResource(R.string.hint_unknown_console))
            Text(
                stringResource(R.string.hint_unsupported_body),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

/** ICU does it: the conjunction and the comma placement differ per locale. */
@Composable
private fun nameList(names: List<String>): String {
    val locale = LocalConfiguration.current.locales[0]
    return when (names.size) {
        0 -> ""
        1 -> names[0]
        else -> ListFormatter.getInstance(locale).format(names)
    }
}

@Composable
private fun humanDuration(seconds: Int): String = when {
    seconds < 60 -> stringResource(R.string.duration_seconds)
    seconds < 3600 -> stringResource(R.string.duration_minutes, seconds / 60)
    else -> stringResource(R.string.duration_hours, seconds / 3600)
}

/** Not a step of [Session.launch]: it only opens PPSSPP, and returns the message to show. */
private fun openPpssppForSetup(
    context: android.content.Context,
    ppsspp: PpssppLauncher,
    onOpened: () -> Unit
): String? = when (val result = ppsspp.openApp()) {
    LaunchResult.Success -> { onOpened(); null }
    LaunchResult.NotInstalled -> context.getString(R.string.err_not_installed, "PPSSPP")
    is LaunchResult.Error -> context.getString(R.string.err_generic, result.message)
    // PPSSPP exposes no netplay to drive: this case cannot come from it.
    is LaunchResult.NoNetplayUi -> null
}

private suspend fun Session.launch(
    context: android.content.Context,
    azahar: AzaharLauncher,
    eden: EdenLauncher,
    ppsspp: PpssppLauncher,
    onPs2Started: () -> Unit = {},
    /**
     * Called when the emulator has actually left, and only then: the return value cannot say,
     * "Launching X…" and "Console not supported" are both strings, and the panel must tell
     * them apart.
     * pourquoi : docs/decisions/second-ecran.md § A panel that asserts something false is a fault
     */
    onLaunched: () -> Unit = {},
): String {
    val rom = this.rom ?: return context.getString(R.string.session_no_rom_attached)
    // An armed plan that stays armed made the automation fight the player for the in-game
    // drawer; here is the one moment we know for certain it is spent.
    if (backend.hasNetplay) NetplayAutomation.clear(PlanStore(context))
    val (result, emulator) = when (backend) {
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
            val plan = netplayPlan(profileName = null)
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
private fun Session.prepareNetplay(
    context: android.content.Context,
    azahar: AzaharLauncher,
    eden: EdenLauncher,
    profileName: String?
): String? {
    val plan = netplayPlan(profileName)
        ?: return context.getString(R.string.session_netplay_no_address)
    val (result, emulator) = when (backend) {
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

/**
 * Both roles point Azahar at the host's tunnel address: `netPlayCreateRoom` binds and
 * self-joins on the same address (PHASE0_AZAHAR.md).
 * pourquoi : docs/decisions/session.md § What each backend receives at launch
 */
internal fun Session.netplayPlan(profileName: String?): NetplayPlan? {
    // With a room on the VPS nobody hosts: both players join it, so the game no longer
    // depends on a phone being reachable, and the tunnel need not be up to dial.
    room?.let {
        return NetplayPlan(
            role = NetplayPlan.Role.Guest,
            ip = it.host,
            port = it.port,
            username = NetplayNames.usernameFor(backend, profileName),
            password = it.password
        )
    }
    if (hostIp.isBlank()) return null
    return NetplayPlan(
        role = when (role) {
            Session.Role.HOST -> NetplayPlan.Role.Host
            Session.Role.GUEST -> NetplayPlan.Role.Guest
        },
        ip = hostIp,
        // Otherwise the target emulator's, 2626 for Dolphin and 24872 for the others: a shared
        // default would send the Dolphin guest to a silent port.
        port = port.toIntOrNull() ?: backend.defaultNetplayPort,
        // Eden only: it ships one default nickname to everybody, and two players sharing one
        // cannot share a room.
        // pourquoi : docs/decisions/session.md § What each backend receives at launch
        username = NetplayNames.usernameFor(backend, profileName),
        roomName = if (role == Session.Role.HOST) NetplayNames.roomName(code) else null,
        preferredGame = if (role == Session.Role.HOST) rom?.displayName else null,
        // On PS2 the session code doubles as the room code: ARMSX2 requires one, identical on
        // both sides, and negotiates nothing.
        // pourquoi : docs/decisions/session.md § What each backend receives at launch
        password = if (backend == Backend.ARMSX2) code else null
    )
}

private const val PRESENCE_MS = 5000L

/** ~15 s of silence before concluding the room is gone rather than the network flaky. */
private const val MAX_PRESENCE_MISSES = 3

/** How many frames the pilot spends claiming the cursor, like the scaffold. */
private const val PILOT_FOCUS_FRAMES = 6
