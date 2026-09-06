package eu.emufii.app.ui.screens.session

import android.icu.text.ListFormatter
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.emufii.app.R
import eu.emufii.app.network.Member
import eu.emufii.app.profile.playerDisplayName
import eu.emufii.app.ui.components.AvatarStack
import eu.emufii.app.ui.components.SectionHeader
import eu.emufii.app.ui.components.SoftCard
import eu.emufii.app.ui.components.softCardFill

@Composable
internal fun CodeCard(code: String, isHost: Boolean) {
    SoftCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            SectionHeader(stringResource(R.string.session_code_label))
            Text(
                code.ifBlank { "—" },
                fontSize = 44.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Black,
                letterSpacing = 4.sp,
                color = coralText()
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

/** pourquoi : docs/decisions/session.md § This screen's drawing decisions */
@Composable
internal fun PresenceCard(
    youName: String,
    others: List<Member>,
    isHost: Boolean,
    live: Boolean,
    modifier: Modifier = Modifier,
    /**
     * True only in the pane: the single-column page already scrolls, and Compose throws when
     * measuring scrolling content unbounded.
     * pourquoi : docs/decisions/session.md § This screen's drawing decisions
     */
    scrollable: Boolean = false
) {
    val scroll = rememberScrollState()
    // A line cut in half reads as a rendering glitch, one fading into the card's background as
    // "there is more"; lit only when something is left below the fold.
    val fill = softCardFill()
    val fade = scrollable && scroll.canScrollForward

    SoftCard(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // BEFORE the scroll: after, it works in the unrolled content's coordinates
                // and lands below the fold, invisible.
                // pourquoi : docs/decisions/session.md § This screen's drawing decisions
                .then(
                    if (!fade) Modifier else Modifier.drawWithContent {
                        drawContent()
                        val h = FADE_HEIGHT.toPx()
                        drawRect(
                            // Opaque before the edge, not at it: a linear run left the last
                            // line legible and sliced.
                            // pourquoi : docs/decisions/session.md § This screen's drawing decisions
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
                // Nothing is live while we are not hearing back: the dot would vouch for a list
                // we can no longer refresh.
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
 * Enough to erase a whole line and its leading: 28 dp left the cut line half legible.
 * pourquoi : docs/decisions/session.md § This screen's drawing decisions
 */
private val FADE_HEIGHT = 44.dp

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
                .background(good())
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
internal fun ConnectionCard(
    hostIp: String,
    addressLabel: String,
    /** Null when the console does not ask for one, the column then disappears. */
    port: String?,
    romName: String?,
    /**
     * False in the pane, where its forty dp are what clipped the card, and a game name is not
     * a state you act on.
     * pourquoi : docs/decisions/session.md § This screen's drawing decisions
     */
    showGame: Boolean = true
) {
    SoftCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
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
            // No copy buttons: Emufii fills the form, and the clipboard holds one value at a time.
            // pourquoi : docs/decisions/session.md § Copying the address stopped making sense once Emufii fills it in
        }
    }
}

/** ICU does it: the conjunction and the comma placement differ per locale. */
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
