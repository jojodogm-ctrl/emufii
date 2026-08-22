package eu.emufii.app.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import eu.emufii.app.compat.LocalCompatDb
import eu.emufii.app.compat.CompatCheck
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import eu.emufii.app.LocalEnsureVpnPermission
import eu.emufii.app.R
import eu.emufii.app.library.Backend
import eu.emufii.app.library.Rom
import eu.emufii.app.library.RomsRepository
import eu.emufii.app.network.CoordinatorClient
import eu.emufii.app.azahar.NetplayAutomation
import eu.emufii.app.azahar.PlanStore
import eu.emufii.app.network.CoordinatorError
import eu.emufii.app.network.CreatedSession
import eu.emufii.app.library.Console
import eu.emufii.app.profile.FriendStore
import eu.emufii.app.profile.ProfileStore
import eu.emufii.app.ps2.Ps2NetworkProfile
import eu.emufii.app.settings.SettingsStore
import eu.emufii.app.session.RomRef
import eu.emufii.app.session.Session
import eu.emufii.app.session.SessionCodes
import eu.emufii.app.tunnel.TunnelHolder
import eu.emufii.app.tunnel.slotIsFree
import eu.emufii.app.tunnel.tunnelHolder
import eu.emufii.app.ui.components.TunnelConflictDialog
import eu.emufii.app.ui.screens.FriendsScreen
import eu.emufii.app.ui.screens.JoinScreen
import eu.emufii.app.ui.screens.LibraryScreen
import eu.emufii.app.ui.screens.OnboardingScreen
import eu.emufii.app.ui.screens.PreparingScreen
import eu.emufii.app.ui.screens.PspOnlineScreen
import eu.emufii.app.ui.screens.SessionFinderScreen
import eu.emufii.app.ui.screens.SessionScreen
import eu.emufii.app.ui.screens.SettingsScreen
import eu.emufii.app.ui.screens.SplashScreen
import eu.emufii.app.ui.screens.WfcScreen
import eu.emufii.app.wfc.WfcManager
import eu.emufii.app.wfc.WfcState
import eu.emufii.app.wg.EmufiiWgManager
import eu.emufii.app.wg.WgState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

private sealed interface Screen {
    data object Library : Screen
    data object Finder : Screen
    data class Preparing(val label: String) : Screen
    data class Join(val rom: RomRef) : Screen
    data class InSession(val session: Session) : Screen

    /**
     * The Kaeru route. Holds a Rom rather than a Session because there is no
     * session: nothing is created, nothing is joined, no other player is
     * involved.
     */
    data class Wfc(val rom: Rom) : Screen

    /**
     * Public PSP ad hoc. Like [Wfc]: a game, no session, nobody invited, nothing
     * created. A screen rather than a card because you leave it to set PPSSPP up
     * and have to find your place again on the way back.
     */
    data class PspOnline(val rom: Rom) : Screen

    /** Profile and app settings. A place you visit, hence a screen. */
    data object ProfileAndSettings : Screen

    data object Friends : Screen
}

private fun Rom.toRef() =
    RomRef(uri = uri, displayName = displayName, console = console, titleIdHex = titleIdHex)

/**
 * The splash screen's token, at process scope.
 *
 * A `rememberSaveable` would not have been enough: the activity is recreated at
 * every language or theme change, and saved state is restored along with it, so
 * the logo would have come back. What we want to remember does not belong to the
 * screen but to the app's launch, so it lives where the app lives.
 */
private object SplashGate {
    var pending = true
}

private const val DEFAULT_PORT = 24872

/** A cold tunnel takes seconds to come up; past this something is wrong, not slow. */
private const val TUNNEL_TIMEOUT_MS = 45_000L

private const val CODE_ATTEMPTS = 5

/**
 * How often we tell the coordinator we're around while *outside* a session.
 * Its presence entries last two minutes, so this tolerates a couple of misses
 * before a friend sees us blink offline. In a session the member heartbeat
 * already reports presence, five times a minute, and this stops.
 */
private const val PRESENCE_MS = 45_000L

/**
 * Tearing a tunnel down is local work, closing a descriptor, joining a thread,
 * so it is quick or it is stuck. Waiting longer would only delay telling the
 * user something is wrong.
 */
private const val TUNNEL_RELEASE_MS = 6_000L

/**
 * Polls until the host publishes its address.
 *
 * The previous version used `return@repeat`, which only ends the current
 * iteration, so every join sat through the full ten seconds even when the
 * address was there on the first try.
 */
private suspend fun pollHostIp(client: CoordinatorClient, code: String): String? {
    repeat(20) {
        delay(500)
        client.getSession(code).getOrNull()?.hostIp?.let { return it }
    }
    return null
}

@Composable
fun EmufiiApp(settings: SettingsStore) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val client = remember { CoordinatorClient() }
    val profileStore = remember { ProfileStore(context) }
    val friendStore = remember { FriendStore(context) }
    // Handed in rather than built here: the theme is applied above this
    // composable, so it has to read the same store the settings page writes.
    val settingsStore = settings
    val romsRepo = remember { RomsRepository(context) }
    val profile by profileStore.profile.collectAsState()
    /**
     * Changing the language recreates the activity, which would drop the user
     * back on the library right after they tapped a setting. This survives that
     * recreation, so they land back where they were.
     */
    var onProfilePage by rememberSaveable { mutableStateOf(false) }
    var screen by remember {
        mutableStateOf<Screen>(if (onProfilePage) Screen.ProfileAndSettings else Screen.Library)
    }
    // Shown once, before anything else: the app is useless until it knows where
    // the ROMs are, and the notification is what keeps the network alive while
    // the player is inside the emulator.
    var onboarding by remember { mutableStateOf(!settingsStore.onboardingDone) }

    /**
     * The splash screen, once per process and never on the first launch.
     *
     * Not on the first launch, because there is nothing to load then: the ROM
     * folder has not been picked yet, and the welcome is the onboarding. Making
     * someone wait in front of a logo for a scan that will find nothing would be
     * time stolen from the very first contact with the app.
     *
     * Once per process, and not once per composition: changing the language or
     * the theme recreates the activity, and a logo that comes back at every
     * setting touched reads as a crash. That is what [SplashGate] keeps, living
     * longer than this composable.
     */
    var splashing by remember {
        mutableStateOf(SplashGate.pending && settingsStore.onboardingDone)
    }
    val ensureVpn = LocalEnsureVpnPermission.current


    /**
     * Library housekeeping is driven from the settings page but observed by the
     * library, so it is owned here, the one place both screens hang off. The
     * revision is what makes the grid rebuild: the library only ever reads the
     * repository's shared cache, and bumping this tells it the cache moved.
     */
    var libraryFolder by remember { mutableStateOf(romsRepo.savedFolderLabel()) }
    var libraryScanning by remember { mutableStateOf(false) }
    var libraryCount by remember { mutableStateOf<Int?>(null) }
    var libraryRevision by remember { mutableStateOf(0) }

    fun rescanLibrary() {
        if (libraryScanning) return
        libraryScanning = true
        scope.launch {
            // Off the main thread, always: walking a SAF tree that holds a
            // multi-GB ROM took long enough to ANR when it ran there (9e1f9fd),
            // and force = true means the cache can never shorten it.
            val roms = withContext(Dispatchers.IO) { romsRepo.scan(force = true) }
            libraryCount = roms.size
            libraryScanning = false
            libraryRevision++
        }
    }

    fun changeLibraryFolder(uri: Uri) {
        romsRepo.setFolder(uri)
        libraryFolder = romsRepo.savedFolderLabel()
        libraryCount = null
        // setFolder drops the cache, so this scan is what fills it, the library
        // would otherwise do it on its own, but then the settings page would
        // have no count to report.
        rescanLibrary()
    }

    fun fail(message: String, back: Screen = Screen.Library) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        screen = back
    }

    fun fail(message: Int, back: Screen = Screen.Library) =
        fail(context.getString(message), back)

    /**
     * The tunnel the user is about to displace, and what they were trying to do.
     * Non-null exactly while the confirmation is on screen.
     */
    var conflict by remember { mutableStateOf<Pair<TunnelHolder, () -> Unit>?>(null) }

    /**
     * Runs [proceed] once [want] can have Android's single VPN slot.
     *
     * Free slot, or one we already hold, and it runs straight away, asking
     * every time would put a dialog in front of an ordinary session start. Held
     * by the other tunnel, and it waits for an answer, because taking it ends a
     * game in progress. Nothing here relies on the system's own revocation: it
     * works, but it is silent, and the losing side finds out by having its
     * descriptor pulled.
     */
    fun withTunnelSlot(want: TunnelHolder, proceed: () -> Unit) {
        val session = EmufiiWgManager.state.value
        val wfc = WfcManager.state.value
        if (slotIsFree(session, wfc, want)) proceed()
        else conflict = tunnelHolder(session, wfc) to proceed
    }

    /** Frees the slot [held] holds, then runs [proceed]. */
    fun releaseTunnelThen(held: TunnelHolder, proceed: () -> Unit) {
        scope.launch {
            val freed = when (held) {
                TunnelHolder.WFC -> {
                    WfcManager.stop(context)
                    withTimeoutOrNull(TUNNEL_RELEASE_MS) {
                        WfcManager.state.first { it !is WfcState.Active }
                    }
                }
                // No coordinator call to make: the session code lives on the
                // session screen, and we are only here because the app came back
                // without it. The GC reaps the room on its TTL.
                TunnelHolder.SESSION -> {
                    EmufiiWgManager.stop(context)
                    withTimeoutOrNull(TUNNEL_RELEASE_MS) {
                        EmufiiWgManager.state.first { it is WgState.Idle || it is WgState.Error }
                    }
                }
                TunnelHolder.NONE -> Unit
            }
            if (freed == null) fail(R.string.tunnel_conflict_stuck) else proceed()
        }
    }

    /**
     * Waits for the tunnel, but not forever.
     *
     * Returns the online state, or null if it errored or took too long. Both
     * happen in practice, another VPN app can pre-empt ours, and a handshake
     * across a bad network simply doesn't land. Previously either case left the
     * loading screen up indefinitely.
     *
     * There is no address to wait for: the coordinator assigns it before the
     * tunnel starts, so it is known all along.
     */
    suspend fun awaitTunnel(): WgState.Online? = withTimeoutOrNull(TUNNEL_TIMEOUT_MS) {
        EmufiiWgManager.state.first { it is WgState.Error || it is WgState.Online } as? WgState.Online
    }

    fun startHostSession(rom: Rom, private: Boolean = false) = withTunnelSlot(TunnelHolder.SESSION) {
        // No screen change here on purpose. The launch card is still up and still
        // spinning, so it carries this leg itself: swapping to a full screen just
        // to show a second spinner made the card's animation look like it had
        // been cut off. The tunnel leg below still gets one, because that is the
        // leg that can actually take a while.
        scope.launch {
            // Codes are short and random, so collisions happen; the coordinator
            // rejects duplicates and a fresh draw is all it takes.
            var created: CreatedSession? = null
            var code = ""
            var lastError: Throwable? = null
            for (attempt in 1..CODE_ATTEMPTS) {
                code = SessionCodes.generate()
                val outcome = client.createSession(
                    code, rom.sessionId, rom.displayName, profile.name, profile.id,
                    // Stated out loud rather than guessed: the coordinator only
                    // sees a title and a titleId, which the 3DS and the Switch
                    // write the same way. This field is what decides whether it
                    // raises an Eden room on the VPS for this session.
                    console = rom.console.wireName,
                    private = private
                )
                created = outcome.getOrNull()
                if (created != null) break
                lastError = outcome.exceptionOrNull()
                // Only a collision is worth another draw. Retrying an
                // unreachable coordinator would just make the player wait three
                // timeouts to be told the same thing.
                val collision = lastError.let { it is CoordinatorError.Http && it.status == 409 }
                if (!collision) break
            }
            val session = created ?: return@launch fail(
                if (lastError is CoordinatorError.Unreachable) R.string.flow_coordinator_unreachable
                else R.string.flow_create_failed
            )

            ensureVpn(
                onGranted = {
                    screen = Screen.Preparing(context.getString(R.string.flow_connecting_tunnel))
                    scope.launch {
                        // Claiming the address also publishes host_ip, because we
                        // hand over the profile id and the coordinator recognises
                        // the host of the session it created.
                        val hostToken = session.token
                        val info = client.claimAddress(
                            code, EmufiiWgManager.publicKey(context), profile.name, profile.id
                        ).getOrNull() ?: run {
                            client.deleteSession(code, hostToken)
                            return@launch fail(R.string.flow_tunnel_failed)
                        }
                        // The DNS is announced for the PS2 only: it is the only
                        // console whose emulator dials a name instead of an
                        // address, for want of being able to type a dot.
                        EmufiiWgManager.start(
                            context, code, info,
                            announceDns = rom.console.backend == Backend.ARMSX2
                        )
                        if (awaitTunnel() == null) {
                            client.deleteSession(code, hostToken)
                            EmufiiWgManager.stop(context)
                            return@launch fail(R.string.flow_tunnel_failed)
                        }
                        // The target emulator's port, not a shared default:
                        // Dolphin listens on 2626 where the others listen on
                        // 24872. This is what the coordinator publishes, hence
                        // what the guest will dial; a single default sent them to
                        // a silent port, with a perfectly good address.
                        val netplayPort = rom.console.backend.defaultNetplayPort
                        client.patchSession(code, info.address, netplayPort, hostToken)
                        screen = Screen.InSession(
                            Session(
                                code = code,
                                hostIp = info.address,
                                port = netplayPort.toString(),
                                role = Session.Role.HOST,
                                rom = rom.toRef(),
                                token = hostToken,
                                // When the VPS holds a room, the host joins it
                                // like everyone else instead of carrying one:
                                // that is where their phone stops being a link
                                // in the network.
                                room = session.room
                            )
                        )
                    }
                },
                onDenied = {
                    // The session already exists, undo it rather than leave a room
                    // nobody can enter.
                    scope.launch { client.deleteSession(code, session.token) }
                    fail(R.string.flow_no_vpn_host)
                }
            )
        }
    }

    fun startJoinFlow(rom: RomRef?, code: String) {
        // The PS2's network profile gates joining too, not just hosting. The
        // launch card refuses to open a session without it; the finder, the
        // friends list and a typed code all went straight past that check, and
        // the guest landed in a tunnel whose game never opens its local menu.
        // Same refusal, same words, and said before the VPN prompt -- before
        // the tunnel slot too, so a refusal never costs a session in progress.
        //
        // Only decidable when the ROM is ours: a session for a game we do not
        // have carries no console, and that case has its own answer further
        // down.
        if (rom?.console == Console.PS2 && !Ps2NetworkProfile.isReady(context)) {
            return fail(R.string.launch_ps2_profile_missing, screen)
        }
        withTunnelSlot(TunnelHolder.SESSION) {
            screen = Screen.Preparing(context.getString(R.string.flow_finding_session))
            scope.launch {
                val back = if (rom != null) Screen.Join(rom) else Screen.Finder
                val remote = client.getSession(code).getOrElse { err ->
                    // A code that doesn't exist is the player's to fix; a
                    // coordinator that doesn't answer is ours. Saying "introuvable"
                    // for both sent people hunting for a typo in a code that was
                    // fine.
                    return@launch fail(
                        if (err is CoordinatorError.NotFound) R.string.flow_session_not_found
                        else R.string.flow_coordinator_unreachable,
                        back
                    )
                }

                // Joining a session for another game opens a tunnel that can never
                // carry a game: the two emulators would never find each other. Said
                // before the VPN prompt rather than after a silent failure in-game.
                // Only different *titles* are caught, two regional dumps of the
                // same game share a title id and are indistinguishable here.
                if (rom?.titleIdHex != null && remote.romTitleId != null &&
                    !rom.titleIdHex.equals(remote.romTitleId, ignoreCase = true)
                ) {
                    return@launch fail(
                        context.getString(
                            R.string.flow_wrong_game,
                            remote.romTitle ?: context.getString(R.string.flow_wrong_game_unnamed)
                        ),
                        back
                    )
                }

                ensureVpn(
                    onGranted = {
                        screen = Screen.Preparing(context.getString(R.string.flow_connecting_tunnel))
                        scope.launch {
                            // A session that is simply full is not a broken tunnel,
                            // and telling the player it is sends them restarting
                            // things that work. The coordinator answers 503 for it
                            // and 429 for a client that is asking too fast.
                            val info = client.claimAddress(
                                code, EmufiiWgManager.publicKey(context), profile.name, profile.id
                            ).getOrElse { err ->
                                val why = when {
                                    err is CoordinatorError.Http && err.status == 503 ->
                                        R.string.flow_session_full
                                    err is CoordinatorError.Http && err.status == 429 ->
                                        R.string.flow_too_many_requests
                                    else -> R.string.flow_tunnel_failed
                                }
                                return@launch fail(why)
                            }
                            EmufiiWgManager.start(
                                context, code, info,
                                announceDns = rom?.console?.backend == Backend.ARMSX2
                            )
                            if (awaitTunnel() == null) {
                                EmufiiWgManager.stop(context)
                                return@launch fail(R.string.flow_tunnel_failed)
                            }
                            // The host publishes its address once its own tunnel is
                            // up, which may be after we got here.
                            val hostIp = remote.hostIp ?: pollHostIp(client, code)
                                ?: run {
                                    EmufiiWgManager.stop(context)
                                    return@launch fail(R.string.flow_host_not_ready)
                                }
                            // A first heartbeat before going in: it announces the
                            // arrival, and brings back the token that will allow us
                            // to withdraw ourselves. Without it, leaving the session
                            // would rest on knowing an identifier alone, which the
                            // coordinator no longer accepts.
                            val memberToken = client.heartbeat(code, profile.id, profile.name)
                                .getOrNull()?.memberToken
                            screen = Screen.InSession(
                                Session(
                                    code = code,
                                    hostIp = hostIp,
                                    // The port published by the host is
                                    // authoritative; the fallback follows the
                                    // emulator, and DEFAULT_PORT only serves a
                                    // session whose game we do not even know.
                                    port = (
                                        remote.port
                                            ?: rom?.console?.backend?.defaultNetplayPort
                                            ?: DEFAULT_PORT
                                        ).toString(),
                                    role = Session.Role.GUEST,
                                    rom = rom,
                                    token = memberToken,
                                    room = remote.room
                                )
                            )
                        }
                    },
                    onDenied = {
                        scope.launch { client.leaveSession(code, profile.id, token = null) }
                        fail(R.string.flow_no_vpn_guest)
                    }
                )
            }
        }
    }

    /**
     * Join a session we found rather than typed: from the finder, or from a
     * friend's card. We know which game it is for, so the ROM is looked up
     * locally instead of asking the user to find it again. Not owning it is
     * fine, the session still opens, it just can't be launched from Emufii.
     */
    fun joinKnownSession(code: String, romTitleId: String?, romTitle: String? = null) {
        scope.launch {
            val rom = withContext(Dispatchers.IO) {
                val library = romsRepo.cachedOrScan()
                library.firstOrNull { r ->
                    romTitleId != null && r.sessionId.equals(romTitleId, ignoreCase = true)
                }
                // The title as a last resort: two regional dumps of the same PSP
                // game carry two disc ids, and the host published theirs.
                // Refusing on that basis would show "you do not have this game"
                // to someone who very much does.
                    ?: library.firstOrNull { r ->
                        romTitle != null && r.displayName.equals(romTitle, ignoreCase = true)
                    }
            }
            startJoinFlow(rom?.toRef(), code)
        }
    }

    /**
     * Presence, so friends holding our code can see we're around.
     *
     * Silent while in a session: the member heartbeat already reports it, and
     * says which game. Leaving one flips this back on, and its first call is
     * what clears "in a game" for everyone watching.
     */
    val inSession = screen is Screen.InSession
    LaunchedEffect(profile.id, profile.name, inSession) {
        if (inSession) return@LaunchedEffect
        while (true) {
            client.announcePresence(profile.id, profile.name, inSession = false)
            delay(PRESENCE_MS)
        }
    }

    if (onboarding) {
        // The first launch spends the token without showing the screen:
        // otherwise the logo would appear right after the onboarding, at the
        // precise moment the player is finally expecting their library.
        LaunchedEffect(Unit) { SplashGate.pending = false }
        OnboardingScreen(
            initialName = profile.name,
            onSetName = { profileStore.setName(it) },
            onPickFolder = { uri -> changeLibraryFolder(uri) },
            onSetArtworkKey = { settingsStore.setSteamGridDbKey(it) },
            onDone = {
                settingsStore.onboardingDone = true
                onboarding = false
            }
        )
        return
    }

    if (splashing) {
        // The scan happens here, not in the library: it is the whole point of
        // this screen. It fills the repository's shared cache, so the grid finds
        // it warm as it composes and never shows a loading indicator of its
        // own.
        var libraryReady by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            withContext(Dispatchers.IO) { runCatching { romsRepo.cachedOrScan() } }
            libraryReady = true
        }
        SplashScreen(
            ready = libraryReady,
            onDone = { SplashGate.pending = false; splashing = false }
        )
        return
    }

    /**
     * What "back" means, screen by screen.
     *
     * This is the gap that closed the app. Nothing intercepted back above the
     * library: on friends, sessions, settings, code entry, the DS or PSP online
     * play, a press on B, which the console delivers as a system back, travelled
     * up to the activity, which had nothing else to do but finish. Measured: from
     * the Sessions screen, `BUTTON_B` handed control back to the launcher.
     *
     * Null on the library, which is the root: there, leaving *is* the right
     * answer, and holding it back would shut the player inside the app.
     *
     * Null during preparation and in session too, but back is consumed there all
     * the same, and that distinction is the point. A preparation has no stable
     * state to return to, the tunnel being half up; leaving a session means
     * telling the coordinator and cutting the tunnel, which the screen's "Leave"
     * button already does. Letting the gesture travel up to the system, on the
     * other hand, closed the app mid-game and left behind a session nobody
     * closes. Doing nothing is the only safe behaviour at this point.
     */
    val goBack: (() -> Unit)? = when (screen) {
        Screen.Library -> null
        is Screen.Preparing -> null
        is Screen.InSession -> null
        Screen.ProfileAndSettings -> ({ onProfilePage = false; screen = Screen.Library })
        else -> ({ screen = Screen.Library })
    }
    // Live everywhere except on the library, which is the root: there, and only
    // there, the gesture belongs to the system.
    BackHandler(enabled = screen != Screen.Library) { goBack?.invoke() }

    /**
     * The compatibility ratings, read from the cache first and refreshed behind
     * it.
     *
     * The cache is read synchronously so the badges are on the first frame: a
     * warning that arrives a second after the grid has been drawn is a warning
     * the player has already scrolled past. The network call only ever replaces
     * it, and never with less than it had — see [CompatCheck].
     */
    var compat by remember { mutableStateOf(CompatCheck.cached(context)) }
    LaunchedEffect(Unit) { compat = CompatCheck.refresh(context) }

    CompositionLocalProvider(LocalCompatDb provides compat) {
    when (val s = screen) {
        Screen.Library -> LibraryScreen(
            profile = profile,
            onOpenProfile = { onProfilePage = true; screen = Screen.ProfileAndSettings },
            onOpenFriends = { screen = Screen.Friends },
            onOpenFinder = { screen = Screen.Finder },
            // DS online play shares nothing with the session flow, no code to
            // create, none to join, so both entry points lead to the same place.
            onCreate = { rom, private ->
                if (rom.console.backend == Backend.MELONDS_WFC) screen = Screen.Wfc(rom)
                else startHostSession(rom, private)
            },
            onJoinWith = { rom ->
                if (rom.console.backend == Backend.MELONDS_WFC) screen = Screen.Wfc(rom)
                else screen = Screen.Join(rom.toRef())
            },
            // The public side of PSP ad hoc: no session, no tunnel, nothing for
            // Emufii to hold, the player picks a server inside PPSSPP, which
            // keeps its own live directory of them. We open the game and get out
            // of the way. See `docs/PHASE1_SCOUT_PPSSPP_ONLINE.md`.
            onPlayPublic = { rom -> screen = Screen.PspOnline(rom) },
            onFolderPicked = { uri -> changeLibraryFolder(uri) },
            libraryRevision = libraryRevision
        )
        is Screen.PspOnline -> PspOnlineScreen(
            rom = (screen as Screen.PspOnline).rom,
            onBack = { screen = Screen.Library }
        )
        Screen.Finder -> SessionFinderScreen(
            client = client,
            romsRepo = romsRepo,
            onBack = { screen = Screen.Library },
            onJoin = { open -> joinKnownSession(open.code, open.romTitleId, open.romTitle) }
        )
        Screen.Friends -> FriendsScreen(
            profile = profile,
            friendStore = friendStore,
            client = client,
            onJoin = { code, romTitleId, romTitle -> joinKnownSession(code, romTitleId, romTitle) },
            onBack = { screen = Screen.Library }
        )
        is Screen.Preparing -> PreparingScreen(label = s.label)
        is Screen.Join -> JoinScreen(
            rom = s.rom,
            client = client,
            onBack = { screen = Screen.Library },
            onSubmitCode = { code -> startJoinFlow(s.rom, code) }
        )
        Screen.ProfileAndSettings -> SettingsScreen(
            profile = profile,
            profileStore = profileStore,
            friendStore = friendStore,
            settingsStore = settingsStore,
            libraryFolder = libraryFolder,
            libraryScanning = libraryScanning,
            libraryCount = libraryCount,
            onFolderPicked = { uri -> changeLibraryFolder(uri) },
            onRescan = { rescanLibrary() },
            onBack = {
                onProfilePage = false
                screen = Screen.Library
            }
        )
        is Screen.Wfc -> WfcScreen(
            rom = s.rom,
            onRequestTunnelSlot = { proceed -> withTunnelSlot(TunnelHolder.WFC, proceed) },
            onBack = { screen = Screen.Library }
        )
        is Screen.InSession -> SessionScreen(
            session = s.session,
            profile = profile,
            client = client,
            onSessionEnded = {
                scope.launch { EmufiiWgManager.stop(context) }
                fail(R.string.flow_host_closed)
            },
            onLeave = {
                // The plan outlives the process on purpose; it must not outlive
                // the session that justified it.
                NetplayAutomation.clear(PlanStore(context))
                scope.launch {
                    if (s.session.role == Session.Role.HOST) {
                        client.deleteSession(s.session.code, s.session.token)
                    } else {
                        // Drop out of the member list straight away, so the
                        // host sees the departure now instead of waiting for
                        // the presence TTL to lapse.
                        client.leaveSession(s.session.code, profile.id, s.session.token)
                    }
                    EmufiiWgManager.stop(context)
                }
                screen = Screen.Library
            }
        )
    }
    }

    // Over whatever screen asked: the answer decides whether that screen's
    // action happens at all, so it belongs outside the when.
    conflict?.let { (held, proceed) ->
        TunnelConflictDialog(
            held = held,
            onConfirm = {
                conflict = null
                releaseTunnelThen(held, proceed)
            },
            onDismiss = { conflict = null }
        )
    }
}
