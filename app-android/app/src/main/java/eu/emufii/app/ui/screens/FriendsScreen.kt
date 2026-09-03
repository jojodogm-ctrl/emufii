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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.res.pluralStringResource
import eu.emufii.app.secondscreen.rememberPresentationDisplay
import eu.emufii.app.settings.SettingsStore
import eu.emufii.app.ui.components.PersonMark
import eu.emufii.app.ui.theme.Coral
import eu.emufii.app.ui.theme.ErrorDark
import eu.emufii.app.ui.theme.ErrorLight
import eu.emufii.app.ui.theme.GoodDark
import eu.emufii.app.ui.theme.GoodLight
import eu.emufii.app.ui.theme.InkDarkTextMuted
import eu.emufii.app.ui.theme.InkTextMuted
import eu.emufii.app.ui.theme.LocalEmufiiDarkTheme
import eu.emufii.app.ui.theme.PlateLight
import eu.emufii.app.ui.theme.ShellDarkLow
import eu.emufii.app.ui.theme.WarnDark
import eu.emufii.app.ui.theme.WarnLight
import eu.emufii.app.ui.theme.socket
import eu.emufii.app.ui.LocalRingTone
import eu.emufii.app.ui.RingTone
import eu.emufii.app.ui.copyToClipboard

/**
 * Your friends, and what they're playing. The screen draws, it no longer asks:
 * presence is polled once for the whole app by [eu.emufii.app.notify.FriendWatcher],
 * two pollers having announced the same arrival twice. No browsing, only codes: the
 * coordinator holds no list of who knows whom.
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
    var pendingRemoval by remember { mutableStateOf<Friend?>(null) }

    val invalidMessage = stringResource(R.string.friends_error_invalid)
    val duplicateMessage = stringResource(R.string.friends_error_duplicate)
    val selfMessage = stringResource(R.string.friends_error_self)

    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    // In a game, then online, then the rest by name: the actionable rows go under the thumb.
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

    // The setting is not enough: the device may have only one screen.
    // pourquoi : docs/decisions/second-ecran.md § The friends list goes to the back, both cards stay in front
    val panelDisplay by rememberPresentationDisplay()
    val panelWanted by remember(context) { SettingsStore.get(context).secondScreen }
        .collectAsStateWithLifecycle()
    val panelLive = panelWanted && panelDisplay != null

    // The social domain: the pad cursor turns coral here.
    // pourquoi : docs/decisions/theme-duotone-shelves.md § GAMEPAD FOCUS
    CompositionLocalProvider(LocalRingTone provides RingTone.CORAL) {
    EmufiiScaffold(title = stringResource(R.string.friends_title), onBack = onBack, modifier = modifier) { topPadding ->
        if (landscape) {
            // The whole page scrolls as one document; the list is rendered eagerly rather
            // than as a `LazyColumn`, Compose refusing two nested vertical scrolls. A
            // friends list is counted in tens.
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(
                        start = 20.dp, end = 20.dp,
                        // Past the gesture handle: the footnote used to rest struck through by it.
                        top = topPadding, bottom = bottomInset + 56.dp
                    ),
                // Centred when the panel carries the list: the two cards left at the top
                // emptied two thirds of the screen below.
                // pourquoi : docs/decisions/second-ecran.md § The friends list goes to the back, both cards stay in front
                verticalArrangement =
                    if (panelLive) Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
                    else Arrangement.spacedBy(12.dp)
            ) {
                // `IntrinsicSize.Min` gives the row the taller card's height, `fillMaxHeight`
                // makes the other take it: the two answer each other.
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

                if (panelLive) {
                    // Without this line a player whose friends are all offline sees a front
                    // screen that never mentions them.
                    FriendsPanelNote(
                        total = ordered.size,
                        online = ordered.count { statuses[it.code]?.online == true }
                    )
                } else if (ordered.isEmpty()) {
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
                    tint = danger()
                )
            }
        ) {
            PadDialogText(stringResource(R.string.friends_remove_confirm, label))
        }
    }
}

/**
 * The code, big enough to read off the screen at arm's length: it gets shared by
 * holding the handheld out to someone.
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
                    // The screen's first control: where the cursor arrives from the header.
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
            // The card is stretched to its neighbour's height; a field pinned to the top of
            // a half-empty card reads as an oversight.
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
                Text(it, style = MaterialTheme.typography.bodySmall, color = danger())
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
                    color = if (status.inSession) coral()
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
                    // Their session exists but their tunnel isn't up: joining would only spin.
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

/** In a game = warning (the room holds them), online = good, absent = muted. */
@Composable
private fun PresenceDot(status: FriendStatus) {
    val dark = LocalEmufiiDarkTheme.current
    // The dot's ring stays a theme surface: the low shell on dark, the plate on light.
    val ring = if (dark) ShellDarkLow else PlateLight
    val fill = when {
        status.inSession -> if (dark) WarnDark else WarnLight
        status.online -> if (dark) GoodDark else GoodLight
        else -> if (dark) InkDarkTextMuted else InkTextMuted
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
        // The disc goes when the empty state follows the add card: 72 dp plus margins is
        // what pushed the sentence off the screen.
        if (!compact) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                // A white wash lifts the emoji off a pale backdrop, and would be the
                // brightest thing on screen against the dark one.
                .background(
                    if (LocalEmufiiDarkTheme.current) PlateLight.copy(alpha = 0.06f)
                    else PlateLight.copy(alpha = 0.55f)
                ),
            contentAlignment = Alignment.Center
        ) { Text("👋", fontSize = 32.sp) }
        Spacer(Modifier.height(16.dp))
        }
        Text(
            stringResource(R.string.friends_none_title),
            style = MaterialTheme.typography.titleMedium,
            // Laid straight on the background: without this it falls back to black and
            // disappears in the dark theme.
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

@Composable
private fun danger() =
    if (LocalEmufiiDarkTheme.current) ErrorDark else ErrorLight

@Composable
private fun coral(dark: Boolean = LocalEmufiiDarkTheme.current) =
    if (dark) Coral.darkBright else Coral.ink


/**
 * The panel is behind the machine: the front screen has to say the list exists, or a
 * player with nobody online closes the page believing they have no friends.
 * pourquoi : docs/decisions/second-ecran.md § The friends list goes to the back, both cards stay in front
 */
@Composable
private fun FriendsPanelNote(total: Int, online: Int) {
    val dark = LocalEmufiiDarkTheme.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .socket(RoundedCornerShape(16.dp), dark)
            .padding(horizontal = 18.dp, vertical = 14.dp)
    ) {
        PersonMark(size = 22.dp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                if (total == 0) stringResource(R.string.friends_none_title)
                else pluralStringResource(R.plurals.friends_count, total, total) +
                    " · " + stringResource(R.string.friends_count_online, online),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                if (total == 0) stringResource(R.string.friends_none_body)
                else stringResource(R.string.friends_on_panel),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
