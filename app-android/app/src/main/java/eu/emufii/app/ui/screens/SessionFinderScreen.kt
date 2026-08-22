package eu.emufii.app.ui.screens

import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.emufii.app.library.Rom
import eu.emufii.app.library.RomsRepository
import eu.emufii.app.ui.components.RomArtwork
import eu.emufii.app.ui.components.PadTextField
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import eu.emufii.app.R
import eu.emufii.app.profile.playerDisplayName
import eu.emufii.app.network.CoordinatorClient
import androidx.compose.ui.platform.LocalContext
import eu.emufii.app.library.Console
import eu.emufii.app.network.OpenSession
import eu.emufii.app.ps2.Ps2NetworkProfile
import eu.emufii.app.ui.components.Avatar
import eu.emufii.app.ui.components.EmufiiScaffold
import eu.emufii.app.ui.components.GhostButton
import eu.emufii.app.ui.components.SectionHeader
import eu.emufii.app.ui.components.SoftCard
import eu.emufii.app.ui.components.padEntry
import eu.emufii.app.ui.components.SignalMark
import eu.emufii.app.ui.theme.socket
import eu.emufii.app.ui.theme.TileShape
import eu.emufii.app.ui.theme.plate
import eu.emufii.app.ui.theme.LocalEmufiiOledTheme
import eu.emufii.app.ui.theme.LocalEmufiiDarkTheme
import kotlinx.coroutines.delay

/**
 * Every session currently open, joinable in one tap.
 *
 * Polls rather than pushes: sessions last an hour at most and the list is
 * small, so a socket would be a lot of machinery for a screen you sit on for
 * twenty seconds.
 */
@Composable
fun SessionFinderScreen(
    client: CoordinatorClient,
    /**
     * The local library, to put a face on a session.
     *
     * The coordinator only knows a title: it has neither cover art nor console to
     * offer, and it has no business having any, these being ROMs, which live on
     * the device. So we match the announced title against what we have locally,
     * and when it lands the card shows the game's real icon. Otherwise it shows
     * the host: a session stays identifiable by whoever opens it.
     */
    romsRepo: RomsRepository,
    onBack: () -> Unit,
    onJoin: (OpenSession) -> Unit
) {
    var sessions by remember { mutableStateOf<List<OpenSession>>(emptyList()) }
    var library by remember { mutableStateOf<List<Rom>>(emptyList()) }
    var query by remember { mutableStateOf("") }

    // The cache, never a fresh scan: opening the session list must not trigger a
    // read of a multi-GB SAF tree.
    LaunchedEffect(Unit) {
        library = withContext(Dispatchers.IO) { runCatching { romsRepo.scan() }.getOrDefault(emptyList()) }
    }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    // Read outside the effect: stringResource needs a composable scope, and the
    // fallback used to be a French literal that showed up in an English app.
    val unreachable = stringResource(R.string.finder_unreachable)

    LaunchedEffect(Unit) {
        while (true) {
            client.listSessions()
                .onSuccess { sessions = it; error = null }
                // Never `it.message`: an unreachable coordinator carries the
                // IOException's text, which names a host and a port and means
                // nothing to a player.
                .onFailure { error = unreachable }
            loading = false
            delay(REFRESH_MS)
        }
    }

    // The filter covers what is read on the card: the game, the host, the code.
    // Searching the code is as useful as searching a title, that being what a
    // friend sends you in a message.
    val shown = remember(sessions, query) {
        val q = query.trim()
        if (q.isBlank()) sessions
        else sessions.filter {
            listOfNotNull(it.romTitle, it.hostName, it.code)
                .any { field -> field.contains(q, ignoreCase = true) }
        }
    }

    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    EmufiiScaffold(
        title = stringResource(R.string.finder_title),
        onBack = onBack
    ) { topPadding ->
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

            error != null -> FinderMessage(
                mark = { tint -> SignalMark(color = tint) },
                title = stringResource(R.string.finder_unreachable),
                subtitle = error!!,
                topPadding = topPadding
            )

            sessions.isEmpty() && query.isBlank() -> FinderMessage(
                // Not a drawn mark at all: an empty socket, the same recess the
                // library leaves in its last row. "Nobody yet" is a slot with no
                // game in it, and the tray already has a word for that — two
                // attempts at a crescent moon proved only that a borrowed
                // metaphor was never going to say it as plainly.
                mark = null,
                title = stringResource(R.string.finder_nobody_yet),
                subtitle = stringResource(R.string.finder_empty),
                topPadding = topPadding
            )

            else -> LazyColumn(
                contentPadding = PaddingValues(
                    start = 20.dp, end = 20.dp,
                    top = topPadding,
                    bottom = bottomInset + 24.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                item {
                    PadTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = stringResource(R.string.finder_search),
                        modifier = Modifier.fillMaxWidth().padEntry()
                    )
                }
                item { SectionHeader(pluralSessions(shown.size)) }
                if (shown.isEmpty()) {
                    item {
                        // A search with no results is not an empty list: saying
                        // "nobody" here would suggest the sessions had vanished
                        // when we have simply filtered too hard.
                        Text(
                            stringResource(R.string.finder_no_match),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
                items(shown, key = { it.code }) { session ->
                    SessionCard(
                        session = session,
                        rom = library.firstOrNull { rom ->
                            rom.displayName.equals(session.romTitle, ignoreCase = true)
                        },
                        onJoin = { onJoin(session) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SessionCard(session: OpenSession, rom: Rom?, onJoin: () -> Unit) {
    val dark = LocalEmufiiDarkTheme.current
    val host = session.hostName?.let { playerDisplayName(it) }
        ?: stringResource(R.string.finder_host)

    // Only knowable for a game we own: the coordinator publishes a title, not a
    // console.
    val ps2Blocked = rom?.console == Console.PS2 &&
        !Ps2NetworkProfile.isReady(LocalContext.current)

    // The card is the "down" destination from the header, and not the pill it
    // contains: a `GhostButton`'s modifier applies to its frame, which is not
    // focusable, so the request failed there silently. The card is clickable, and
    // is a real focus node.
    SoftCard(
        onClick = onJoin,
        modifier = Modifier.animateContentSize().padEntry()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // The cover art when we have the game, the host otherwise. No empty
            // square in the middle: a card with no visual is worse than a card
            // showing something else that is true.
            if (rom != null) RomArtwork(rom = rom, size = 64.dp)
            else Avatar(name = host, size = 56.dp)

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    session.romTitle ?: stringResource(R.string.finder_unknown_game),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // The console comes from the local ROM: the coordinator does
                    // not know it, and guessing it from a title would be a bet.
                    rom?.let { MetaChip(it.console.label) }
                    // The ROM's identifier, when it has one, is what tells two
                    // editions of the same game apart, and therefore the only
                    // honest answer to "is this really my version?".
                    (rom?.titleIdHex ?: rom?.productCode)?.let { MetaChip(it) }
                    MetaChip(session.code)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "$host · ${playersLabel(session.players)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Box(
                modifier = Modifier.defaultMinSize(minHeight = 36.dp),
                contentAlignment = Alignment.Center
            ) {
                if (session.ready && !ps2Blocked) {
                    GhostButton(
                        label = stringResource(R.string.finder_join),
                        onClick = onJoin
                    )
                } else if (ps2Blocked) {
                    // Joining would come back with the same refusal as the
                    // launch card: a PS2 game whose memory card carries no
                    // network profile never opens its local menu. Said on the
                    // card, so the session does not look joinable when it is
                    // not.
                    Text(
                        stringResource(R.string.finder_ps2_profile),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    // The host's tunnel isn't up yet; joining now would just
                    // spin. Say so rather than offer a button that stalls.
                    Text(
                        stringResource(R.string.finder_starting),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun playersLabel(n: Int): String = when (n) {
    0 -> stringResource(R.string.finder_nobody_yet)
    1 -> stringResource(R.string.finder_one_player)
    else -> stringResource(R.string.finder_n_players, n)
}

/** "3 sessions en cours" / "3 sessions in progress", plural per language. */
@Composable
private fun pluralSessions(count: Int): String {
    val sessions = if (count == 1) stringResource(R.string.finder_one_session, count)
    else stringResource(R.string.finder_many_sessions, count)
    return stringResource(R.string.finder_in_progress, sessions)
}

@Composable
private fun FinderMessage(
    mark: (@Composable (Color) -> Unit)?,
    title: String,
    subtitle: String,
    topPadding: androidx.compose.ui.unit.Dp
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = topPadding, start = 32.dp, end = 32.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // A mark sits on its own moulded disc, the same object as the header's
        // round button: an empty state is still part of the tray, not a gap in
        // it. With no mark, the disc gives way to the socket itself.
        if (mark != null) {
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .plate(
                        shape = CircleShape,
                        dark = LocalEmufiiDarkTheme.current,
                        oled = LocalEmufiiOledTheme.current,
                        lift = 6.dp
                    ),
                contentAlignment = Alignment.Center
            ) { mark(MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .socket(TileShape, LocalEmufiiDarkTheme.current)
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall,
            // This Column sits straight on the wallpaper, so nothing supplies a
            // content colour and the title falls back to black, invisible in
            // dark mode. Every Text outside a Surface has to name its own.
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(10.dp))
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

private const val REFRESH_MS = 4000L

/**
 * A metadata pill: console, ROM id, session code.
 *
 * Three short facts lined up, rather than a sentence separated by full stops. A
 * sentence has to be read whole to extract one detail; pills are scanned, which
 * is what people do in front of a list of sessions.
 */
@Composable
private fun MetaChip(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    )
}
