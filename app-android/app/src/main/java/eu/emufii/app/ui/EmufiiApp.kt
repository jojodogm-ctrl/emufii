package eu.emufii.app.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.emufii.app.LocalEnsureVpnPermission
import eu.emufii.app.R
import eu.emufii.app.artwork.ArtworkPreload
import eu.emufii.app.azahar.NetplayAutomation
import eu.emufii.app.azahar.PlanStore
import eu.emufii.app.compat.CompatCheck
import eu.emufii.app.compat.LocalCompatDb
import eu.emufii.app.library.Backend
import eu.emufii.app.library.Console
import eu.emufii.app.library.GameTitles
import eu.emufii.app.library.Rom
import eu.emufii.app.library.RomsRepository
import eu.emufii.app.library.allEmulators
import eu.emufii.app.meta.LocalGameMetaDb
import eu.emufii.app.meta.MetaCheck
import eu.emufii.app.network.CoordinatorClient
import eu.emufii.app.network.CoordinatorError
import eu.emufii.app.network.CreatedSession
import eu.emufii.app.notify.AppForeground
import eu.emufii.app.notify.FriendEvent
import eu.emufii.app.notify.FriendWatchJob
import eu.emufii.app.notify.FriendWatcher
import eu.emufii.app.notify.Notifications
import eu.emufii.app.profile.Friend
import eu.emufii.app.profile.FriendStore
import eu.emufii.app.profile.Profile
import eu.emufii.app.profile.ProfileStore
import eu.emufii.app.ps2.Ps2NetworkProfile
import eu.emufii.app.secondscreen.PadLegendBar
import eu.emufii.app.secondscreen.PanelFeed
import eu.emufii.app.secondscreen.PanelFriend
import eu.emufii.app.secondscreen.SecondScreen
import eu.emufii.app.secondscreen.SecondScreenModel
import eu.emufii.app.secondscreen.rememberPresentationDisplay
import eu.emufii.app.session.RomRef
import eu.emufii.app.session.Session
import eu.emufii.app.session.SessionCodes
import eu.emufii.app.settings.SettingsStore
import eu.emufii.app.tunnel.TunnelHolder
import eu.emufii.app.tunnel.slotIsFree
import eu.emufii.app.tunnel.tunnelHolder
import eu.emufii.app.ui.components.FriendAlert
import eu.emufii.app.ui.components.TunnelConflictDialog
import eu.emufii.app.ui.screens.FriendsScreen
import eu.emufii.app.ui.screens.JoinScreen
import eu.emufii.app.ui.screens.LibraryScreen
import eu.emufii.app.ui.screens.OnboardingScreen
import eu.emufii.app.ui.screens.PreparingScreen
import eu.emufii.app.ui.screens.PspOnlineScreen
import eu.emufii.app.ui.screens.SessionFinderScreen
import eu.emufii.app.ui.screens.SessionScreen
import eu.emufii.app.ui.screens.SplashScreen
import eu.emufii.app.ui.screens.WfcScreen
import eu.emufii.app.ui.screens.settings.SettingsScreen
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
     * A Rom rather than a Session: there is no session.
     * pourquoi : docs/decisions/lancement-et-navigation.md § Two routes that are not sessions
     */
    data class Wfc(val rom: Rom) : Screen

    /**
     * A screen rather than a card: you leave it to set PPSSPP up and must find your
     * place again.
     * pourquoi : docs/decisions/lancement-et-navigation.md § Two routes that are not sessions
     */
    data class PspOnline(val rom: Rom) : Screen

    data object ProfileAndSettings : Screen

    data object Friends : Screen
}

private fun Rom.toRef() =
    RomRef(
        uri = uri,
        displayName = displayName,
        console = console,
        titleIdHex = titleIdHex,
        filename = filename,
        productCode = productCode,
        ps2ElfCrc = ps2ElfCrc,
    )

/**
 * At process scope: a `rememberSaveable` comes back with the activity.
 * pourquoi : docs/decisions/lancement-et-navigation.md § The preload runs, and the app composes behind it
 */
internal object SplashGate {
    var pending by mutableStateOf(true)
    var sessionAlive = false

    fun rearm() {
        if (!sessionAlive) pending = true
    }

}

private const val DEFAULT_PORT = 24872

/** A cold tunnel takes seconds to come up; past this something is wrong, not slow. */
private const val TUNNEL_TIMEOUT_MS = 45_000L

private const val CODE_ATTEMPTS = 5

/**
 * Outside a session only; inside one the member heartbeat reports for us.
 * pourquoi : docs/decisions/lancement-et-navigation.md § What is hoisted to app level, and why
 */
private const val PRESENCE_MS = 45_000L

/**
 * Local work: it is quick, or it is stuck.
 * pourquoi : docs/decisions/lancement-et-navigation.md § Android's single VPN slot
 */
private const val TUNNEL_RELEASE_MS = 6_000L

/**
 * Never `return@repeat` here: it ends the iteration, not the loop.
 * pourquoi : docs/decisions/lancement-et-navigation.md § Android's single VPN slot
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
    SilenceSystemSfx()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val client = remember { CoordinatorClient() }
    val profileStore = remember { ProfileStore(context) }
    val friendStore = remember { FriendStore.get(context) }
    // Handed in: the theme is applied above this composable and must read the same
    // store.
    val settingsStore = settings
    val romsRepo = remember { RomsRepository(context) }
    val profile by profileStore.profile.collectAsStateWithLifecycle()
    /**
     * Survives the activity recreation a language change causes.
     * pourquoi : docs/decisions/lancement-et-navigation.md § The logo, once per process and never on first launch
     */
    var onProfilePage by rememberSaveable { mutableStateOf(false) }
    var screen by remember {
        mutableStateOf<Screen>(if (onProfilePage) Screen.ProfileAndSettings else Screen.Library)
    }
    // The gate must not re-arm while a session lives, or returning lands on the logo.
    SideEffect {
        SplashGate.sessionAlive =
            screen is Screen.InSession || screen is Screen.Preparing
    }
    // Derived from `screen`, never pushed from a call site, so the panel cannot
    // disagree.
    // pourquoi : docs/decisions/lancement-et-navigation.md § What the second screen receives
    DisposableEffect(Unit) { onDispose { SecondScreen.clear() } }

    var onboarding by remember { mutableStateOf(!settingsStore.onboardingDone) }

    val splashing = SplashGate.pending && settingsStore.onboardingDone


    val ensureVpn = LocalEnsureVpnPermission.current


    /**
     * The one place both screens hang off; the revision tells the grid the cache
     * changed.
     * pourquoi : docs/decisions/lancement-et-navigation.md § What is hoisted to app level, and why
     */
    var libraryFolder by remember { mutableStateOf(romsRepo.savedFolderLabel()) }
    var librarySecondFolder by remember { mutableStateOf(romsRepo.secondFolderLabel()) }
    var libraryScanning by remember { mutableStateOf(false) }
    var libraryCount by remember { mutableStateOf<Int?>(null) }
    var libraryRevision by remember { mutableStateOf(0) }

    fun rescanLibrary() {
        if (libraryScanning) return
        libraryScanning = true
        scope.launch {
            // Always off the main thread: walking a SAF tree over a multi-GB ROM has
            // ANR'd (9e1f9fd).
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
        // setFolder drops the cache and this refills it, so the settings page does not
        // sit empty.
        rescanLibrary()
    }

    /** The second folder adds to the first; a refusal means they were the same. */
    fun changeSecondLibraryFolder(uri: Uri) {
        if (!romsRepo.setSecondFolder(uri)) return
        librarySecondFolder = romsRepo.secondFolderLabel()
        libraryCount = null
        rescanLibrary()
    }

    fun removeSecondLibraryFolder() {
        romsRepo.clearSecondFolder()
        librarySecondFolder = null
        libraryCount = null
        rescanLibrary()
    }

    /**
     * Giving up increments it, orphaning any attempt still in flight.
     * pourquoi : docs/decisions/lancement-et-navigation.md § An in-flight attempt must not teleport somebody who has given up
     */
    var prepEpoch by remember { mutableIntStateOf(0) }

    fun fail(message: String, back: Screen = Screen.Library) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        screen = back
    }

    fun fail(message: Int, back: Screen = Screen.Library) =
        fail(context.getString(message), back)

    var conflict by remember { mutableStateOf<Pair<TunnelHolder, () -> Unit>?>(null) }

    /**
     * Nothing here relies on the system's own revocation.
     * pourquoi : docs/decisions/lancement-et-navigation.md § Android's single VPN slot
     */
    fun withTunnelSlot(want: TunnelHolder, proceed: () -> Unit) {
        val session = EmufiiWgManager.state.value
        val wfc = WfcManager.state.value
        if (slotIsFree(session, wfc, want)) proceed()
        else conflict = tunnelHolder(session, wfc) to proceed
    }

    fun releaseTunnelThen(held: TunnelHolder, proceed: () -> Unit) {
        scope.launch {
            val freed = when (held) {
                TunnelHolder.WFC -> {
                    WfcManager.stop(context)
                    withTimeoutOrNull(TUNNEL_RELEASE_MS) {
                        WfcManager.state.first { it !is WfcState.Active }
                    }
                }
                // No coordinator call: we are here because the app came back without
                // the code.
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
     * Null if it errored or took too long.
     * pourquoi : docs/decisions/lancement-et-navigation.md § Android's single VPN slot
     */
    suspend fun awaitTunnel(): WgState.Online? = withTimeoutOrNull(TUNNEL_TIMEOUT_MS) {
        EmufiiWgManager.state.first { it is WgState.Error || it is WgState.Online } as? WgState.Online
    }

    fun startHostSession(rom: Rom, private: Boolean = false) = withTunnelSlot(TunnelHolder.SESSION) {
        // No screen change: the launch card is still spinning and carries this leg.
        // pourquoi : docs/decisions/lancement-et-navigation.md § Android's single VPN slot
        scope.launch {
            // Codes are short and random: the coordinator rejects duplicates and a
            // fresh draw fixes it.
            var created: CreatedSession? = null
            var code = ""
            var lastError: Throwable? = null
            for (attempt in 1..CODE_ATTEMPTS) {
                code = SessionCodes.generate()
                val outcome = client.createSession(
                    code, rom.sessionId, rom.displayName, profile.name, profile.id,
                    // Stated, never guessed: 3DS and Switch write titleId alike, and
                    // this decides the VPS room.
                    console = rom.console.wireName,
                    private = private
                )
                created = outcome.getOrNull()
                if (created != null) break
                lastError = outcome.exceptionOrNull()
                // Only a collision is worth another draw; an unreachable coordinator
                // costs three timeouts.
                val collision = lastError.let { it is CoordinatorError.Http && it.status == 409 }
                if (!collision) break
            }
            val session = created ?: return@launch fail(
                if (lastError is CoordinatorError.Unreachable) R.string.flow_coordinator_unreachable
                else R.string.flow_create_failed
            )

            ensureVpn(
                onGranted = {
                    val epoch = prepEpoch
                    screen = Screen.Preparing(context.getString(R.string.flow_connecting_tunnel))
                    scope.launch {
                        // Claiming the address publishes host_ip: the profile id
                        // identifies the host.
                        val hostToken = session.token
                        val info = client.claimAddress(
                            code, EmufiiWgManager.publicKey(context), profile.name, profile.id
                        ).getOrNull() ?: run {
                            client.deleteSession(code, hostToken)
                            return@launch fail(R.string.flow_tunnel_failed)
                        }
                        // PS2 only: its keyboard has no dot key, so it dials a name.
                        EmufiiWgManager.start(
                            context, code, info,
                            announceDns = rom.console.backend == Backend.ARMSX2
                        )
                        if (awaitTunnel() == null) {
                            client.deleteSession(code, hostToken)
                            EmufiiWgManager.stop(context)
                            return@launch fail(R.string.flow_tunnel_failed)
                        }
                        // The target emulator's port: Dolphin listens on 2626, the
                        // others on 24872.
                        // pourquoi : docs/decisions/lancement-et-navigation.md § Android's single VPN slot
                        val netplayPort = rom.console.backend.defaultNetplayPort
                        client.patchSession(code, info.address, netplayPort, hostToken)
                        if (prepEpoch != epoch) return@launch
                        screen = Screen.InSession(
                            Session(
                                code = code,
                                hostIp = info.address,
                                port = netplayPort.toString(),
                                role = Session.Role.HOST,
                                rom = rom.toRef(),
                                token = hostToken,
                                // With a VPS room the host joins like everyone else.
                                room = session.room
                            )
                        )
                    }
                },
                onDenied = {
                    scope.launch { client.deleteSession(code, session.token) }
                    fail(R.string.flow_no_vpn_host)
                }
            )
        }
    }

    fun startJoinFlow(rom: RomRef?, code: String) {
        // Gates joining as well as hosting, and is said before the VPN prompt.
        // pourquoi : docs/decisions/lancement-et-navigation.md § Refusals are said before the VPN prompt
        if (rom?.console == Console.PS2 && !Ps2NetworkProfile.isReady(context)) {
            return fail(R.string.launch_ps2_profile_missing, screen)
        }
        withTunnelSlot(TunnelHolder.SESSION) {
            screen = Screen.Preparing(context.getString(R.string.flow_finding_session))
            scope.launch {
                val back = if (rom != null) Screen.Join(rom) else Screen.Finder
                val remote = client.getSession(code).getOrElse { err ->
                    // A missing code is the player's to fix, a silent coordinator ours:
                    // one message misled both.
                    return@launch fail(
                        if (err is CoordinatorError.NotFound) R.string.flow_session_not_found
                        else R.string.flow_coordinator_unreachable,
                        back
                    )
                }

                // Only different titles are caught: two regional dumps share a title
                // id.
                // pourquoi : docs/decisions/lancement-et-navigation.md § Refusals are said before the VPN prompt
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
                        val epoch = prepEpoch
                        screen = Screen.Preparing(context.getString(R.string.flow_connecting_tunnel))
                        scope.launch {
                            // 503 is full, 429 is asking too fast.
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
                            // The host publishes its address once its tunnel is up,
                            // possibly after we got here.
                            val hostIp = remote.hostIp ?: pollHostIp(client, code)
                                ?: run {
                                    EmufiiWgManager.stop(context)
                                    return@launch fail(R.string.flow_host_not_ready)
                                }
                            // It brings back the token that lets us withdraw ourselves
                            // later.
                            // pourquoi : docs/decisions/lancement-et-navigation.md § Android's single VPN slot
                            val memberToken = client.heartbeat(code, profile.id, profile.name)
                                .getOrNull()?.memberToken
                            if (prepEpoch != epoch) return@launch
                            screen = Screen.InSession(
                                Session(
                                    code = code,
                                    hostIp = hostIp,
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

    /** Not owning the ROM is fine: it just cannot launch. */
    fun joinKnownSession(code: String, romTitleId: String?, romTitle: String? = null) {
        scope.launch {
            val rom = withContext(Dispatchers.IO) {
                val library = romsRepo.cachedOrScan()
                library.firstOrNull { r ->
                    romTitleId != null && r.sessionId.equals(romTitleId, ignoreCase = true)
                }
                // Title as a last resort: two regional PSP dumps carry two disc ids.
                    ?: library.firstOrNull { r ->
                        romTitle != null && r.displayName.equals(romTitle, ignoreCase = true)
                    }
            }
            startJoinFlow(rom?.toRef(), code)
        }
    }

    /**
     * Silent while in a session; its first call on leaving one reports again.
     * pourquoi : docs/decisions/lancement-et-navigation.md § What is hoisted to app level, and why
     */
    val inSession = screen is Screen.InSession
    LaunchedEffect(profile.id, profile.name, inSession) {
        if (inSession) return@LaunchedEffect
        while (true) {
            client.announcePresence(profile.id, profile.name, inSession = false)
            delay(PRESENCE_MS)
        }
    }

    /**
     * Asked once for the whole app: presence is not the friends screen's business.
     * pourquoi : docs/decisions/lancement-et-navigation.md § What is hoisted to app level, and why
     */
    val friends by friendStore.friends.collectAsStateWithLifecycle()
    val watcher = remember { FriendWatcher(context, client) }
    val friendStatuses by watcher.statuses.collectAsStateWithLifecycle()
    val friendCodes = friends.map { it.code }
    LaunchedEffect(friendCodes) { watcher.run(friendCodes) }

    // Resolved here, on the side that speaks the interface language.
    // pourquoi : docs/decisions/second-ecran.md § The friends list goes to the back, both cards stay in front
    val friendPlayingUnknown = stringResource(R.string.friends_playing_unknown)
    // Resolved here too: `playerDisplayName` is composable and an effect is not a
    // composition.
    val friendUnnamed = stringResource(R.string.profile_default_name)
    val friendOnline = stringResource(R.string.friends_online)
    val friendOffline = stringResource(R.string.friends_offline)

    LaunchedEffect(screen, friends, friendStatuses) {
        if (screen is Screen.Friends) {
            SecondScreen.publish(
                SecondScreenModel.Friends(
                    entries = friends
                        .sortedWith(
                            compareByDescending<Friend> {
                                friendStatuses[it.code]?.inSession == true
                            }
                                .thenByDescending { friendStatuses[it.code]?.online == true }
                                .thenBy { (it.name ?: it.displayCode).lowercase() }
                        )
                        .map { friend ->
                            val status = friendStatuses[friend.code]
                            PanelFriend(
                                name = friend.name?.takeIf { it.isNotBlank() }
                                    ?.takeIf { it != Profile.DEFAULT_NAME }
                                    ?: friend.displayCode.ifBlank { friendUnnamed },
                                line = when {
                                    status?.inSession == true ->
                                        status.romTitle ?: friendPlayingUnknown
                                    status?.online == true -> friendOnline
                                    else -> friendOffline
                                },
                                online = status?.online == true,
                                inSession = status?.inSession == true,
                                onRemove = { friendStore.remove(friend.code) },
                            )
                        }
                )
            )
            return@LaunchedEffect
        }
        SecondScreen.publish(
            (screen as? Screen.InSession)?.session?.let { active ->
                SecondScreenModel.InSession(
                    code = active.code,
                    role = active.role,
                    console = active.console,
                    gameTitle = active.rom?.displayName,
                    // The same values as the front screen, by the same definition.
                    // pourquoi : docs/decisions/session.md § What the rear panel carries, the front screen does not repeat
                    hostAddress = active.shownAddress,
                    port = active.shownPort,
                )
            } ?: SecondScreenModel.Idle
        )
    }

    var alert by remember { mutableStateOf<FriendEvent?>(null) }
    LaunchedEffect(watcher) {
        watcher.alerts.collect { event ->
            alert = event
            // Mirrored, the card above unchanged: one screen must lose nothing to a
            // panel.
            PanelFeed.post(friendNoteText(context, event), PanelFeed.Kind.FRIEND)
        }
    }

    // Honoured here rather than in the activity: this is the only place that owns
    // `screen`.
    val pendingOpen by Notifications.PendingOpen.target.collectAsStateWithLifecycle()
    LaunchedEffect(pendingOpen) {
        if (Notifications.PendingOpen.consume() == Notifications.OPEN_FRIENDS) {
            onProfilePage = false
            screen = Screen.Friends
        }
    }

    /**
     * Scheduling is idempotent, and an app with nothing to watch schedules nothing.
     * pourquoi : docs/decisions/lancement-et-navigation.md § What is hoisted to app level, and why
     */
    val notifyFriends by settingsStore.notifyFriends.collectAsStateWithLifecycle()
    val notifyUpdates by settingsStore.notifyUpdates.collectAsStateWithLifecycle()
    // A key, not a decoration: the notification permission is granted outside the app.
    val foreground by AppForeground.visible.collectAsStateWithLifecycle()
    LaunchedEffect(notifyFriends, notifyUpdates, friends.size, foreground) {
        if (!foreground) return@LaunchedEffect
        Notifications.ensureChannels(context)
        FriendWatchJob.sync(context, notifyFriends, notifyUpdates)
    }

    if (onboarding) {
        // The first launch spends the token unseen, or the logo lands right after
        // onboarding.
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

    /**
     * The app composes behind the logo, so it uncovers a finished image.
     * pourquoi : docs/decisions/lancement-et-navigation.md § The preload runs, and the app composes behind it
     */
    var libraryReady by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val roms = withContext(Dispatchers.IO) {
            runCatching { romsRepo.cachedOrScan() }.getOrDefault(emptyList())
        }
        val warm = launch(Dispatchers.IO) {
            runCatching { GameTitles.refresh(context, roms) }
            runCatching { CompatCheck.refresh(context) }
            // Seven system queries and as many rasterisations, paid once here.
            runCatching { allEmulators(context) }
            runCatching { ArtworkPreload.warm(context, roms) }
        }
        withTimeoutOrNull(PRELOAD_MS) { warm.join() }
        libraryReady = true
    }

    /**
     * Null on the library, which is the root.
     * pourquoi : docs/decisions/lancement-et-navigation.md § What "back" means, screen by screen
     */
    val goBack: (() -> Unit)? = when (screen) {
        Screen.Library -> null
        is Screen.Preparing -> null
        is Screen.InSession -> null
        Screen.ProfileAndSettings -> ({ onProfilePage = false; screen = Screen.Library })
        else -> ({ screen = Screen.Library })
    }
    BackHandler(enabled = screen != Screen.Library) { goBack?.invoke() }

    /**
     * Cache read synchronously so the beads are on the first frame, refreshed after.
     * pourquoi : docs/decisions/lancement-et-navigation.md § What is hoisted to app level, and why
     */
    var compat by remember { mutableStateOf(CompatCheck.cached(context)) }
    LaunchedEffect(Unit) { compat = CompatCheck.refresh(context) }

    var gameMeta by remember { mutableStateOf(MetaCheck.cached(context)) }
    LaunchedEffect(Unit) { gameMeta = MetaCheck.refresh(context) }

    CompositionLocalProvider(
        LocalCompatDb provides compat,
        LocalGameMetaDb provides gameMeta,
    ) {
    when (val s = screen) {
        Screen.Library -> LibraryScreen(
            profile = profile,
            onOpenProfile = { onProfilePage = true; screen = Screen.ProfileAndSettings },
            onOpenFriends = { screen = Screen.Friends },
            onOpenFinder = { screen = Screen.Finder },
            // DS online play shares nothing with the session flow: no code to create,
            // none to join.
            onCreate = { rom, private ->
                if (rom.console.backend == Backend.MELONDS_WFC) screen = Screen.Wfc(rom)
                else startHostSession(rom, private)
            },
            onJoinWith = { rom ->
                if (rom.console.backend == Backend.MELONDS_WFC) screen = Screen.Wfc(rom)
                else screen = Screen.Join(rom.toRef())
            },
            // No session, no tunnel: the player picks a server inside PPSSPP.
            // pourquoi : docs/decisions/lancement-et-navigation.md § Two routes that are not sessions
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
            statuses = friendStatuses,
            onJoin = { code, romTitleId, romTitle -> joinKnownSession(code, romTitleId, romTitle) },
            onBack = { screen = Screen.Library }
        )
        is Screen.Preparing -> PreparingScreen(
            label = s.label,
            onGiveUp = {
                // The counter first, the tunnel after: the attempt in flight is
                // orphaned before it loses the floor.
                prepEpoch++
                EmufiiWgManager.stop(context)
                screen = Screen.Library
            }
        )
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
            romsRepo = romsRepo,
            libraryFolder = libraryFolder,
            librarySecondFolder = librarySecondFolder,
            libraryScanning = libraryScanning,
            libraryCount = libraryCount,
            onFolderPicked = { uri -> changeLibraryFolder(uri) },
            onSecondFolderPicked = { uri -> changeSecondLibraryFolder(uri) },
            onSecondFolderRemoved = { removeSecondLibraryFolder() },
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
                // The plan outlives the process, but must not outlive the session that
                // justified it.
                NetplayAutomation.clear(PlanStore(context))
                scope.launch {
                    if (s.session.role == Session.Role.HOST) {
                        client.deleteSession(s.session.code, s.session.token)
                    } else {
                        // Leave at once, so the host sees the departure now rather than
                        // at the TTL.
                        client.leaveSession(s.session.code, profile.id, s.session.token)
                    }
                    EmufiiWgManager.stop(context)
                }
                screen = Screen.Library
            }
        )
    }
    }

    // Last in source order, so it covers everything.
    if (splashing) {
        SplashScreen(
            ready = libraryReady,
            onDone = { SplashGate.pending = false }
        )
    }

    // The setting is not enough: the device may have only one screen.
    val panelDisplay by rememberPresentationDisplay()
    val panelWanted by settings.secondScreen.collectAsStateWithLifecycle()
    val panelLive = panelWanted && panelDisplay != null

    // One screen: the legend the panel would carry goes at the foot of this one, drawn
    // from the same motif so the two cannot drift.
    // pourquoi : docs/decisions/second-ecran.md § The legend, and why the symbols are drawn
    if (!panelLive) {
        val legendModel by SecondScreen.model.collectAsStateWithLifecycle()
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter
        ) {
            PadLegendBar(
                legend = legendModel.legend,
                modifier = Modifier
                    // No navigation-bar inset: the app hides the system bars, and the
                    // inset it still reports parked the legend 40 dp off the edge.
                    .padding(horizontal = 24.dp)
                    .padding(top = 12.dp, bottom = 10.dp)
            )
        }
    }

    // Silent on the main screen while the panel is lit: the note is already down there,
    // and one friend arriving twice reads as two friends.
    LaunchedEffect(panelLive) { if (panelLive) alert = null }

    // Before the conflict dialog in source order, so the dialog covers it.
    FriendAlert(
        event = if (panelLive) null else alert,
        onOpen = { alert = null; screen = Screen.Friends },
        onDismiss = { alert = null }
    )

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

/**
 * The strings the Android notification already uses.
 * pourquoi : docs/decisions/lancement-et-navigation.md § What the second screen receives
 */
private fun friendNoteText(context: android.content.Context, event: FriendEvent): String {
    val name = event.name ?: context.getString(R.string.notify_friend_unnamed)
    return when (event) {
        is FriendEvent.CameOnline -> context.getString(R.string.notify_friend_online, name)
        is FriendEvent.StartedPlaying -> event.game
            ?.let { context.getString(R.string.notify_friend_playing, name, it) }
            ?: context.getString(R.string.notify_friend_in_game, name)
    }
}

/**
 * Four seconds free, two more for a cold start.
 * pourquoi : docs/decisions/lancement-et-navigation.md § The preload runs, and the app composes behind it
 */
private const val PRELOAD_MS = 6_000L

