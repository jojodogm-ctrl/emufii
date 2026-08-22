package eu.emufii.app.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import eu.emufii.app.R
import eu.emufii.app.profile.playerDisplayName
import eu.emufii.app.network.CoordinatorClient
import eu.emufii.app.profile.AddFriendResult
import eu.emufii.app.profile.Friend
import eu.emufii.app.profile.FriendStatus
import eu.emufii.app.profile.FriendStore
import eu.emufii.app.profile.Profile
import eu.emufii.app.ui.components.Avatar
import eu.emufii.app.ui.components.EmufiiScaffold
import eu.emufii.app.ui.components.CrossIcon
import eu.emufii.app.ui.components.GhostButton
import eu.emufii.app.ui.components.PadDialog
import eu.emufii.app.ui.components.PadDialogText
import eu.emufii.app.ui.components.PadTextField
import eu.emufii.app.ui.components.SectionHeader
import eu.emufii.app.ui.components.SoftCard
import eu.emufii.app.ui.components.padEntry
import eu.emufii.app.ui.copyToClipboard
import eu.emufii.app.ui.theme.LocalEmufiiDarkTheme
import eu.emufii.app.ui.theme.ShellRed
import kotlinx.coroutines.delay

/**
 * Your friends, and what they're playing.
 *
 * Like the finder, this polls: the answer is a handful of rows and it only
 * matters while the screen is up. Unlike the finder, it asks about specific
 * codes, there is no browsing here, because the coordinator holds no list of
 * who knows whom. Everything social about this feature lives on the device.
 */
@Composable
fun FriendsScreen(
    profile: Profile,
    friendStore: FriendStore,
    client: CoordinatorClient,
    onJoin: (code: String, romTitleId: String?, romTitle: String?) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val friends by friendStore.friends.collectAsState()
    var statuses by remember { mutableStateOf<Map<String, FriendStatus>>(emptyMap()) }
    var input by remember { mutableStateOf("") }
    var addError by remember { mutableStateOf<String?>(null) }
    var pendingRemoval by remember { mutableStateOf<Friend?>(null) }

    val invalidMessage = stringResource(R.string.friends_error_invalid)
    val duplicateMessage = stringResource(R.string.friends_error_duplicate)
    val selfMessage = stringResource(R.string.friends_error_self)

    // Keyed on the codes themselves: adding or removing a friend restarts the
    // poll straight away, so a new row isn't stuck on "offline" for a full
    // cycle while the answer is already known.
    val codes = friends.map { it.code }
    LaunchedEffect(codes) {
        while (true) {
            client.friendStatuses(codes).onSuccess { fresh ->
                statuses = codes.associateWith { code ->
                    fresh[code]?.let { p ->
                        FriendStatus(
                            online = true,
                            sessionCode = p.sessionCode,
                            romTitle = p.romTitle,
                            romTitleId = p.romTitleId,
                            players = p.players,
                            ready = p.ready
                        )
                    } ?: FriendStatus.Offline
                }
                friendStore.noteNames(fresh.mapNotNull { (c, p) -> p.name?.let { c to it } }.toMap())
            }
            delay(REFRESH_MS)
        }
    }

    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    // In a game first, then merely online, then everyone else by name: the rows
    // you can act on are the ones worth putting under the thumb.
    val ordered = friends.sortedWith(
        compareByDescending<Friend> { statuses[it.code]?.inSession == true }
            .thenByDescending { statuses[it.code]?.online == true }
            .thenBy { (it.name ?: it.displayCode).lowercase() }
    )

    val onCopyCode = { copyToClipboard(context, "Emufii", profile.friendCode) }
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
        when (friendStore.add(input, profile.id)) {
            is AddFriendResult.Added -> { input = ""; addError = null }
            AddFriendResult.Invalid -> addError = invalidMessage
            AddFriendResult.AlreadyAdded -> addError = duplicateMessage
            AddFriendResult.Self -> addError = selfMessage
        }
    }
    val onInputChange: (String) -> Unit = { input = it; addError = null }

    val configuration = LocalConfiguration.current
    val landscape = configuration.screenWidthDp > configuration.screenHeightDp

    EmufiiScaffold(title = stringResource(R.string.friends_title), onBack = onBack, modifier = modifier) { topPadding ->
        if (landscape) {
            // The whole page scrolls, as one movement.
            //
            // Before, only the list scrolled inside its column: the code card
            // stayed frozen while the other half moved, which gave two screens
            // side by side rather than one. Here the page is a single document,
            // the pair of cards, then the friends, and the list is rendered
            // eagerly rather than as a `LazyColumn`, two nested vertical scrolls
            // not being something Compose accepts. A friends list is counted in
            // tens, not thousands.
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(
                        start = 20.dp, end = 20.dp,
                        // Past the gesture handle, not level with it: the
                        // footnote used to come to rest struck through by it.
                        // Clearance alone was not enough — it only slid the line
                        // a dozen pixels — so the gap above it pushes it clear of
                        // the fold as well, where it is read after scrolling
                        // rather than half-read at rest.
                        top = topPadding, bottom = bottomInset + 56.dp
                    ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Two cards of equal width and equal height. `IntrinsicSize.Min`
                // gives the row the height of the taller of the two, and
                // `fillMaxHeight` makes the other take it: they answer each other
                // instead of sitting out of step.
                Row(
                    modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    MyCodeCard(
                        code = profile.friendCode,
                        onCopy = onCopyCode,
                        onShare = onShareCode,
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                    AddFriendCard(
                        value = input,
                        onValueChange = onInputChange,
                        error = addError,
                        onAdd = onAddFriend,
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                }

                if (ordered.isEmpty()) {
                    EmptyFriends()
                } else {
                    SectionHeader(stringResource(R.string.friends_list))
                    ordered.forEach { friend ->
                        FriendRow(
                            friend = friend,
                            status = statuses[friend.code] ?: FriendStatus.Offline,
                            onJoin = { code, titleId, title -> onJoin(code, titleId, title) },
                            onRemove = { pendingRemoval = friend }
                        )
                    }
                    Text(
                        stringResource(R.string.friends_privacy_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }
            return@EmufiiScaffold
        }

        LazyColumn(
            contentPadding = PaddingValues(
                start = 20.dp, end = 20.dp,
                top = topPadding,
                bottom = bottomInset + 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item {
                MyCodeCard(code = profile.friendCode, onCopy = onCopyCode, onShare = onShareCode)
            }

            item {
                AddFriendCard(
                    value = input,
                    onValueChange = onInputChange,
                    error = addError,
                    onAdd = onAddFriend
                )
            }

            if (ordered.isEmpty()) {
                item { EmptyFriends() }
            } else {
                item { SectionHeader(stringResource(R.string.friends_list)) }
                items(ordered, key = { it.code }) { friend ->
                    FriendRow(
                        friend = friend,
                        status = statuses[friend.code] ?: FriendStatus.Offline,
                        onJoin = { code, titleId, title -> onJoin(code, titleId, title) },
                        onRemove = { pendingRemoval = friend }
                    )
                }
                item {
                    Text(
                        stringResource(R.string.friends_privacy_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }
        }
    }

    pendingRemoval?.let { friend ->
        val label = friend.name?.let { playerDisplayName(it) } ?: friend.displayCode
        PadDialog(
            title = stringResource(R.string.friends_remove),
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
                    tint = DANGER
                )
            }
        ) {
            PadDialogText(stringResource(R.string.friends_remove_confirm, label))
        }
    }
}

/**
 * The code, big enough to read off the screen at arm's length, which is how it
 * gets shared most of the time, one person holding their handheld out to
 * another.
 */
@Composable
private fun MyCodeCard(
    code: String,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier
) {
    SoftCard(modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxWidth().fillMaxHeight().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically)
        ) {
            Text(
                code,
                style = MaterialTheme.typography.headlineSmall,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                textAlign = TextAlign.Center
            )
            Text(
                stringResource(R.string.friends_my_code_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                GhostButton(
                    label = stringResource(R.string.friends_copy),
                    onClick = onCopy,
                    fillWidth = true,
                    // The screen's first control: this is where you arrive coming
                    // down from the header, and where you go back up from.
                    modifier = Modifier.weight(1f).padEntry()
                )
                GhostButton(
                    label = stringResource(R.string.friends_share),
                    onClick = onShare,
                    fillWidth = true,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun AddFriendCard(
    value: String,
    onValueChange: (String) -> Unit,
    error: String?,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier
) {
    SoftCard(modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxWidth().fillMaxHeight().padding(16.dp),
            // Centred: the card is stretched to its neighbour's height, and a
            // field pinned to the top of a half-empty card reads as an
            // oversight.
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)
        ) {
            SectionHeader(stringResource(R.string.friends_add))
            PadTextField(
                value = value,
                onValueChange = onValueChange,
                placeholder = stringResource(R.string.friends_add_hint),
                isError = error != null,
                modifier = Modifier.fillMaxWidth()
            )
            error?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = DANGER)
            }
            GhostButton(
                label = stringResource(R.string.friends_add_action),
                onClick = onAdd,
                fillWidth = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun FriendRow(
    friend: Friend,
    status: FriendStatus,
    onJoin: (code: String, romTitleId: String?, romTitle: String?) -> Unit,
    onRemove: () -> Unit
) {
    val name = friend.name?.let { playerDisplayName(it) } ?: friend.displayCode
    val joinable = status.sessionCode != null && status.ready
    val join: (() -> Unit)? =
        if (joinable) ({ onJoin(status.sessionCode, status.romTitleId, status.romTitle) }) else null

    SoftCard(onClick = join) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(contentAlignment = Alignment.BottomEnd) {
                Avatar(name = name, size = 48.dp)
                PresenceDot(status)
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    statusLine(status),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (status.inSession) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Box(
                modifier = Modifier.defaultMinSize(minHeight = 36.dp),
                contentAlignment = Alignment.Center
            ) {
                when {
                    join != null -> GhostButton(
                        label = stringResource(R.string.friends_join),
                        onClick = join
                    )
                    // Their session exists but their tunnel isn't up: joining
                    // now would only spin, same as in the finder.
                    status.inSession -> Text(
                        stringResource(R.string.friends_starting),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    else -> GhostButton(
                        label = stringResource(R.string.friends_remove),
                        onClick = onRemove,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        icon = { tint -> CrossIcon(size = 16.dp, color = tint) }
                    )
                }
            }
        }
    }
}

/** Green when in a game, amber when merely online, hollow when not there. */
@Composable
private fun PresenceDot(status: FriendStatus) {
    val dark = LocalEmufiiDarkTheme.current
    val ring = if (dark) Color(0xFF0E1116) else Color.White
    val fill = when {
        status.inSession -> Color(0xFF30A46C)
        status.online -> Color(0xFFF5A524)
        else -> if (dark) Color(0xFF3A414D) else Color(0xFFC9CED6)
    }
    Box(
        modifier = Modifier.size(16.dp).clip(CircleShape).background(ring),
        contentAlignment = Alignment.Center
    ) {
        Box(Modifier.size(11.dp).clip(CircleShape).background(fill))
    }
}

@Composable
private fun statusLine(status: FriendStatus): String = when {
    status.romTitle != null -> stringResource(R.string.friends_playing, status.romTitle)
    status.inSession -> stringResource(R.string.friends_playing_unknown)
    status.online -> stringResource(R.string.friends_online)
    else -> stringResource(R.string.friends_offline)
}

@Composable
private fun EmptyFriends(compact: Boolean = false) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = if (compact) 12.dp else 24.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // The disc disappears when the empty state follows the add card: it is
        // 72 dp plus its margins, and that is what pushed the text off the
        // screen. What people come to read is the sentence.
        if (!compact) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                // Same idea as the finder's empty state, opposite direction: a
                // white wash lifts the emoji off a pale backdrop, and would be
                // the brightest thing on screen against the dark one.
                .background(
                    if (LocalEmufiiDarkTheme.current) Color.White.copy(alpha = 0.06f)
                    else Color.White.copy(alpha = 0.55f)
                ),
            contentAlignment = Alignment.Center
        ) { Text("👋", fontSize = 32.sp) }
        Spacer(Modifier.height(16.dp))
        }
        Text(
            stringResource(R.string.friends_none_title),
            style = MaterialTheme.typography.titleMedium,
            // Laid straight on the background, hence with no inherited colour:
            // without this it falls back to black and disappears in the dark
            // theme.
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.friends_none_body),
            style = if (compact) MaterialTheme.typography.bodySmall
                    else MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

private val DANGER = ShellRed

private const val REFRESH_MS = 5000L
