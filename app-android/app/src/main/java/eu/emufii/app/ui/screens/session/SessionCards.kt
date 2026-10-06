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
import androidx.compose.runtime.collectAsState
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

@Composable
internal fun PresenceCard(
    youName: String,
    others: List<Member>,
    youPicture: java.io.File? = null,
    isHost: Boolean,
    live: Boolean,
    modifier: Modifier = Modifier,
    /** Bounded height (landscape column): the "since" lines that do not fit are left out, nothing scrolls. */
    fitted: Boolean = false
) {

    SoftCard(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
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
                if (others.isNotEmpty() && live) LiveDot()
            }
            Spacer(Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                val context = androidx.compose.ui.platform.LocalContext.current
                val pictures by eu.emufii.app.profile.AvatarSync.get(context).memberFiles.collectAsState()
                AvatarStack(
                    names = listOf(playerDisplayName(youName)) + others.map { playerDisplayName(it.name) },
                    pictures = listOf(youPicture) + others.map { pictures[it.id] },
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
                enter = fadeIn() + expandVertically(),
                modifier = if (fitted) Modifier.weight(1f, fill = false) else Modifier
            ) {
                FitLines(Modifier.padding(top = 12.dp)) {
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

/** A column that only places the lines that fit whole in its height. */
@Composable
private fun FitLines(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    androidx.compose.ui.layout.Layout(content, modifier) { measurables, constraints ->
        val loose = constraints.copy(minHeight = 0, maxHeight = androidx.compose.ui.unit.Constraints.Infinity)
        val placeables = measurables.map { it.measure(loose) }
        var height = 0
        val shown = placeables.takeWhile { p ->
            (height + p.height <= constraints.maxHeight).also { if (it) height += p.height }
        }
        layout(shown.maxOfOrNull { it.width } ?: 0, height) {
            var y = 0
            shown.forEach { it.place(0, y); y += it.height }
        }
    }
}

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
    port: String?,
    romName: String?,
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
        }
    }
}

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
