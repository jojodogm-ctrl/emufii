package eu.emufii.app.ui

import android.net.Uri
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.SizeTransform
import androidx.compose.foundation.background
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
import eu.emufii.app.library.emulatorVersion
import eu.emufii.app.ui.components.VersionMismatchDialog
import kotlinx.coroutines.CompletableDeferred
import eu.emufii.app.meta.LocalGameMetaDb
import eu.emufii.app.meta.MetaCheck
import eu.emufii.app.network.CoordinatorClient
import eu.emufii.app.network.RelayRegions
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
import eu.emufii.app.library.compatKeys
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import eu.emufii.app.ui.theme.LocalEmufiiDarkTheme
import eu.emufii.app.ui.theme.LocalEmufiiOledTheme
import eu.emufii.app.ui.theme.ShellDark
import eu.emufii.app.ui.theme.ShellLight
import eu.emufii.app.ui.theme.ShellOled
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
import kotlin.time.Duration.Companion.milliseconds

private val Screen.depth: Int
    get() = when (this) {
        Screen.Library -> 0
        is Screen.Preparing, is Screen.InSession -> 2
        else -> 1
    }

private sealed interface Screen {
    data object Library : Screen
    data object Finder : Screen
    data class Preparing(val label: String) : Screen
    data class Join(val rom: RomRef) : Screen
    data class InSession(val session: Session) : Screen

    data class Wfc(val rom: Rom) : Screen

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

/** Process scope: a `rememberSaveable` would come back with the activity. */
internal object SplashGate {
    var pending by mutableStateOf(true)
    var sessionAlive = false

    fun rearm() {
        if (!sessionAlive) pending = true
    }

}

private const val DEFAULT_PORT = 24872

private const val TUNNEL_TIMEOUT_MS = 45_000L

private const val CODE_ATTEMPTS = 5

private const val PRESENCE_MS = 45_000L

/** Trailing zero components are padding: Windows reports `2126.0.0.0` where Android says `2126.0`. */
internal fun sameEmulatorVersion(a: String, b: String): Boolean {
    fun norm(v: String) = v.trim().removePrefix("v").split('.', '-', ' ').dropLastWhile { it == "0" || it.isEmpty() }
    return norm(a) == norm(b)
}

private data class VersionGate(
    val console: Console,
    val host: String,
    val ours: String,
    val answer: CompletableDeferred<Boolean>
)

private const val TUNNEL_RELEASE_MS = 6_000L

/** Never `return@repeat` here: it ends the iteration, not the loop. */
private suspend fun pollHostIp(client: CoordinatorClient, code: String): String? {
    repeat(20) {
        delay(500.milliseconds)
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
    val settingsStore = settings
    val romsRepo = remember { RomsRepository.get(context) }
    val profile by profileStore.profile.collectAsStateWithLifecycle()

    var onProfilePage by rememberSaveable { mutableStateOf(false) }
    var screen by remember {
        mutableStateOf(if (onProfilePage) Screen.ProfileAndSettings else Screen.Library)
    }
    // In composition, before the new screen's cursor lands: see `Sfx.settle`.
    var settledFor by remember { mutableStateOf<Screen?>(null) }
    if (settledFor != screen) {
        settledFor = screen
        Sfx.settle()
    }
    // The gate must not re-arm while a session lives, or returning lands on the logo.
    SideEffect {
        SplashGate.sessionAlive =
            screen is Screen.InSession || screen is Screen.Preparing
    }
    DisposableEffect(Unit) { onDispose { SecondScreen.clear() } }

    var onboarding by remember { mutableStateOf(!settingsStore.onboardingDone) }

    val splashing = SplashGate.pending && settingsStore.onboardingDone


    val ensureVpn = LocalEnsureVpnPermission.current


    var libraryFolder by remember { mutableStateOf(romsRepo.savedFolderLabel()) }
    var librarySecondFolder by remember { mutableStateOf(romsRepo.secondFolderLabel()) }
    var libraryScanning by remember { mutableStateOf(false) }
    var libraryCount by remember { mutableStateOf<Int?>(null) }
    var libraryRevision by remember { mutableIntStateOf(0) }

    fun rescanLibrary() {
        if (libraryScanning) return
        libraryScanning = true
        scope.launch {
            // Off the main thread: walking a SAF tree over a large ROM has caused ANRs.
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
        rescanLibrary()
    }

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

    var prepEpoch by remember { mutableIntStateOf(0) }

    fun fail(message: String, back: Screen = Screen.Library) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        screen = back
    }

    fun fail(message: Int, back: Screen = Screen.Library) =
        fail(context.getString(message), back)

    var conflict by remember { mutableStateOf<Pair<TunnelHolder, () -> Unit>?>(null) }

    var versionGate by remember { mutableStateOf<VersionGate?>(null) }

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
                    withTimeoutOrNull(TUNNEL_RELEASE_MS.milliseconds) {
                        WfcManager.state.first { it !is WfcState.Active }
                    }
                }
                TunnelHolder.SESSION -> {
                    EmufiiWgManager.stop(context)
                    withTimeoutOrNull(TUNNEL_RELEASE_MS.milliseconds) {
                        EmufiiWgManager.state.first { it is WgState.Idle || it is WgState.Error }
                    }
                }

                TunnelHolder.NONE -> Unit
            }
            if (freed == null) fail(R.string.tunnel_conflict_stuck) else proceed()
        }
    }

    suspend fun awaitTunnel(): WgState.Online? = withTimeoutOrNull(TUNNEL_TIMEOUT_MS.milliseconds) {
        EmufiiWgManager.state.first { it is WgState.Error || it is WgState.Online } as? WgState.Online
    }

    fun startHostSession(rom: Rom, private: Boolean = false) =
        withTunnelSlot(TunnelHolder.SESSION) {
            scope.launch {
                var created: CreatedSession? = null
                var code = ""
                var lastError: Throwable? = null
                val region = RelayRegions.pick(client)

                @Suppress("unused")
                for (attempt in 1..CODE_ATTEMPTS) {
                    code = SessionCodes.generate()
                    val outcome = client.createSession(
                        code, rom.sessionId, rom.displayName, profile.name, profile.id,
                        // Explicit: 3DS and Switch both write titleId, and this picks the VPS room.
                        console = rom.console.wireName,
                        private = private,
                        region = region,
                        emulatorVersion = withContext(Dispatchers.IO) {
                            emulatorVersion(context, rom.console)
                        }
                    )
                    created = outcome.getOrNull()
                    if (created != null) break
                    lastError = outcome.exceptionOrNull()
                    val collision =
                        lastError.let { it is CoordinatorError.Http && it.status == 409 }
                    if (!collision) break
                }
                val session = created ?: return@launch fail(
                    if (lastError is CoordinatorError.Unreachable) R.string.flow_coordinator_unreachable
                    else R.string.flow_create_failed
                )

                ensureVpn(
                    onGranted = {
                        val epoch = prepEpoch
                        screen =
                            Screen.Preparing(context.getString(R.string.flow_connecting_tunnel))
                        scope.launch {
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
        if (rom?.console == Console.PS2 && !Ps2NetworkProfile.isReady(context)) {
            return fail(R.string.launch_ps2_profile_missing, screen)
        }
        withTunnelSlot(TunnelHolder.SESSION) {
            screen = Screen.Preparing(context.getString(R.string.flow_finding_session))
            scope.launch {
                val back = if (rom != null) Screen.Join(rom) else Screen.Finder
                val remote = client.getSession(code).getOrElse { err ->
                    return@launch fail(
                        if (err is CoordinatorError.NotFound) R.string.flow_session_not_found
                        else R.string.flow_coordinator_unreachable,
                        back
                    )
                }

                // Two regional dumps share a title id, so only different titles are caught.
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

                val mine = rom?.let { withContext(Dispatchers.IO) { emulatorVersion(context, it.console) } }
                val theirs = remote.emulatorVersion
                if (mine != null && theirs != null && !sameEmulatorVersion(mine, theirs)) {
                    val answer = CompletableDeferred<Boolean>()
                    versionGate = VersionGate(rom!!.console, theirs, mine, answer)
                    val goOn = answer.await()
                    versionGate = null
                    if (!goOn) {
                        screen = back
                        return@launch
                    }
                }

                ensureVpn(
                    onGranted = {
                        val epoch = prepEpoch
                        screen =
                            Screen.Preparing(context.getString(R.string.flow_connecting_tunnel))
                        scope.launch {
                            // 503 is full, 429 is asking too fast.
                            val info = client.claimAddress(
                                code, EmufiiWgManager.publicKey(context), profile.name, profile.id
                            ).getOrElse { err ->
                                val why = when (err) {
                                    is CoordinatorError.Http if err.status == 503 ->
                                        R.string.flow_session_full

                                    is CoordinatorError.Http if err.status == 429 ->
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
                            val hostIp = remote.hostIp ?: pollHostIp(client, code)
                            ?: run {
                                EmufiiWgManager.stop(context)
                                return@launch fail(R.string.flow_host_not_ready)
                            }
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

    val inSession = screen is Screen.InSession
    LaunchedEffect(profile.id, profile.name, inSession) {
        if (inSession) return@LaunchedEffect
        while (true) {
            client.announcePresence(profile.id, profile.name, inSession = false)
            // On the heartbeat so a failed upload retries; a picture unchanged costs a stat.
            eu.emufii.app.profile.AvatarSync.get(context).syncOwn(client, profile)
            delay(PRESENCE_MS.milliseconds)
        }
    }

    var lastLaunched by remember { mutableStateOf<Pair<String, String?>?>(null) }
    fun launched(rom: Rom) { lastLaunched = rom.displayName to rom.compatKeys().firstOrNull() }
    LaunchedEffect(screen) {
        when (val s = screen) {
            is Screen.InSession -> s.session.rom?.let { ref ->
                lastLaunched = ref.displayName to
                    compatKeys(ref.console, ref.productCode, ref.titleIdHex).firstOrNull()
            }
            is Screen.Wfc -> launched(s.rom)
            is Screen.PspOnline -> launched(s.rom)
            else -> Unit
        }
    }
    val shareLastGame by settingsStore.shareLastGame.collectAsStateWithLifecycle()
    LaunchedEffect(lastLaunched, shareLastGame, profile.id) {
        val game = lastLaunched
        when {
            !shareLastGame -> client.announceLastGame(profile.id, null, null)
            game != null -> client.announceLastGame(profile.id, game.first, game.second)
        }
    }

    val friends by friendStore.friends.collectAsStateWithLifecycle()
    val watcher = remember { FriendWatcher(context, client) }
    val friendStatuses by watcher.statuses.collectAsStateWithLifecycle()
    val friendLastGames by watcher.lastGames.collectAsStateWithLifecycle()
    val friendAvatars by remember { eu.emufii.app.profile.AvatarSync.get(context) }.friendFiles
        .collectAsStateWithLifecycle()
    val friendCodes = friends.map { it.code }
    LaunchedEffect(friendCodes) { watcher.run(friendCodes) }

    val friendPlayingUnknown = stringResource(R.string.friends_playing_unknown)
    val friendUnnamed = stringResource(R.string.profile_default_name)
    val friendOnline = stringResource(R.string.friends_online)
    val friendOffline = stringResource(R.string.friends_offline)

    var gameMeta by remember { mutableStateOf(MetaCheck.cached(context)) }
    LaunchedEffect(Unit) { gameMeta = MetaCheck.refresh(context) }
    val friendsFocus by SecondScreen.friendsFocus.collectAsStateWithLifecycle()
    LaunchedEffect(screen, friends, friendStatuses, friendsFocus, friendAvatars, friendLastGames) {
        if (screen is Screen.Friends) {
            val face =
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
                                code = friend.code,
                                avatar = friendAvatars[friend.code],
                                game = if (status?.inSession == true) {
                                    status.romTitle?.let { title ->
                                        eu.emufii.app.network.LastGame(
                                            title,
                                            friendLastGames[friend.code]?.takeIf { it.title == title }?.titleId,
                                            0L
                                        )
                                    } ?: friendLastGames[friend.code]
                                } else friendLastGames[friend.code],
                                gameShot = friendLastGames[friend.code]
                                    ?.takeIf { status?.inSession != true || it.title == status.romTitle }
                                    ?.titleId
                                    ?.let { gameMeta.metaFor(listOf(it))?.screenshots?.firstOrNull() },
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
                                onJoin = status?.sessionCode
                                    ?.takeIf { status.ready }
                                    ?.let { session ->
                                        { joinKnownSession(session, status.romTitleId, status.romTitle) }
                                    },
                            )
                        },
                    focus = friendsFocus,
                )
            SecondScreen.publish(face)
            SecondScreen.model.collect { shown ->
                if (shown !is SecondScreenModel.Friends && SecondScreen.aside.value == null) {
                    SecondScreen.publish(face)
                }
            }
        }
    }
    LaunchedEffect(screen) {
        if (screen is Screen.Friends) return@LaunchedEffect
        SecondScreen.publish(
            (screen as? Screen.InSession)?.session?.let { active ->
                SecondScreenModel.InSession(
                    code = active.code,
                    role = active.role,
                    console = active.console,
                    gameTitle = active.rom?.displayName,
                    hostAddress = active.shownAddress,
                    port = active.shownPort,
                )
            } ?: SecondScreenModel.Idle
        )
    }

    if (eu.emufii.app.BuildConfig.DEBUG) {
        DisposableEffect(watcher) {
            val receiver = object : android.content.BroadcastReceiver() {
                override fun onReceive(c: android.content.Context, i: android.content.Intent) {
                    android.util.Log.d("FriendAlertDebug", "received ${i.extras?.keySet()}")
                    val name = i.getStringExtra("name")
                    val code = "DEBUG-" + (name ?: "x")
                    watcher.debugEmit(
                        when (i.getStringExtra("type")) {
                            "playing" -> FriendEvent.StartedPlaying(code, name, i.getStringExtra("game"))
                            "ingame" -> FriendEvent.StartedPlaying(code, name, null)
                            else -> FriendEvent.CameOnline(code, name)
                        }
                    )
                }
            }
            androidx.core.content.ContextCompat.registerReceiver(
                context, receiver,
                android.content.IntentFilter("eu.emufii.app.DEBUG_FRIEND_EVENT"),
                androidx.core.content.ContextCompat.RECEIVER_EXPORTED
            )
            onDispose { runCatching { context.unregisterReceiver(receiver) } }
        }
    }

    var alert by remember { mutableStateOf<FriendEvent?>(null) }
    LaunchedEffect(watcher) {
        watcher.alerts.collect { event ->
            if (eu.emufii.app.BuildConfig.DEBUG) android.util.Log.d("FriendAlertDebug", "collected $event")
            alert = event
            PanelFeed.post(friendNoteText(context, event), PanelFeed.Kind.FRIEND, event)
        }
    }

    val pendingOpen by Notifications.PendingOpen.target.collectAsStateWithLifecycle()
    LaunchedEffect(pendingOpen) {
        if (Notifications.PendingOpen.consume() == Notifications.OPEN_FRIENDS) {
            onProfilePage = false
            screen = Screen.Friends
        }
    }

    val notifyFriends by settingsStore.notifyFriends.collectAsStateWithLifecycle()
    // A key, not a decoration: the notification permission is granted outside the app.
    val foreground by AppForeground.visible.collectAsStateWithLifecycle()
    LaunchedEffect(notifyFriends, friends.size, foreground) {
        if (!foreground) return@LaunchedEffect
        Notifications.ensureChannels(context)
        FriendWatchJob.sync(context, notifyFriends)
    }

    if (onboarding) {
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

    var libraryReady by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val roms = withContext(Dispatchers.IO) {
            runCatching { romsRepo.cachedOrScan() }.getOrDefault(emptyList())
        }
        val warm = launch(Dispatchers.IO) {
            runCatching { GameTitles.refresh(context, roms) }
            runCatching { CompatCheck.refresh(context) }
            runCatching { allEmulators(context) }
            runCatching { ArtworkPreload.warm(context, roms) }
        }
        withTimeoutOrNull(PRELOAD_MS.milliseconds) { warm.join() }
        libraryReady = true
    }

    val goBack: (() -> Unit)? = when (screen) {
        Screen.Library -> null
        is Screen.Preparing -> null
        is Screen.InSession -> null
        Screen.ProfileAndSettings -> ({ onProfilePage = false; screen = Screen.Library })
        else -> ({ screen = Screen.Library })
    }
    BackHandler(enabled = screen != Screen.Library) { goBack?.invoke() }

    var compat by remember { mutableStateOf(CompatCheck.cached(context)) }
    LaunchedEffect(Unit) { compat = CompatCheck.refresh(context) }


    CompositionLocalProvider(
        LocalCompatDb provides compat,
        LocalGameMetaDb provides gameMeta,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    when {
                        LocalEmufiiOledTheme.current -> ShellOled
                        LocalEmufiiDarkTheme.current -> ShellDark
                        else -> ShellLight
                    }
                )
        )

        // `transitionSpec` is not a composable scope, so the specs are read here.
        val bloomIn: FiniteAnimationSpec<Float> = Motion.enter()
        val bloomOut: FiniteAnimationSpec<Float> = Motion.exit()
        AnimatedContent(
            targetState = screen,
            transitionSpec = {
                // No blur: two full-screen blurred layers per frame made page changes drag on the Thor.
                val enter = fadeIn(bloomIn) + scaleIn(bloomIn, initialScale = 0.965f)
                val exit = fadeOut(bloomOut) + scaleOut(bloomOut, targetScale = 0.965f)
                (enter togetherWith exit).using(SizeTransform(clip = false))
            },
            label = "screen"
        ) { s ->
        val openedAt = remember { SystemClock.uptimeMillis() }
        CompositionLocalProvider(LocalScreenOpenedAt provides openedAt) {
        Box(Modifier.fillMaxSize()) {
        when (s) {
            Screen.Library -> LibraryScreen(
                profile = profile,
                onlineFriends = friendStatuses.values.count { it.online },
                onOpenProfile = { onProfilePage = true; screen = Screen.ProfileAndSettings },
                onOpenFriends = { screen = Screen.Friends },
                onOpenFinder = { screen = Screen.Finder },
                onCreate = { rom, private -> startHostSession(rom, private) },
                onJoinWith = { rom -> screen = Screen.Join(rom.toRef()) },
                onPlayPublic = { rom ->
                    if (rom.console == Console.DS) {
                        screen = Screen.Wfc(rom)
                    } else {
                        when (val launched = eu.emufii.app.psp.PpssppLauncher(context).launchAutoPublic(rom)) {
                            null -> screen = Screen.PspOnline(rom)
                            eu.emufii.app.azahar.LaunchResult.Success -> launched(rom)
                            eu.emufii.app.azahar.LaunchResult.NotInstalled ->
                                fail(context.getString(R.string.err_not_installed, "PPSSPP"))
                            is eu.emufii.app.azahar.LaunchResult.Error ->
                                fail(context.getString(R.string.err_generic, launched.message))
                            else -> Unit
                        }
                    }
                },
                onFolderPicked = { uri -> changeLibraryFolder(uri) },
                libraryRevision = libraryRevision
            )

            is Screen.PspOnline -> PspOnlineScreen(
                rom = s.rom,
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
                lastGames = friendLastGames,
                avatars = friendAvatars,
                onJoin = { code, romTitleId, romTitle ->
                    joinKnownSession(
                        code,
                        romTitleId,
                        romTitle
                    )
                },
                onBack = { screen = Screen.Library }
            )

            is Screen.Preparing -> PreparingScreen(
                label = s.label,
                onGiveUp = {
                    // Bump before releasing the tunnel so the in-flight attempt is orphaned first.
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
                    NetplayAutomation.clear(PlanStore(context))
                    scope.launch {
                        if (s.session.role == Session.Role.HOST) {
                            client.deleteSession(s.session.code, s.session.token)
                        } else {
                            client.leaveSession(s.session.code, profile.id, s.session.token)
                        }
                        EmufiiWgManager.stop(context)
                    }
                    screen = Screen.Library
                }
            )
        }
                }
        }
}
    }

    // Last in source order, so it covers everything.
    if (splashing) {
        SplashScreen(
            ready = libraryReady,
            onDone = { SplashGate.pending = false }
        )
    }

    val panelDisplay by rememberPresentationDisplay()
    val panelWanted by settings.secondScreen.collectAsStateWithLifecycle()
    val panelLive = panelWanted && panelDisplay != null

    if (!panelLive) {
        val legendModel by SecondScreen.model.collectAsStateWithLifecycle()
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter
        ) {
            PadLegendBar(
                legend = legendModel.legend,
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .padding(top = 12.dp, bottom = 10.dp)
            )
        }
    }

    LaunchedEffect(panelLive) { if (panelLive) alert = null }

    // Before the conflict dialog in source order, so the dialog covers it.
    FriendAlert(
        event = if (panelLive) null else alert,
        onOpen = { alert = null; screen = Screen.Friends },
        onDismiss = { alert = null }
    )

    versionGate?.let { (console, theirs, mine, answer) ->
        VersionMismatchDialog(
            console = console,
            hostVersion = theirs,
            ourVersion = mine,
            onContinue = { answer.complete(true) },
            onLeave = { answer.complete(false) }
        )
    }

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

private fun friendNoteText(context: android.content.Context, event: FriendEvent): String {
    val name = event.name ?: context.getString(R.string.notify_friend_unnamed)
    return when (event) {
        is FriendEvent.CameOnline -> context.getString(R.string.notify_friend_online, name)
        is FriendEvent.StartedPlaying -> event.game
            ?.let { context.getString(R.string.notify_friend_playing, name, it) }
            ?: context.getString(R.string.notify_friend_in_game, name)
    }
}

private const val PRELOAD_MS = 6_000L

