package eu.emufii.app.ui.screens

import android.content.Intent
import android.text.format.DateUtils
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.request.crossfade
import eu.emufii.app.R
import eu.emufii.app.meta.LocalGameMetaDb
import eu.emufii.app.network.LastGame
import eu.emufii.app.profile.AddFriendResult
import eu.emufii.app.profile.Friend
import eu.emufii.app.profile.FriendStatus
import eu.emufii.app.profile.FriendStore
import eu.emufii.app.profile.Profile
import eu.emufii.app.profile.playerDisplayName
import eu.emufii.app.secondscreen.FriendsFocus
import eu.emufii.app.secondscreen.SecondScreen
import eu.emufii.app.ui.Motion
import eu.emufii.app.ui.Sfx
import eu.emufii.app.ui.TrailerSpinner
import eu.emufii.app.ui.components.Avatar
import eu.emufii.app.ui.components.CrossIcon
import eu.emufii.app.ui.components.EmufiiScaffold
import eu.emufii.app.ui.components.GhostButton
import eu.emufii.app.ui.components.PadDialog
import eu.emufii.app.ui.components.PadTextField
import eu.emufii.app.ui.components.PrimaryButton
import eu.emufii.app.ui.components.TopBarChip
import eu.emufii.app.ui.components.padEntry
import eu.emufii.app.ui.components.upToHeader
import eu.emufii.app.ui.copyToClipboard
import eu.emufii.app.ui.focusRing
import eu.emufii.app.ui.popIn
import eu.emufii.app.ui.tap
import eu.emufii.app.ui.theme.Coral
import eu.emufii.app.ui.theme.ErrorDark
import eu.emufii.app.ui.theme.ErrorLight
import eu.emufii.app.ui.theme.GoodDark
import eu.emufii.app.ui.theme.GoodLight
import eu.emufii.app.ui.theme.LocalEmufiiDarkTheme
import eu.emufii.app.ui.theme.LocalEmufiiOledTheme
import eu.emufii.app.ui.theme.PlateLight
import eu.emufii.app.ui.theme.ShellDarkLow
import eu.emufii.app.ui.theme.WarnDark
import eu.emufii.app.ui.theme.WarnLight
import eu.emufii.app.ui.theme.plate
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun FriendsScreen(
    profile: Profile,
    friendStore: FriendStore,
    statuses: Map<String, FriendStatus>,
    lastGames: Map<String, LastGame>,
    avatars: Map<String, File>,
    onJoin: (code: String, romTitleId: String?, romTitle: String?) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val friends by friendStore.friends.collectAsStateWithLifecycle()
    var input by remember { mutableStateOf("") }
    var addError by remember { mutableStateOf<String?>(null) }
    var adding by remember { mutableStateOf(false) }
    var pendingRemoval by remember { mutableStateOf<Friend?>(null) }

    val invalidMessage = stringResource(R.string.friends_error_invalid)
    val duplicateMessage = stringResource(R.string.friends_error_duplicate)
    val selfMessage = stringResource(R.string.friends_error_self)

    // In a game, then online, then the rest by name: the ones you can join come first.
    val ordered = friends.sortedWith(
        compareByDescending<Friend> { statuses[it.code]?.inSession == true }
            .thenByDescending { statuses[it.code]?.online == true }
            .thenBy { (it.name ?: it.displayCode).lowercase() }
    )
    val onlineCount = ordered.count { statuses[it.code]?.online == true }

    // Keyed by friend code, not index: the order changes as people come and go.
    val focus by SecondScreen.friendsFocus.collectAsStateWithLifecycle()
    val selectedCode = (focus as? FriendsFocus.Friend)?.code
        ?.takeIf { code -> ordered.any { it.code == code } }
        ?: ordered.firstOrNull()?.code
    val selectedIndex = ordered.indexOfFirst { it.code == selectedCode }.coerceAtLeast(0)
    LaunchedEffect(selectedCode) {
        if (selectedCode != null) SecondScreen.friendsFocus.value = FriendsFocus.Friend(selectedCode)
    }
    DisposableEffect(Unit) { onDispose { SecondScreen.friendsFocus.value = FriendsFocus.None } }
    val select: (Int) -> Unit = { index ->
        ordered.getOrNull(index)?.let {
            if (it.code != selectedCode) Sfx.tick()
            SecondScreen.friendsFocus.value = FriendsFocus.Friend(it.code)
        }
    }

    val onCopyCode = {
        copyToClipboard(context, "Emufii", profile.friendCode)
        Sfx.confirm()
    }
    val onShareCode = {
        val text = context.getString(R.string.friends_share_text, profile.friendCode)
        context.startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, text)
                },
                null
            )
        )
    }
    val onAddFriend = {
        when (val result = friendStore.add(input, profile.id)) {
            is AddFriendResult.Added -> {
                input = ""
                addError = null
                adding = false
                SecondScreen.friendsFocus.value = FriendsFocus.Friend(result.friend.code)
                Sfx.confirm()
            }
            AddFriendResult.Invalid -> addError = invalidMessage
            AddFriendResult.AlreadyAdded -> addError = duplicateMessage
            AddFriendResult.Self -> addError = selfMessage
        }
    }

    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val selected = ordered.getOrNull(selectedIndex)
    // Up from the row lands on the card's buttons, not on the header above them.
    val cardActions = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        repeat(2) { withFrameNanos { } }
        runCatching { cardActions.requestFocus() }
    }

    EmufiiScaffold(
        title = stringResource(R.string.friends_title),
        onBack = onBack,
        modifier = modifier,
        contentScrolls = false,
        trailing = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (ordered.isNotEmpty()) CountLine(ordered.size, onlineCount)
                CodePill(profile = profile, onCopy = onCopyCode)
                TopBarChip(onClick = { adding = true }) {
                    Text(
                        "+",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    ) { topPadding ->
        if (selected == null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
                contentAlignment = Alignment.Center
            ) {
                NobodyCard(
                    code = profile.friendCode,
                    onAdd = { adding = true },
                    onShare = onShareCode
                )
            }
            return@EmufiiScaffold
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 20.dp, end = 20.dp, top = topPadding, bottom = bottomInset + 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically)
        ) {
            if (selected == null) {
                NobodyCard(
                    code = profile.friendCode,
                    onAdd = { adding = true },
                    onShare = onShareCode
                )
            } else {
                FriendCard(
                    friend = selected,
                    status = statuses[selected.code] ?: FriendStatus.Offline,
                    lastGame = lastGames[selected.code],
                    avatar = avatars[selected.code],
                    index = selectedIndex,
                    onJoin = onJoin,
                    onRemove = { pendingRemoval = selected },
                    onStep = { step -> select(selectedIndex + step) },
                    actions = cardActions
                )
                run {
                    FriendCarousel(
                        faces = ordered.map { f ->
                            CarouselFace(
                                code = f.code,
                                name = f.name?.let { playerDisplayName(it) } ?: f.displayCode,
                                avatar = avatars[f.code],
                                status = statuses[f.code] ?: FriendStatus.Offline,
                            )
                        },
                        selected = selectedIndex,
                        onSelect = select,
                        faceSize = 64.dp,
                        up = cardActions,
                    )
                }
            }
        }
    }

    if (adding) {
        PadDialog(
            title = stringResource(R.string.friends_add),
            onDismiss = { adding = false; addError = null },
            panelDetail = stringResource(R.string.friends_add_note),
            panelSocial = true,
            actions = {
                GhostButton(
                    label = stringResource(R.string.friends_cancel),
                    onClick = { adding = false; addError = null }
                )
                GhostButton(
                    label = stringResource(R.string.friends_add_action),
                    onClick = onAddFriend
                )
            }
        ) {
            Text(
                stringResource(R.string.friends_add_note),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            PadTextField(
                value = input,
                onValueChange = { input = it; addError = null },
                placeholder = stringResource(R.string.friends_add_hint),
                isError = addError != null,
                modifier = Modifier.fillMaxWidth().padEntry()
            )
            addError?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = danger())
            }
        }
    }

    pendingRemoval?.let { friend ->
        val label = friend.name?.let { playerDisplayName(it) } ?: friend.displayCode
        PadDialog(
            title = stringResource(R.string.friends_remove_confirm, label),
            onDismiss = { pendingRemoval = null },
            actions = {
                GhostButton(
                    label = stringResource(R.string.friends_cancel),
                    onClick = { pendingRemoval = null }
                )
                GhostButton(
                    label = stringResource(R.string.friends_remove),
                    onClick = {
                        friendStore.remove(friend.code)
                        pendingRemoval = null
                    },
                    tint = danger()
                )
            }
        ) {}
    }
}

@Composable
private fun CodePill(profile: Profile, onCopy: () -> Unit) {
    val dark = LocalEmufiiDarkTheme.current
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .height(46.dp)
            .focusRing(focused, CircleShape, width = 2.5.dp, glowRadius = 10.dp)
            .plate(CircleShape, dark = dark, oled = LocalEmufiiOledTheme.current, lift = 3.dp)
            .tap(interactionSource = interaction, indication = null, onClick = onCopy)
            .padding(start = 5.dp, end = 16.dp)
    ) {
        Avatar(
            name = playerDisplayName(profile.name.ifBlank { Profile.DEFAULT_NAME }),
            imageFile = profile.avatarFile,
            size = 36.dp
        )
        Text(
            profile.friendCode,
            style = MaterialTheme.typography.labelLarge,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = if (dark) Coral.darkBright else Coral.deep
        )
    }
}

@Composable
private fun FriendCard(
    friend: Friend,
    status: FriendStatus,
    lastGame: LastGame?,
    avatar: File?,
    index: Int,
    onJoin: (code: String, romTitleId: String?, romTitle: String?) -> Unit,
    onRemove: () -> Unit,
    onStep: (Int) -> Unit,
    actions: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val dark = LocalEmufiiDarkTheme.current
    val oled = LocalEmufiiOledTheme.current
    val cardButtons = remember { mutableStateOf(CardButtons(-1, 1)) }
    var refocus by remember { mutableStateOf(false) }
    LaunchedEffect(friend.code) {
        if (!refocus) return@LaunchedEffect
        withFrameNanos { }
        runCatching { actions.requestFocus() }
        refocus = false
    }
    var last by remember { mutableStateOf(index) }
    val forward = index >= last
    LaunchedEffect(index) { last = index }
    val slideIn = Motion.enter<IntOffset>()
    val slideOut = Motion.exit<IntOffset>()
    val fadeInSpec = Motion.enter<Float>()
    val fadeOutSpec = Motion.exit<Float>()

    Box(
        modifier = modifier
            .widthIn(max = 900.dp)
            .fillMaxWidth()
            .height(CARD_HEIGHT)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val buttons = cardButtons.value
                val step = { by: Int -> refocus = buttons.focused >= 0; onStep(by); true }
                when (event.key) {
                    Key.ButtonL1 -> step(-1)
                    Key.ButtonR1 -> step(1)
                    Key.DirectionLeft -> if (buttons.focused > 0) false else step(-1)
                    Key.DirectionRight ->
                        if (buttons.focused in 0 until buttons.count - 1) false else step(1)
                    else -> false
                }
            }
            .plate(CardShape, dark = dark, oled = oled, lift = 10.dp)
            // After the plate, so its shadow stays outside the clip.
            .clip(CardShape)
    ) {
        AnimatedContent(
            targetState = CardState(friend, status, lastGame, avatar),
            contentKey = { it.friend.code },
            transitionSpec = {
                val sign = if (forward) 1 else -1
                (slideInHorizontally(slideIn) { sign * it / 8 } + fadeIn(fadeInSpec))
                    .togetherWith(slideOutHorizontally(slideOut) { -sign * it / 8 } + fadeOut(fadeOutSpec))
                    .using(SizeTransform(clip = false))
            },
            label = "friend-card",
            modifier = Modifier.fillMaxSize()
        ) { state ->
            CardBody(
                state,
                actions = actions,
                onJoin = onJoin,
                onRemove = onRemove,
                onButtons = { cardButtons.value = it }
            )
        }
    }
}

@Composable
private fun RemoveChip(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val red = danger()
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Box(
        modifier = modifier
            .size(48.dp)
            .focusRing(focused, CircleShape, width = 2.5.dp, glowRadius = 10.dp)
            .clip(CircleShape)
            .background(red)
            .tap(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        CrossIcon(size = 16.dp, color = Color.White)
    }
}

private data class CardButtons(val focused: Int, val count: Int)

private data class CardState(
    val friend: Friend,
    val status: FriendStatus,
    val lastGame: LastGame?,
    val avatar: File?,
)

@Composable
private fun CardBody(
    state: CardState,
    actions: FocusRequester,
    onJoin: (code: String, romTitleId: String?, romTitle: String?) -> Unit,
    onRemove: () -> Unit,
    onButtons: (CardButtons) -> Unit,
) {
    val (friend, status, lastGame, avatar) = state
    val name = friend.name?.let { playerDisplayName(it) } ?: friend.displayCode
    val session = status.sessionCode?.takeIf { status.ready }
    // In a game, the game they are in; otherwise the last one they launched.
    val game = if (status.inSession) {
        status.romTitle?.let { LastGame(it, lastGame?.takeIf { g -> g.title == it }?.titleId, 0L) } ?: lastGame
    } else lastGame
    Row(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Box(contentAlignment = Alignment.BottomEnd) {
            SquarePortrait(name = name, file = avatar)
            if (status.online || status.inSession) {
                Box(Modifier.padding(10.dp)) { PresenceDot(status, big = true) }
            }
        }
        Column(
            modifier = Modifier.weight(1f).fillMaxHeight().padding(vertical = 6.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    name,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                StatusPill(status)
                Text(
                    friend.displayCode,
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            val entry = Modifier.focusRequester(actions).padEntry()
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val count = if (session != null) 2 else 1
                val track = { i: Int ->
                    Modifier.onFocusChanged { f ->
                        if (f.hasFocus) onButtons(CardButtons(i, count))
                        else onButtons(CardButtons(-1, count))
                    }
                }
                when {
                    session != null -> PrimaryButton(
                        modifier = entry.then(track(0)),
                        label = stringResource(R.string.friends_join),
                        onClick = { onJoin(session, status.romTitleId, status.romTitle) }
                    )
                    // Their session exists but their tunnel isn't up: joining would only spin.
                    status.inSession -> TrailerSpinner(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        size = 20.dp,
                        stroke = 2.5.dp,
                        fps = 30
                    )
                    else -> Unit
                }
                RemoveChip(
                    onClick = onRemove,
                    modifier = (if (session == null) entry else Modifier.upToHeader())
                        .then(track(count - 1))
                )
            }
        }
        GamePicture(
            game = game,
            playingNow = status.inSession,
            modifier = Modifier.fillMaxHeight().aspectRatio(16f / 10f)
        )
    }
}

@Composable
private fun SquarePortrait(name: String, file: File?) {
    Avatar(
        name = name,
        imageFile = file,
        size = CARD_HEIGHT - 32.dp,
        shape = RoundedCornerShape(22.dp)
    )
}

@Composable
internal fun GamePicture(
    game: LastGame?,
    playingNow: Boolean,
    modifier: Modifier = Modifier,
    big: Boolean = false,
    /** Resolved by the caller: the panel window has no `LocalGameMetaDb`. */
    shotUrl: String? = null,
) {
    val meta = LocalGameMetaDb.current
    val dark = LocalEmufiiDarkTheme.current
    val shot = shotUrl ?: game?.titleId?.let { meta.metaFor(listOf(it))?.screenshots?.firstOrNull() }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(if (big) 28.dp else 18.dp))
            .background(
                Brush.linearGradient(
                    listOf(if (dark) Coral.darkBright else Coral.bright, Coral.deep)
                )
            )
    ) {
        if (shot != null) {
            AsyncImage(
                model = coil3.request.ImageRequest.Builder(LocalContext.current)
                    .data(shot)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                // DS and 3DS snaps stack both screens: the top one is the game.
                alignment = Alignment.TopCenter,
                modifier = Modifier.fillMaxSize()
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.4f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.72f)
                    )
                )
        )
        Column(
            modifier = Modifier.align(Alignment.BottomStart).padding(if (big) 24.dp else 14.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                stringResource(if (playingNow) R.string.friends_playing_now else R.string.friends_last_game),
                style = if (big) MaterialTheme.typography.titleSmall else MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.8f)
            )
            Text(
                game?.title ?: stringResource(R.string.friends_no_game),
                style = if (big) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (game != null && !playingNow && game.at > 0) {
                val now = System.currentTimeMillis()
                Text(
                    if (now - game.at < DateUtils.MINUTE_IN_MILLIS) stringResource(R.string.friends_just_now)
                    else DateUtils.getRelativeTimeSpanString(game.at, now, DateUtils.MINUTE_IN_MILLIS).toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
private fun StatusPill(status: FriendStatus) {
    val here = status.online || status.inSession
    val color = if (here) presenceColor(status) else MaterialTheme.colorScheme.onSurfaceVariant
    val label = when {
        status.inSession -> stringResource(R.string.friends_in_game)
        status.online -> stringResource(R.string.friends_online)
        else -> stringResource(R.string.friends_offline)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 12.dp, vertical = 5.dp)
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Text(label, style = MaterialTheme.typography.labelLarge, color = color, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun FriendCarousel(
    faces: List<CarouselFace>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    focusable: Boolean = true,
    faceSize: androidx.compose.ui.unit.Dp = 76.dp,
    entry: Boolean = false,
    up: FocusRequester? = null,
) {
    val scope = rememberCoroutineScope()
    val position = remember { Animatable(selected.toFloat()) }
    val glide = Motion.cursor<Float>()
    LaunchedEffect(selected) { position.animateTo(selected.toFloat(), glide) }
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val density = LocalDensity.current
    val stepPx = with(density) { STEP.toPx() }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(faceSize + 48.dp)
            .then(
                if (focusable) Modifier
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (event.key) {
                            Key.DirectionLeft -> { onSelect(selected - 1); true }
                            Key.DirectionRight -> { onSelect(selected + 1); true }
                            Key.DirectionUp -> up?.let { runCatching { it.requestFocus() }.isSuccess } ?: false
                            else -> false
                        }
                    }
                    .then(if (entry) Modifier.padEntry() else Modifier)
                    .focusable(interactionSource = interaction)
                else Modifier
            )
            .pointerInput(faces.size) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        val target = position.value.roundToInt().coerceIn(0, faces.lastIndex)
                        onSelect(target)
                        scope.launch { position.animateTo(target.toFloat(), glide) }
                    }
                ) { _, delta ->
                    scope.launch {
                        position.snapTo(
                            (position.value - delta / stepPx).coerceIn(-0.4f, faces.lastIndex + 0.4f)
                        )
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        faces.forEachIndexed { index, face ->
            val distance = index - position.value
            // Past four on each side the row has faded out: nothing to draw.
            if (abs(distance) > 4.5f) return@forEachIndexed
            val near = (1f - abs(distance)).coerceIn(0f, 1f)
            val name = face.name
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (distance * stepPx).roundToInt(),
                            (-10 * density.density * near).roundToInt()
                        )
                    }
                    .graphicsLayer {
                        val s = 0.78f + 0.22f * near
                        scaleX = s
                        scaleY = s
                        alpha = (1f - abs(distance) / 4.5f).coerceIn(0f, 1f)
                        // Per draw: an offscreen buffer clipped the cursor glow square while sliding.
                        compositingStrategy = CompositingStrategy.ModulateAlpha
                    }
                    .tap(onClick = { onSelect(index) })
            ) {
                Box(contentAlignment = Alignment.BottomEnd) {
                    Avatar(
                        name = name,
                        imageFile = face.avatar,
                        size = faceSize,
                        ring = if (index == selected && !focused) Coral.bright.copy(alpha = 0.55f) else null,
                        modifier = Modifier.focusRing(
                            focused && index == selected,
                            CircleShape,
                            width = 3.dp,
                            glowRadius = 10.dp
                        )
                    )
                    if (face.status.online || face.status.inSession) {
                        Box(Modifier.zIndex(1f)) { PresenceDot(face.status) }
                    }
                }
                Text(
                    name,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f + 0.55f * near),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 120.dp).padding(top = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun NobodyCard(code: String, onAdd: () -> Unit, onShare: () -> Unit) {
    val dark = LocalEmufiiDarkTheme.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .widthIn(max = 560.dp)
            .fillMaxWidth()
            .plate(RoundedCornerShape(28.dp), dark = dark, oled = LocalEmufiiOledTheme.current, lift = 16.dp)
            .padding(28.dp)
    ) {
        Text(
            stringResource(R.string.friends_none_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Black
        )
        Text(
            stringResource(R.string.friends_none_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            code,
            style = MaterialTheme.typography.headlineMedium,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = if (dark) Coral.darkBright else Coral.deep
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GhostButton(label = stringResource(R.string.friends_share), onClick = onShare)
            PrimaryButton(
                label = stringResource(R.string.friends_add),
                onClick = onAdd,
                modifier = Modifier.padEntry()
            )
        }
    }
}

data class CarouselFace(
    val code: String,
    val name: String,
    val avatar: File?,
    val status: FriendStatus,
)

@Composable
private fun CountLine(total: Int, online: Int) {
    Text(
        pluralStringResource(R.plurals.friends_count, total, total) +
            if (online > 0) " · " + stringResource(R.string.friends_count_online, online) else "",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun presenceColor(status: FriendStatus): Color {
    val dark = LocalEmufiiDarkTheme.current
    return when {
        status.inSession -> if (dark) WarnDark else WarnLight
        else -> if (dark) GoodDark else GoodLight
    }
}

@Composable
internal fun PresenceDot(status: FriendStatus, big: Boolean = false) {
    val dark = LocalEmufiiDarkTheme.current
    val ring = if (dark) ShellDarkLow else PlateLight
    Box(
        modifier = Modifier.size(if (big) 24.dp else 16.dp).popIn().clip(CircleShape).background(ring),
        contentAlignment = Alignment.Center
    ) {
        Box(Modifier.size(if (big) 16.dp else 10.dp).clip(CircleShape).background(presenceColor(status)))
    }
}

@Composable
private fun danger() =
    if (LocalEmufiiDarkTheme.current) ErrorDark else ErrorLight

private val STEP = 108.dp

private val CardShape = RoundedCornerShape(28.dp)

private val CARD_HEIGHT = 232.dp
