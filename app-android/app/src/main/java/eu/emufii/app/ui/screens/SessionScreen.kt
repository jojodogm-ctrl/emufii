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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import eu.emufii.app.ui.ActionShape
import androidx.compose.foundation.layout.heightIn
import eu.emufii.app.ui.theme.PillShape
import eu.emufii.app.ui.controlRing
import eu.emufii.app.R
import eu.emufii.app.azahar.AzaharLauncher
import eu.emufii.app.azahar.LaunchResult
import eu.emufii.app.azahar.NetplayAutomation
import eu.emufii.app.azahar.NetplayPlan
import eu.emufii.app.azahar.NetplayProgress
import eu.emufii.app.azahar.PlanStore
import eu.emufii.app.dolphin.DolphinLauncher
import eu.emufii.app.dolphin.DolphinTarget
import eu.emufii.app.ps2.Ps2Launcher
import eu.emufii.app.ps2.Ps2Target
import eu.emufii.app.eden.EdenLauncher
import eu.emufii.app.netplay.NetplayNames
import eu.emufii.app.psp.HOST_SENTINEL
import eu.emufii.app.psp.PpssppLauncher
import eu.emufii.app.library.Backend
import eu.emufii.app.network.CoordinatorClient
import eu.emufii.app.network.CoordinatorError
import eu.emufii.app.network.Member
import eu.emufii.app.profile.Profile
import eu.emufii.app.profile.playerDisplayName
import eu.emufii.app.session.Session
import eu.emufii.app.ui.components.AvatarStack
import eu.emufii.app.ui.components.EmufiiScaffold
import eu.emufii.app.ui.components.GhostButton
import eu.emufii.app.ui.components.SectionHeader
import eu.emufii.app.ui.components.SoftCard
import eu.emufii.app.ui.components.softCardFill
import eu.emufii.app.ui.components.padEntry
import eu.emufii.app.ui.copyToClipboard
import eu.emufii.app.ui.theme.AccentGreen
import eu.emufii.app.ui.theme.LocalEmufiiDarkTheme
import eu.emufii.app.ui.theme.PlateDark
import eu.emufii.app.ui.theme.PlateLight
import eu.emufii.app.ui.theme.socket
import eu.emufii.app.ui.theme.plate
import eu.emufii.app.ui.theme.LocalEmufiiOledTheme
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import eu.emufii.app.ui.theme.ShellRed
import eu.emufii.app.ui.theme.edgeColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
    var status by remember { mutableStateOf<String?>(null) }
    var members by remember { mutableStateOf<List<Member>>(emptyList()) }
    // Our own name in the list for *this* session, see `Heartbeat.memberHandle`.
    // Re-read on every beat rather than kept from the first one: if the
    // coordinator let us expire and signs us up again, the handle changes too.
    var myHandle by remember { mutableStateOf<String?>(null) }

    /** Has the room step been run? Gates the launch button, see the pair below. */
    var netplayPrepared by remember(session.code) { mutableStateOf(false) }

    /**
     * Did the automation get all the way to a joined room?
     *
     * Distinct from [netplayPrepared], which only says the emulator was opened.
     * Latched rather than read straight off the progress flow: starting the game
     * disarms the plan, which resets that flow to idle, and the setup would stop
     * looking done at the very moment it starts mattering.
     */
    var netplayDone by remember(session.code) { mutableStateOf(false) }

    /**
     * PPSSPP has been opened at least once for its manual setup.
     *
     * Says nothing about the settings themselves, Emufii can neither read nor
     * write them, only that the player went there. It is a marker, not a
     * guarantee, and the button label is careful not to claim otherwise.
     */
    var pspOpened by remember(session.code) { mutableStateOf(false) }
    val netplayProgress by NetplayAutomation.progress.collectAsState()
    LaunchedEffect(netplayProgress) {
        if (netplayProgress is NetplayProgress.Done) netplayDone = true
    }

    /** The coordinator has stopped answering us. Says so; changes nothing else. */
    var offline by remember { mutableStateOf(false) }

    /**
     * Does the host's room already exist in the emulator?
     *
     * The order is not a comfort detail: a guest who sets up before the host
     * finds nothing. The room does not exist yet, the emulator answers "no
     * session", and the player concludes the game is broken when they merely
     * turned up too early. Nothing on screen stated that order, both players
     * saw the same button, ready to be pressed.
     *
     * True from the start: that is what a coordinator ignorant of the question
     * will answer, and it is also what holds for the host, who never waits on
     * anyone.
     */
    var hostReady by remember(session.code) { mutableStateOf(true) }

    /**
     * Does this session have a host step, and is it ours?
     *
     * An upstream room changes nothing here, and that was a reasoning mistake
     * fixed on 2026-08-10 after a two-player run. The first version excluded
     * sessions with an upstream room: nobody hosts there, both players join the
     * same room, so, I thought, no order to respect. The safeguard never showed
     * up, since every Switch session has one.
     *
     * What that reasoning confused: the room and the game are not the same
     * thing. The room exists as soon as the session is created, but what the
     * guest looks for in Eden is the game's LDN session, which only exists once
     * the host has opened it from their game. Arriving first means staring at
     * an empty list, exactly the original symptom.
     *
     * The order therefore holds for any backend with a room to join, with or
     * without a relay on the VPS.
     */
    val hasHostStep = session.backend.hasNetplay
    val weHostTheRoom = hasHostStep && session.role == Session.Role.HOST

    /**
     * The guest is waiting on their host: setting up would only lead to "not found".
     */
    val waitingForHost = hasHostStep && session.role == Session.Role.GUEST && !hostReady

    var automationOn by remember { mutableStateOf(azahar.isNetplayAutomationEnabled()) }

    /**
     * How many times we have come back into Emufii from outside.
     *
     * Serves as a second signal for "the host made their room": the automation
     * only completes with the accessibility service, and a host who declined it
     * sets up by hand. Without this counter their guests would wait for a
     * `Done` that never comes, and a queue with no exit is worse than no queue
     * at all.
     */
    var returns by remember(session.code) { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                automationOn = azahar.isNetplayAutomationEnabled()
                returns++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val scope = rememberCoroutineScope()

    // Publish our room once it exists. Two signals count as proof: the
    // automation having run all the way through, and, when it is declined, the
    // mere fact of having come back into Emufii after opening the emulator.
    // The second one is weaker, and that is accepted: at worst a guest sets off
    // a handful of seconds too early, which is what they did before anyway.
    LaunchedEffect(weHostTheRoom, netplayDone, returns) {
        if (!weHostTheRoom) return@LaunchedEffect
        if (netplayDone || (netplayPrepared && returns > 0)) {
            client.setHostReady(session.code, true, session.token)
        }
    }

    /**
     * Step 1, pressed. One definition for both layouts: the day one of the two
     * drifted, half the screens changed behaviour without anyone noticing.
     */
    val onNetplayStep = {
        netplayDone = false
        // Setting up again destroys the previous room: putting the guests back
        // in the waiting state beats letting them run at a room that is gone.
        if (weHostTheRoom && netplayPrepared) {
            scope.launch { client.setHostReady(session.code, false, session.token) }
        }
        status = session.prepareNetplay(context, azahar, eden, profile.name)
        if (status == null) netplayPrepared = true
    }


    // Presence: announce ourselves on a timer, and read back who else is here.
    // The same loop serves both roles, the host mostly cares about the list,
    // the guest about being counted, so there's one loop, not two.
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

            // A guest whose host closed the room used to sit on a live-looking
            // screen forever. Tolerate a couple of misses, mobile networks
            // drop requests, then say so and get out.
            //
            // Only a coordinator that *answers* 404 proves the room is gone.
            // A coordinator that says nothing proves only that we can't reach
            // it: on a Wi-Fi hiccup this used to announce "the host closed the
            // session" and tear down a tunnel whose peers were both still
            // there. Now it says what it knows, and leaves the session alone,
            // WireGuard re-handshakes by itself once the network is back.
            if (gone >= MAX_PRESENCE_MISSES && session.role == Session.Role.GUEST) {
                onSessionEnded()
                return@LaunchedEffect
            }
            if (mute >= MAX_PRESENCE_MISSES) offline = true
            delay(PRESENCE_MS)
        }
    }

    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    // Both comparisons, not one: the handle is what an up-to-date coordinator
    // returns, the friend code what the old one returned. Keeping both avoids
    // seeing yourself turn up as your own neighbour until the server is
    // deployed.
    val others = members.filter { it.id != myHandle && it.id != profile.id }

    val configuration = LocalConfiguration.current
    val landscape = configuration.screenWidthDp > configuration.screenHeightDp

    // On PSP the host's address is nobody's address on screen: the player
    // types it nowhere. What they set in PPSSPP is the sentinel, which the
    // relay translates towards the host of their session. Showing both put two
    // different addresses on the same screen, both presented as the same host's,
    // and the useless one was the one carrying the word "address".
    //
    // Hoisted here because both layouts want it: a value computed inside one of
    // the two columns would only be true on one side.
    val psp = session.backend == Backend.PPSSPP
    // With a room on the VPS the address to dial is the room's, and the host's
    // is no longer the address of anything: nobody hosts. Showing it anyway
    // would put on screen, under the word "host", an address the player must not
    // enter, and this is precisely the screen they look at when the automation
    // fails and they type by hand.
    val room = session.room
    val shownAddress = room?.host ?: if (psp) HOST_SENTINEL else session.hostIp
    val shownPort = room?.port?.toString() ?: session.port
    val addressLabel = stringResource(
        when {
            room != null -> R.string.session_room_address
            psp -> R.string.session_psp_address
            else -> R.string.session_host_address
        }
    )
    val onCopyIp = {
        copyToClipboard(context, "Emufii IP", shownAddress)
        status = context.getString(R.string.session_ip_copied)
    }
    val onCopyCode = {
        copyToClipboard(context, "Emufii", session.code)
        status = context.getString(R.string.common_copied, session.code)
    }
    val onCopyPort = {
        copyToClipboard(context, "Emufii Port", shownPort)
        status = context.getString(R.string.session_port_copied)
    }

    EmufiiScaffold(
        title = if (session.role == Session.Role.HOST) stringResource(R.string.session_mine) else stringResource(R.string.session_joined),
        modifier = modifier,
        onBack = onLeave,
        // In landscape, "leave" moves up into the header. It is an action you
        // go looking for, not one you scroll past, and the 60 dp it gave back
        // to the left pane are exactly what was missing for the address to fit
        // on screen without scrolling.
        trailing = if (landscape) {
            {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SessionCodeChip(code = session.code, onCopy = onCopyCode)
                    LeaveButton(session = session, onLeave = onLeave, fillWidth = false)
                }
            }
        } else null,
        // Both panes fit on screen: nothing rises under the header, and the
        // fade margin was an empty band between the title and the cards.
        contentScrolls = !landscape
    ) { topPadding ->
        if (landscape) {
            // Two panes. Stacked, this screen runs to eight full-width cards
            // and three 56 dp buttons in a scrolling column: on the Thor's
            // 468 dp the player never sees more than a third of their own
            // session, and the code, the one thing they read out loud to
            // someone else, leaves the screen as soon as they scroll down.
            //
            // On the left the state, which does not move: the code, who is
            // here, the address. On the right what is left to do, with its
            // buttons pinned at the bottom. The rule that cost us dearly still
            // holds, and for free this time: the answer to a tap sits under the
            // button that produced it, in a pane that does not scroll.
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        top = topPadding,
                        bottom = bottomInset + 16.dp,
                        start = 20.dp,
                        end = 20.dp
                    ),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // No `verticalScroll`: this pane has to fit. It had one, and a
                // card ended up shifted out of the pane without anything asking
                // for it, and a state pane that can hide its state is not doing
                // its job.
                //
                // It fits because the code moved up into the header: at two
                // cards they both keep their full shape, where squeezing three
                // of them ended up clipping the address.
                Column(
                    modifier = Modifier.width(272.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Presence is what gives way, never the address.
                    //
                    // The pane does not scroll and its height is the screen's:
                    // what does not fit is clipped. With no weight here the two
                    // cards were measured in order, presence took what it
                    // wanted, and the address card inherited the rest; with two
                    // players the arrivals list grows and the rest stopped being
                    // enough: the copy button labels went missing, then the port
                    // button itself was cut off.
                    //
                    // Weight reverses the measuring order. Compose measures the
                    // unweighted children first: the address therefore gets its
                    // natural height, whole, and presence makes do with what is
                    // left, scrolling inside rather than being cut. Losing sight
                    // of the third line of a player list costs nothing; losing
                    // the button that copies the port blocks setting the game
                    // up.
                    PresenceCard(
                        youName = profile.name,
                        youAvatar = profile.avatarFile,
                        others = others,
                        isHost = session.role == Session.Role.HOST,
                        live = !offline,
                        scrollable = true,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    ConnectionCard(
                        hostIp = shownAddress,
                        addressLabel = addressLabel,
                        port = if (psp) null else shownPort,
                        romName = session.rom?.displayName,
                        onCopyIp = onCopyIp,
                        onCopyPort = onCopyPort
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // What gives way when there is no room left: the
                    // explanation. Never the buttons, that being exactly the
                    // flaw this rework exists to remove.
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            // What runs past the foot of the pane fades out
                            // rather than being cut through the middle of a
                            // word. The pane's height is the screen's and its
                            // content is a paragraph: sliced, the last line read
                            // as a rendering fault, which is the same complaint
                            // the tray's half-rows earned.
                            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                            .drawWithContent {
                                drawContent()
                                drawRect(
                                    brush = Brush.verticalGradient(
                                        0.88f to Color.Black,
                                        1f to Color.Transparent
                                    ),
                                    blendMode = BlendMode.DstIn
                                )
                            }
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (offline) OfflineCard()
                        if (session.backend == Backend.PPSSPP) PspHintCard()
                        EmulatorHintCard(
                            session = session,
                            automationOn = automationOn,
                        )
                    }

                    // The "down" destination is the first button that exists
                    // and responds. It used to be "Launch", the only one every
                    // backend shows, but that one is disabled until the previous
                    // step is done, and a disabled button does not take focus:
                    // going down failed and the cursor went off into the left
                    // column.
                    if (session.backend.hasNetplay) {
                        NetplayButton(
                            session = session,
                            netplayDone = netplayDone,
                            netplayPrepared = netplayPrepared,
                            waitingForHost = waitingForHost,
                            onClick = onNetplayStep,
                            modifier = Modifier.padEntry()
                        )
                    }
                    if (session.backend == Backend.PPSSPP) {
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
                        onClick = { status = session.launch(context, azahar, eden, ppsspp) },
                        // Last resort: when no step precedes it, this is the
                        // first button on the page.
                        modifier = if (session.backend.hasNetplay ||
                                       session.backend == Backend.PPSSPP) Modifier
                                   else Modifier.padEntry()
                    )
                    status?.let { StatusLine(it) }
                }
            }
            return@EmufiiScaffold
        }

        Column(
            modifier = Modifier
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

            // The PSP instructions come before everything else, right after
            // the code. This is something to do *in another program*, once:
            // leaving it at the bottom of the column, under the button that
            // starts the game, meant showing it after the moment it was useful.
            // The other consoles keep their card at the end of the screen: they
            // have nothing to set up before playing.
            if (session.backend == Backend.PPSSPP) PspHintCard()

            // Above the member list, because that list is the thing that has
            // gone stale: whoever is shown there was here the last time we
            // heard back, not necessarily now.
            if (offline) OfflineCard()

            PresenceCard(
                youName = profile.name,
                youAvatar = profile.avatarFile,
                others = others,
                isHost = session.role == Session.Role.HOST,
                live = !offline
            )

            ConnectionCard(
                hostIp = shownAddress,
                addressLabel = addressLabel,
                // And no port: the ad hoc server's is fixed and PPSSPP does not
                // ask for it. One more field to fill in is one more field to
                // fill in wrong.
                port = if (psp) null else shownPort,
                romName = session.rom?.displayName,
                onCopyIp = onCopyIp,
                onCopyPort = onCopyPort
            )

            // Before the buttons, not after them. This card carries the one
            // thing the player has to do *by hand, in the emulator*, the
            // pseudo, which Azahar refuses the room over while blaming the
            // address, and a prerequisite printed below the button it applies
            // to is read after the mistake, if at all. Same reasoning that
            // already put the PSP card at the top.
            EmulatorHintCard(
                session = session,
                automationOn = automationOn,
            )

            // Two steps, in the order the emulator itself expects: join the room
            // from its main menu, then boot the game. One button did both, so
            // the ROM started in an emulator that had joined nothing, and the
            // player learned it from the game instead of from Emufii.
            if (session.backend.hasNetplay) {
                NetplayButton(
                    session = session,
                    netplayDone = netplayDone,
                    netplayPrepared = netplayPrepared,
                    waitingForHost = waitingForHost,
                    onClick = onNetplayStep,
                    modifier = Modifier.padEntry()
                )
            }

            // PPSSPP has no netplay to drive, no accessibility service can do
            // it, but it does have settings the player must enter themselves,
            // and they are not guessable. The button does not apply them: it
            // opens the emulator, which is all Emufii can do, and says so
            // plainly in its label rather than implying an automatic setup like
            // Azahar's.
            if (session.backend == Backend.PPSSPP) {
                PspSetupButton(
                    pspOpened = pspOpened,
                    onClick = { status = openPpssppForSetup(context, ppsspp) { pspOpened = true } },
                    modifier = if (session.backend.hasNetplay) Modifier else Modifier.padEntry()
                )
            }

            LaunchButton(
                session = session,
                netplayPrepared = netplayPrepared,
                onClick = { status = session.launch(context, azahar, eden, ppsspp) },
                modifier = if (session.backend.hasNetplay ||
                               session.backend == Backend.PPSSPP) Modifier
                           else Modifier.padEntry()
            )

            // Directly under the button that produces it, not at the far end of
            // the column: this Column scrolls, and on a landscape handheld the
            // bottom of it is off-screen. Rendered last, the reply to a tap
            // landed where the user could not see it, and a launch refused for a
            // good reason, an emulator with no multiplayer UI, say, was
            // indistinguishable from a dead button.
            status?.let { StatusLine(it) }

            LeaveButton(session = session, onLeave = onLeave)
        }
    }
}

/**
 * What the player has to set in the emulator, if anything.
 *
 * The `when` lives here rather than inline in the screen: both layouts put it
 * at the same logical place, in the walkthrough, and a single definition
 * guarantees they will not drift apart.
 */
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
        // Unreachable from the library, which routes DS straight to the Kaeru
        // screen. Handled anyway: a session joined from the finder carries
        // whatever console the host had.
        Backend.EDEN -> EdenHintCard(
            automationOn = automationOn,
            // With a room on the VPS the host joins like everyone else: telling
            // them "Create" would send them to open a second, empty room next to
            // the one where their guest is waiting.
            isHost = session.role == Session.Role.HOST && session.room == null,
            // What the player would type by hand if the automation failed: the
            // room when there is one, the host otherwise.
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
            automationOn = automationOn,
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
 * Step 1: join the room, without launching the game.
 *
 * Green once the room has actually been joined, not once the emulator has
 * merely been opened. The player comes back to this screen from Azahar and has
 * to read at a glance whether the setup took.
 */
@Composable
private fun NetplayButton(
    session: Session,
    netplayDone: Boolean,
    netplayPrepared: Boolean,
    /**
     * The host has not opened their room yet.
     *
     * The button greys out and says so, instead of sending the guest to a room
     * that does not exist; they came back from it with "no session found",
     * which reads as a breakdown when it is a matter of a minute.
     */
    waitingForHost: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val enabled = session.rom != null && !waitingForHost
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = ActionShape,
        colors = if (netplayDone) {
            ButtonDefaults.buttonColors(containerColor = AccentGreen)
        } else {
            ButtonDefaults.buttonColors()
        },
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .controlRing(ActionShape)
            // Greyed out, but still reachable with a gamepad. A disabled
            // `Button` stops being focusable, and it is the column's only stop
            // here: a waiting guest ended up on a screen where the d-pad finds
            // nothing at all, hence frozen. Focus does not promise a click will
            // land, it says where you are.
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
                // The emulator this session actually drives. It was written
                // "Azahar" in the string, so a Switch session announced the
                // wrong program by name.
                session.backend.emulatorName
            ),
            style = MaterialTheme.typography.titleMedium
        )
    }
}

/**
 * PPSSPP has no netplay to drive, no accessibility service can do it, but it
 * does have settings the player must enter themselves. The button does not
 * apply them: it opens the emulator, which Emufii can do, and says so plainly
 * rather than implying an automatic setup.
 */
@Composable
private fun PspSetupButton(
    pspOpened: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        shape = ActionShape,
        colors = if (pspOpened) {
            ButtonDefaults.buttonColors(containerColor = AccentGreen)
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

/** Step 2: launch the game, once the room has been joined. */
@Composable
private fun LaunchButton(
    session: Session,
    netplayPrepared: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        // Greyed until the room step has been through: launching first is the
        // mistake this pair exists to prevent.
        enabled = session.rom != null && session.backend != Backend.NONE &&
            (!session.backend.hasNetplay || netplayPrepared),
        shape = ActionShape,
        // Disabled, but still legibly the next step: Material's grey-on-grey
        // slab read as an absence rather than as a button waiting for step 1.
        colors = ButtonDefaults.buttonColors(
            disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
            disabledContentColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
        ),
        modifier = modifier.fillMaxWidth().height(56.dp).controlRing(ActionShape)
    ) {
        Text(
            when {
                // Joining from the finder for a game we don't own is a different
                // situation from an unsupported console, and saying the wrong
                // one sends the user looking in the wrong place.
                session.rom == null -> stringResource(R.string.session_no_rom)
                session.backend == Backend.AZAHAR ||
                    session.backend == Backend.EDEN ||
                    session.backend == Backend.PPSSPP ||
                    // The PS2 is in the set: ARMSX2's `MainActivity` is
                    // exported and takes a `content://`, so the game does launch
                    // from here. Dolphin is the exception, not it.
                    session.backend == Backend.ARMSX2 ->
                    // Numbered only where there is a step 1 above it.
                    stringResource(
                        if (session.backend.hasNetplay) R.string.session_launch_step2
                        else R.string.session_launch_emulation
                    )
                session.backend == Backend.MELONDS_WFC -> stringResource(R.string.session_wfc_not_a_session)
                // Dolphin has no step 2. The game is not launched here, it is
                // picked in the room and starts for everyone at once, and
                // Dolphin cannot be handed a game from outside anyway. Say so,
                // rather than falling through to "not supported yet", which was
                // wrong and discouraging.
                session.backend == Backend.DOLPHIN -> stringResource(R.string.session_dolphin_lobby)
                else -> stringResource(R.string.session_unsupported_short)
            },
            style = MaterialTheme.typography.titleMedium
        )
    }
}

/**
 * The session code, in the header.
 *
 * It moved up there rather than keeping its card in the pane: it is what you
 * read out loud to someone else, so it has to stay visible at all times, and at
 * three cards the pane had to squeeze them all until the address was clipped.
 * Up top it costs nobody anything and the two remaining cards keep their full
 * shape.
 *
 * The pill is the one used by the library chips, same height, same radius, same
 * shadow, so the header stays a row of floating objects. A tap copies: that is
 * the gesture you want to make when looking at a code.
 */
@Composable
private fun SessionCodeChip(code: String, onCopy: () -> Unit) {
    val dark = LocalEmufiiDarkTheme.current
    // The pill is round: the ring takes its shape, and its height is explicit
    // so the drawing covers the whole touch target, otherwise `Surface(onClick)`
    // reserves 48 dp and paints a smaller background inside it, which leaves a
    // gap between the outline and the pill.
    Surface(
        onClick = onCopy,
        shape = CircleShape,
        color = if (dark) PlateDark else PlateLight,
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
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.sp
            )
            Text(
                code.ifBlank { "—" },
                fontSize = 20.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/** The answer to a tap, in its reserved place under the buttons. */
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
    // A moulded pill, like everything else that can be pressed.
    //
    // It was bare red text floating on the tray — the one control on the screen
    // made of nothing, and it happened to be the destructive one. Red ink on a
    // plate says the same thing without pretending to be a link.
    val dark = LocalEmufiiDarkTheme.current
    val oled = LocalEmufiiOledTheme.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        modifier = (if (fillWidth) modifier.fillMaxWidth() else modifier)
            .heightIn(min = 48.dp)
            .controlRing(PillShape)
            .plate(shape = PillShape, dark = dark, oled = oled, lift = 4.dp, pressed = pressed)
            .clickable(interactionSource = interaction, indication = null, onClick = onLeave)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            if (session.role == Session.Role.HOST) stringResource(R.string.session_close)
            else stringResource(R.string.session_leave),
            style = MaterialTheme.typography.labelLarge,
            color = ShellRed
        )
    }
}

/**
 * A tick, drawn rather than imported.
 *
 * Two strokes are cheaper than pulling the whole material-icons artifact in for
 * one glyph, and unlike a "✓" character it sits exactly where it is put, text
 * glyphs are centred on their line box, not on their ink.
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
                fontWeight = FontWeight.Black,
                letterSpacing = 4.sp,
                color = MaterialTheme.colorScheme.onSurface
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

/**
 * Who is in the room, live.
 *
 * This is the whole point of the presence loop: hosting used to be a screen
 * with a code on it and no way to tell whether anyone had turned up.
 */
@Composable
private fun PresenceCard(
    youName: String,
    youAvatar: java.io.File?,
    others: List<Member>,
    isHost: Boolean,
    live: Boolean,
    /**
     * True in the pane, whose height is fixed and where this card is the one
     * that gives way. False elsewhere, and that is not a matter of taste: the
     * single-column page already scrolls, so it measures its children at
     * infinite height, and Compose refuses, by throwing, to measure scrolling
     * content under an unbounded constraint.
     */
    scrollable: Boolean = false,
    modifier: Modifier = Modifier
) {
    val scroll = rememberScrollState()
    // A line cut in half reads as a rendering glitch; the same line fading into
    // the card's background reads as "there is more". The gradient only lights
    // up when something is left below the fold.
    val fill = softCardFill()
    val fade = scrollable && scroll.canScrollForward

    SoftCard(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // Before the scroll, and the order is the whole point. Placed
                // after, this drawing works in the coordinates of the *unrolled*
                // content: `size.height` there is the full height of the text and
                // the gradient would land below the fold, invisible. Placed
                // before, it wraps the scrolling node, so it measures the window
                // and the fade stays stuck to the bottom of the card.
                .then(
                    if (!fade) Modifier else Modifier.drawWithContent {
                        drawContent()
                        val h = FADE_HEIGHT.toPx()
                        drawRect(
                            // Opaque before reaching the edge, not only at the
                            // edge. A linear gradient running to the bottom left
                            // the top of the last line under 40 % coverage, so
                            // legible and sliced, measured. The colour is solid
                            // over the last third, and that is where the cut
                            // falls.
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
                // Nothing is live while we're not hearing back: the dot would
                // be vouching for a list we can no longer refresh.
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

            // Announce arrivals rather than just growing the row silently.
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
 * Enough to erase a whole line of text, not just bite into it.
 *
 * Measured: at 28 dp the cut line stayed half legible, the top of the gradient
 * being nearly transparent there, so the text could still be seen getting
 * sliced. It has to cover the line and its leading.
 */
private val FADE_HEIGHT = 44.dp

/** Slow pulse, something is live without being a spinner demanding attention. */
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
                .background(AccentGreen)
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
    onCopyIp: () -> Unit,
    onCopyPort: () -> Unit,
    /**
     * False in the pane, where the forty dp of the "Game" block are exactly what
     * clipped the copy buttons. The game name is not a state you act on, the
     * player has just launched it, and the other pane already talks about its
     * emulator, whereas the address does get copied out.
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
            // Equal widths and equal heights: without the weight the port pill
            // was wider than the IP one, and without the shared intrinsic height
            // whichever wrapped to two lines overhung its neighbour. Two actions
            // of the same rank must be the same size.
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.height(IntrinsicSize.Min)
            ) {
                GhostButton(
                    label = stringResource(R.string.session_copy_ip),
                    onClick = onCopyIp,
                    fillWidth = true,
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
                if (port != null) {
                    GhostButton(
                        label = stringResource(R.string.session_copy_port),
                        onClick = onCopyPort,
                        fillWidth = true,
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                }
            }
        }
    }
}

/**
 * A line the player cannot afford to skim, inside a card of lines they can.
 *
 * Deliberately not a `SectionHeader` plus body: the surrounding card is all
 * quiet grey advisory text, and one more paragraph in that voice reads as more
 * of the same. The tinted block and the label are what make the eye stop.
 */
@Composable
private fun ImportantNote(text: String) {
    // A recessed note, not a red slab with a shouted label on it.
    //
    // It was `errorContainer` filled at 55 %, topped by "IMPORTANT" in tracked
    // capitals. Two faults, and the second is the expensive one: the eyebrow is
    // the device the craft floor bans outright, and a saturated red field the
    // size of a paragraph took the whole pane's attention away from the two
    // buttons that are the actual job. Red is this app's danger colour and it
    // appears twice in the whole product — spending it on an advisory is what
    // makes it stop meaning anything when something is really wrong.
    //
    // So: the tray's own recess, the app's ordinary ink, and one red bar down
    // the reading edge to say "this one matters". The words were always doing
    // the work; they just could not be heard over the colour.
    val dark = LocalEmufiiDarkTheme.current
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .socket(shape, dark)
            .padding(start = 12.dp, top = 12.dp, end = 14.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .heightIn(min = 20.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(2.dp))
                .background(ShellRed)
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
            // Loud, not a footnote. This is the one thing the player must do by
            // hand before anything else works, and getting it wrong produces a
            // message that accuses the address, so a player who reads Azahar's
            // own error is sent looking in exactly the wrong place. It stays on
            // both paths: the pseudo is Azahar's whether Emufii fills the form
            // or the player does.
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
 * Eden's multiplayer is not in a game drawer but in the app's own settings, so
 * the card says where to go, and, when the autofill is on, that there is
 * nothing left to type once they get there.
 *
 * The host is told to Create and the guest to Join, because unlike Azahar this
 * screen can be opened before a game is even running: telling both the same
 * thing would put two players on the same side of the room.
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
            // As loud as Azahar's nickname, and for the same reason: it is a
            // prerequisite the emulator does not mention. A differing game
            // version lets the room form, then the game never starts, and
            // nothing on screen points at the cause.
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
 * Dolphin, whose flow is one step where the others are two.
 *
 * The game is not launched from here and then joined: it is picked in the
 * lobby, by the host, once the room is up, Dolphin cannot be told to boot a
 * file from outside anyway. So the card never says "now start your game", and
 * it does say the one prerequisite the emulator will not mention until it is
 * too late: both sides need the same dump, byte for byte. Netplay hashes it and
 * refuses quietly otherwise.
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
            // As loud as the dump, and more treacherous: the dump, Dolphin
            // checks and refuses. The save, nobody checks. Two players starting
            // from different states join the room, start the game, and each see
            // a match that does not exist on the other side, without a message
            // anywhere. Measured on Brawl on 2026-08-16: one was at the "create
            // a save" menu, the other had already passed it, and it took an
            // evening to get there.
            //
            // Desktop Dolphin has "Sync Save Data", which pushes the host's save
            // and makes all of this invisible. This Android build does not
            // expose it, and Emufii cannot take it on: the saves live in
            // Dolphin's private storage. So we warn, for want of being able to
            // act.
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
 * The PS2 card, and the one thing it has to prevent: confusion.
 *
 * ARMSX2 can do two unrelated kinds of multiplayer. The local mode (Local Link)
 * is the one Emufii serves, for the sixty-odd games shipped with a LAN or
 * System Link mode. The online mode goes through a revival server, over plain
 * DNS, with no session and no tunnel, Emufii is of no use there, and implying
 * otherwise would produce exactly the wrong expectation. Hence the warning at
 * the top of the card, before anything else.
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
            // Said by ARMSX2 itself, and it is not guessable: with the network
            // adapter attached, some games stop responding to the pad. Without
            // this line, that reads as a frozen app.
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
 * The coordinator has gone quiet on us.
 *
 * Deliberately not an error: the tunnel is a direct WireGuard peering that
 * doesn't need the coordinator once it's up, so a game already running keeps
 * running. Only the list of who's here stops being trustworthy.
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
 * The four PSP settings, with the address already on the clipboard.
 *
 * Copied on display rather than on tap: the player is about to leave Emufii for
 * PPSSPP, and having to come back and press a button they did not see before
 * leaving is exactly the kind of round trip this card exists to avoid. The
 * button stays, for whoever comes back later.
 */
@Composable
private fun PspHintCard() {
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        copyToClipboard(context, "Emufii", HOST_SENTINEL)
        copied = true
    }
    SoftCard {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SectionHeader(stringResource(R.string.hint_psp_title))
            Text(
                stringResource(R.string.hint_psp_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                HOST_SENTINEL,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
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
                    color = AccentGreen
                )
            }
            GhostButton(
                label = stringResource(R.string.hint_psp_copy),
                onClick = { copyToClipboard(context, "Emufii", HOST_SENTINEL) }
            )

            // At the end of the card, not in the middle: it is the longest
            // paragraph on the screen, and slotted between the address and its
            // "Copy" button it separated the gesture from what it acts on. The
            // one comfort factor measured with two players can wait until the
            // end, it blocks nothing, it degrades.
            //
            // Deliberately not an `ImportantNote`: that red block is reserved
            // for what stops you playing, the nickname Azahar refuses, and using
            // it for a comfort tip would wear the signal out. But not the grey
            // voice of the notes above either, where it would read as filler.
            // Hence a heading, and a full-strength body.
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

/**
 * "Toi, Bibi et Théo" in French, "You, Bibi and Théo" in English.
 *
 * Hand-rolling this was fine while the app spoke one language; the conjunction
 * and the comma placement differ per locale, so ICU does it instead of us.
 */
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

/**
 * Opens PPSSPP so the player can enter their settings there, and returns the
 * message to show, null when everything went fine.
 *
 * Kept apart from [Session.launch] because it is not a step of it: no game
 * starts, and the reverse order (launch then set up) works just as badly.
 */
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

private fun Session.launch(
    context: android.content.Context,
    azahar: AzaharLauncher,
    eden: EdenLauncher,
    ppsspp: PpssppLauncher
): String {
    val rom = this.rom ?: return context.getString(R.string.session_no_rom_attached)
    // Step two runs after the room has been joined, so the plan has done its
    // work, and an armed plan that stays armed is what made the automation
    // fight the player for the in-game drawer. Disarming here is the one moment
    // we know for certain it is spent.
    if (backend.hasNetplay) NetplayAutomation.clear(PlanStore(context))
    val (result, emulator) = when (backend) {
        Backend.AZAHAR -> azahar.launchGame(rom.uri, plan = null) to "Azahar"
        Backend.EDEN -> eden.launchGame(
            rom.uri,
            plan = null,
            automationOn = azahar.isNetplayAutomationEnabled()
        ) to "Eden"
        Backend.PPSSPP -> ppsspp.launchGame(rom.uri) to "PPSSPP"
        // Not a launch: a return. The game is picked and started in the Dolphin
        // room, not here, and Dolphin cannot be handed a game from outside
        // anyway. What this button has to do is bring the player back to where
        // the game is waiting for them, after a round trip through Emufii.
        //
        // The launch intent resumes the existing task instead of opening a fresh
        // one: the room is still on screen behind, and we land straight back on
        // it. If Dolphin has been killed in the meantime we land on its game
        // grid, which is the best that can be done, `NetplayActivity` is not
        // exported and cannot be targeted.
        //
        // And above all: no armed plan. The room is already open; re-arming
        // would send the driver to fill the form in again over a running game.
        Backend.DOLPHIN -> {
            val result = DolphinLauncher(context).launch()
            return if (result == LaunchResult.Success) {
                context.getString(R.string.session_dolphin_lobby_opened)
            } else {
                context.getString(R.string.err_not_installed, "Dolphin")
            }
        }
        // A real launch, unlike Dolphin: ARMSX2's `MainActivity` is exported
        // with a VIEW filter on `content`, so the SAF ROM travels with the
        // intent. No armed plan for all that, the network was set at step one,
        // and re-arming would send the driver to fill the form in again over a
        // running game.
        Backend.ARMSX2 -> Ps2Launcher(context).launchGame(rom.uri) to "ARMSX2"
        Backend.MELONDS_WFC ->
            return context.getString(R.string.session_wfc_launch_from_library)
        Backend.NONE -> return context.getString(R.string.session_unsupported_console)
    }
    return when (result) {
        LaunchResult.Success -> context.getString(R.string.session_launching, rom.displayName)
        LaunchResult.NotInstalled -> context.getString(R.string.err_not_installed, emulator)
        is LaunchResult.NoNetplayUi -> context.getString(
            R.string.err_no_netplay_ui,
            emulator,
            result.versionName ?: "?"
        )
        is LaunchResult.Error -> context.getString(R.string.err_generic, result.message)
    }
}

/**
 * Step one: open the emulator on its multiplayer screen with the plan armed.
 *
 * Returns null when it worked, the caller turns that into "the launch button is
 * now live", and a message otherwise.
 */
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
        // Dolphin has no multiplayer-less version to detect: netplay arrived in
        // the same screen as everything else, and a build that is too old simply
        // lacks the menu entry, at which point the driver stops and the card
        // says what to type.
        Backend.DOLPHIN -> DolphinLauncher(context).openForNetplay(
            plan,
            automationOn = azahar.isNetplayAutomationEnabled()
        ) to "Dolphin"
        // The PS2, whose driver goes and sets ARMSX2's Network screen. The game
        // is not passed here: Local Link is set in the app's settings, and the
        // DEV9 adapter initialises when the game boots, so a port set afterwards
        // would not be read back. The game goes at step two.
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
 * Both roles point Azahar at the host's tunnel address: the guest to reach the
 * host, the host because `netPlayCreateRoom` binds and self-joins on the same
 * address (see PHASE0_AZAHAR.md), its own tunnel IP is the one value that works
 * for both.
 */
internal fun Session.netplayPlan(profileName: String?): NetplayPlan? {
    // With a room on the VPS nobody hosts: both players join it, and the host
    // stops being a link in the network. That is the whole point of the work,
    // the game no longer depends on a phone being reachable, and the tunnel does
    // not even have to be up in order to dial.
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
        // The session's port when it carries one, otherwise the target
        // emulator's, 2626 for Dolphin, 24872 for the others. A shared default
        // would send the Dolphin guest to a silent port.
        port = port.toIntOrNull() ?: backend.defaultNetplayPort,
        // The nickname, on Eden only, and for both roles: two players with the
        // same nickname cannot share a room, and Eden ships the same one to
        // everybody by default, so two Emufii players would turn up there as the
        // same person. Azahar keeps its own: Emufii used to write the profile
        // name in, which replaced a valid nickname with a two-letter one the
        // form refused, with a message that blamed the address. The help card
        // says where to change it.
        username = NetplayNames.usernameFor(backend, profileName),
        roomName = if (role == Session.Role.HOST) NetplayNames.roomName(code) else null,
        preferredGame = if (role == Session.Role.HOST) rom?.displayName else null,
        // The session code doubles as the room code on PS2: ARMSX2 requires
        // one, identical on both sides, and it negotiates nothing. It is the
        // secret both players already share. Useless elsewhere, the other
        // emulators have no field to put it in, except the VPS rooms above,
        // which carry their own.
        password = if (backend == Backend.ARMSX2) code else null
    )
}

private const val PRESENCE_MS = 5000L

/** ~15 s of silence before concluding the room is gone rather than the network flaky. */
private const val MAX_PRESENCE_MISSES = 3
