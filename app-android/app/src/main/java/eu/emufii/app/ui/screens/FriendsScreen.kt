package eu.emufii.app.ui.screens

import eu.emufii.app.ui.components.upToHeader
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.emufii.app.R
import eu.emufii.app.profile.AddFriendResult
import eu.emufii.app.profile.Friend
import eu.emufii.app.profile.FriendStatus
import eu.emufii.app.profile.FriendStore
import eu.emufii.app.profile.Profile
import eu.emufii.app.profile.playerDisplayName
import eu.emufii.app.secondscreen.rememberPresentationDisplay
import eu.emufii.app.settings.SettingsStore
import eu.emufii.app.ui.Cascade
import eu.emufii.app.ui.EntryScroll
import eu.emufii.app.ui.LocalEntryScroll
import eu.emufii.app.ui.Sfx
import eu.emufii.app.ui.TrailerSpinner
import eu.emufii.app.ui.components.Avatar
import eu.emufii.app.ui.components.CrossIcon
import eu.emufii.app.ui.components.EmufiiScaffold
import eu.emufii.app.ui.components.GhostButton
import eu.emufii.app.ui.components.PadDialog
import eu.emufii.app.ui.components.PadTextField
import eu.emufii.app.ui.components.SoftCard
import eu.emufii.app.ui.components.padEntry
import eu.emufii.app.ui.copyToClipboard
import eu.emufii.app.ui.popIn
import eu.emufii.app.ui.theme.ErrorDark
import eu.emufii.app.ui.theme.ErrorLight
import eu.emufii.app.ui.theme.GoodDark
import eu.emufii.app.ui.theme.GoodLight
import eu.emufii.app.ui.theme.LocalEmufiiDarkTheme
import eu.emufii.app.ui.theme.PlateLight
import eu.emufii.app.ui.theme.ShellDarkLow
import eu.emufii.app.ui.theme.WarnDark
import eu.emufii.app.ui.theme.WarnLight

/**
 * Your friends, and what they're playing. The screen draws, it no longer asks:
 * presence is polled once for the whole app by [eu.emufii.app.notify.FriendWatcher],
 * two pollers having announced the same arrival twice. No browsing, only codes: the
 * coordinator holds no list of who knows whom.
 *
 * One card says who you are and takes a code; the list below is a grid of short rows,
 * a status only when there is one to give. Every sentence that explained the page is gone.
 * pourquoi : docs/decisions/matiere-et-mouvement-trailer.md § Friends
 */
@Composable
fun FriendsScreen(
    profile: Profile,
    friendStore: FriendStore,
    statuses: Map<String, FriendStatus>,
    onJoin: (code: String, romTitleId: String?, romTitle: String?) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val friends by friendStore.friends.collectAsStateWithLifecycle()
    var input by remember { mutableStateOf("") }
    var addError by remember { mutableStateOf<String?>(null) }
    // The friend just added pops into the list; the others are already there.
    var justAdded by remember { mutableStateOf<String?>(null) }
    var pendingRemoval by remember { mutableStateOf<Friend?>(null) }

    val invalidMessage = stringResource(R.string.friends_error_invalid)
    val duplicateMessage = stringResource(R.string.friends_error_duplicate)
    val selfMessage = stringResource(R.string.friends_error_self)

    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    // In a game, then online, then the rest by name: the actionable rows come first.
    val ordered = friends.sortedWith(
        compareByDescending<Friend> { statuses[it.code]?.inSession == true }
            .thenByDescending { statuses[it.code]?.online == true }
            .thenBy { (it.name ?: it.displayCode).lowercase() }
    )
    val onlineCount = ordered.count { statuses[it.code]?.online == true }

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
        when (val result = friendStore.add(input, profile.id)) {
            is AddFriendResult.Added -> {
                input = ""
                addError = null
                justAdded = result.friend.code
                Sfx.confirm()
            }
            AddFriendResult.Invalid -> addError = invalidMessage
            AddFriendResult.AlreadyAdded -> addError = duplicateMessage
            AddFriendResult.Self -> addError = selfMessage
        }
    }
    val onInputChange: (String) -> Unit = { input = it; addError = null }

    val configuration = LocalConfiguration.current
    val wide = configuration.screenWidthDp > configuration.screenHeightDp

    // The setting is not enough: the device may have only one screen.
    // pourquoi : docs/decisions/second-ecran.md § The friends list goes to the back, both cards stay in front
    val panelDisplay by rememberPresentationDisplay()
    val panelWanted by remember(context) { SettingsStore.get(context).secondScreen }
        .collectAsStateWithLifecycle()
    val panelLive = panelWanted && panelDisplay != null

    EmufiiScaffold(title = stringResource(R.string.friends_title), onBack = onBack, modifier = modifier) { topPadding ->
        // One scrolling document, rendered eagerly: a friends list is counted in tens, and
        // Compose refuses two nested vertical scrolls.
        val pageScroll = rememberScrollState()
        val page = remember(pageScroll) { EntryScroll(pageScroll) }
        CompositionLocalProvider(LocalEntryScroll provides page) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(pageScroll)
                    .padding(start = 20.dp, end = 20.dp, top = topPadding, bottom = bottomInset + 40.dp),
                // Centred when the panel carries the list: the front then holds one card.
                // pourquoi : docs/decisions/second-ecran.md § The friends list goes to the back, both cards stay in front
                verticalArrangement =
                    if (panelLive) Arrangement.spacedBy(18.dp, Alignment.CenterVertically)
                    else Arrangement.spacedBy(14.dp)
            ) {
                Cascade(0) {
                    IdentityCard(
                        code = profile.friendCode,
                        onCopy = onCopyCode,
                        onShare = onShareCode,
                        value = input,
                        onValueChange = onInputChange,
                        error = addError,
                        onAdd = onAddFriend,
                        wide = wide
                    )
                }

                when {
                    ordered.isEmpty() -> Cascade(1) { EmptyFriends() }
                    panelLive -> Cascade(1) {
                        CountLine(ordered.size, onlineCount, note = stringResource(R.string.friends_on_panel))
                    }
                    else -> {
                        Cascade(1) { CountLine(ordered.size, onlineCount) }
                        val columns = if (wide) 2 else 1
                        ordered.chunked(columns).forEachIndexed { row, pair ->
                            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                pair.forEachIndexed { column, friend ->
                                    key(friend.code) {
                                        Cascade(
                                            index = 2 + row * columns + column,
                                            pop = friend.code == justAdded,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            FriendRow(
                                                friend = friend,
                                                status = statuses[friend.code] ?: FriendStatus.Offline,
                                                onJoin = onJoin,
                                                onRemove = { pendingRemoval = friend }
                                            )
                                        }
                                    }
                                }
                                // The odd one keeps half the width, like a slot left empty.
                                repeat(columns - pair.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                }
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

/**
 * Who you are and who to add, in one card: the code on one side, big enough to read out
 * at arm's length, the field on the other. Stacked when the screen is narrow.
 */
@Composable
private fun IdentityCard(
    code: String,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    value: String,
    onValueChange: (String) -> Unit,
    error: String?,
    onAdd: () -> Unit,
    wide: Boolean,
) {
    SoftCard {
        val mine: @Composable (Modifier) -> Unit = { m ->
            Column(m, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    stringResource(R.string.friends_my_code),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    code,
                    style = MaterialTheme.typography.headlineSmall,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                    maxLines = 1
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GhostButton(
                        label = stringResource(R.string.friends_copy),
                        onClick = onCopy,
                        // The screen's first control: where the cursor arrives from the header.
                        modifier = Modifier.padEntry()
                    )
                    GhostButton(
                        label = stringResource(R.string.friends_share),
                        onClick = onShare,
                        // Up is the header, not the field in the next column, which sits higher.
                        modifier = Modifier.upToHeader()
                    )
                }
            }
        }
        val add: @Composable (Modifier) -> Unit = { m ->
            Column(m, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    stringResource(R.string.friends_add),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    PadTextField(
                        value = value,
                        onValueChange = onValueChange,
                        placeholder = stringResource(R.string.friends_add_hint),
                        isError = error != null,
                        modifier = Modifier.weight(1f).then(if (wide) Modifier.upToHeader() else Modifier)
                    )
                    GhostButton(
                        label = stringResource(R.string.friends_add_action),
                        onClick = onAdd,
                        modifier = if (wide) Modifier.upToHeader() else Modifier
                    )
                }
                error?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = danger())
                }
            }
        }
        if (wide) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(20.dp).height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                mine(Modifier.weight(1f))
                Hairline(vertical = true)
                add(Modifier.weight(1.2f))
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                mine(Modifier.fillMaxWidth())
                Hairline(vertical = false)
                add(Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun Hairline(vertical: Boolean) {
    val color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    Box(
        if (vertical) Modifier.width(1.dp).fillMaxHeight().background(color)
        else Modifier.height(1.dp).fillMaxWidth().background(color)
    )
}

/** "3 friends · 1 online", and on a two-screen device where the list went. */
@Composable
private fun CountLine(total: Int, online: Int, note: String? = null) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
    ) {
        Text(
            pluralStringResource(R.plurals.friends_count, total, total),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (online > 0) {
            OnlineDot()
            Text(
                stringResource(R.string.friends_count_online, online),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.weight(1f))
        note?.let {
            Text(
                it,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun OnlineDot() {
    Box(
        Modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(if (LocalEmufiiDarkTheme.current) GoodDark else GoodLight)
    )
}

/**
 * Avatar, name, and a status only when there is one: an offline friend is a name in the
 * muted ink, not a row saying "Offline". Join when joinable, a small cross otherwise.
 */
@Composable
private fun FriendRow(
    friend: Friend,
    status: FriendStatus,
    onJoin: (code: String, romTitleId: String?, romTitle: String?) -> Unit,
    onRemove: () -> Unit,
) {
    val name = friend.name?.let { playerDisplayName(it) } ?: friend.displayCode
    val joinable = status.sessionCode != null && status.ready
    val join: (() -> Unit)? =
        if (joinable) ({ onJoin(status.sessionCode, status.romTitleId, status.romTitle) }) else null
    val line = when {
        status.romTitle != null -> status.romTitle
        status.inSession -> stringResource(R.string.friends_playing_unknown)
        status.online -> stringResource(R.string.friends_online)
        else -> null
    }

    SoftCard(onClick = join) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(contentAlignment = Alignment.BottomEnd) {
                Avatar(name = name, size = 40.dp)
                if (status.online || status.inSession) PresenceDot(status)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (line != null) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                line?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            when {
                join != null -> GhostButton(label = stringResource(R.string.friends_join), onClick = join)
                // Their session exists but their tunnel isn't up: joining would only spin.
                status.inSession -> TrailerSpinner(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    size = 18.dp,
                    stroke = 2.5.dp,
                    fps = 30
                )
                else -> GhostButton(
                    label = stringResource(R.string.friends_remove),
                    onClick = onRemove,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    icon = { tint -> CrossIcon(size = 14.dp, color = tint) }
                )
            }
        }
    }
}

/** In a game = warning (the room holds them), online = good. */
@Composable
private fun PresenceDot(status: FriendStatus) {
    val dark = LocalEmufiiDarkTheme.current
    // The dot's ring stays a theme surface: the low shell on dark, the plate on light.
    val ring = if (dark) ShellDarkLow else PlateLight
    val fill = when {
        status.inSession -> if (dark) WarnDark else WarnLight
        else -> if (dark) GoodDark else GoodLight
    }
    Box(
        modifier = Modifier.size(14.dp).popIn().clip(CircleShape).background(ring),
        contentAlignment = Alignment.Center
    ) {
        Box(Modifier.size(9.dp).clip(CircleShape).background(fill))
    }
}

@Composable
private fun EmptyFriends() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text("👋", fontSize = 30.sp)
        Text(
            stringResource(R.string.friends_none_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Text(
            stringResource(R.string.friends_none_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun danger() =
    if (LocalEmufiiDarkTheme.current) ErrorDark else ErrorLight
