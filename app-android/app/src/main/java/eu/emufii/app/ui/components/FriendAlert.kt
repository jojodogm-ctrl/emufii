package eu.emufii.app.ui.components

import eu.emufii.app.ui.ShadowsFollow
import eu.emufii.app.ui.Sfx
import eu.emufii.app.ui.popIn
import eu.emufii.app.ui.bloom
import eu.emufii.app.ui.rememberAppear
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.emufii.app.R
import eu.emufii.app.notify.FriendEvent
import eu.emufii.app.ui.LocalRingTone
import eu.emufii.app.ui.RingTone
import kotlinx.coroutines.delay

@Composable
fun FriendAlert(
    event: FriendEvent?,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(event) {
        if (event != null) {
            Sfx.alert()
            delay(VISIBLE_MS)
            onDismiss()
        }
    }

    // Not AnimatedVisibility: its fade buffer clips the card's shadow square.
    val appear by rememberAppear(event != null)
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopEnd) {
        if (event != null || appear > 0.001f) ShadowsFollow({ appear }) {
            // Held across the exit animation, or the text blanks when event goes null.
            val shown = lastNonNull(event)
            CompositionLocalProvider(LocalRingTone provides RingTone.CORAL) {
            SoftCard(
                onClick = onOpen,
                modifier = Modifier
                    .padding(WindowInsets.statusBars.asPaddingValues())
                    .bloom({ appear }, blur = 0.dp, rise = 30.dp)
                    .padding(vertical = 8.dp)
                    // The top bar's free gap on a 1920 px screen: 334 dp starting 188 dp from the right edge.
                    .padding(end = ALERT_END_INSET)
                    .widthIn(max = ALERT_WIDTH)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    val name = shown?.name ?: stringResource(R.string.notify_friend_unnamed)
                    shown?.let { FriendBadge(it, size = 42.dp, modifier = Modifier.popIn()) }
                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text(
                            name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            when (shown) {
                                is FriendEvent.StartedPlaying -> shown.game
                                    ?.let { stringResource(R.string.alert_friend_playing, it) }
                                    ?: stringResource(R.string.alert_friend_in_game)
                                else -> stringResource(R.string.alert_friend_online)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            }
        }
    }
}

/** Compose keeps the composable alive through the exit animation, with nothing left to draw. */
@Composable
private fun lastNonNull(event: FriendEvent?): FriendEvent? {
    val holder = androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf<FriendEvent?>(null)
    }
    if (event != null) holder.value = event
    return holder.value
}

private val ALERT_WIDTH = 320.dp

private val ALERT_END_INSET = 196.dp

private const val VISIBLE_MS = 4_000L
