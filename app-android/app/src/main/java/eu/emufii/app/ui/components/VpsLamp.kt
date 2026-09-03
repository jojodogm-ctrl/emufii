package eu.emufii.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import eu.emufii.app.R
import eu.emufii.app.secondscreen.VpsState
import eu.emufii.app.secondscreen.VpsStatus
import eu.emufii.app.ui.theme.ErrorDark
import eu.emufii.app.ui.theme.ErrorLight
import eu.emufii.app.ui.theme.GoodDark
import eu.emufii.app.ui.theme.GoodLight
import eu.emufii.app.ui.theme.LocalEmufiiDarkTheme

/**
 * The service light: a lit dot and two words. Its own colour, never the app's accent.
 * It lives here rather than on the rear panel because the main screen asks for it too,
 * and the panel delegates nothing.
 * pourquoi : docs/decisions/second-ecran.md § The service light has its own colour
 */
@Composable
fun VpsLamp(modifier: Modifier = Modifier, dotSize: Dp = 15.dp) {
    // The poll lives with the lamp, its only consumer; [VpsStatus.keepPolling] keeps one
    // loop when both screens draw it together.
    LaunchedEffect(Unit) { VpsStatus.keepPolling() }

    val state by VpsStatus.state.collectAsStateWithLifecycle()
    val dark = LocalEmufiiDarkTheme.current

    val tone = when (state) {
        VpsState.ONLINE -> if (dark) GoodDark else GoodLight
        VpsState.OFFLINE -> if (dark) ErrorDark else ErrorLight
        // Grey while nothing is known: writing "down" for a handheld in a tunnel would
        // blame our machine for the train.
        VpsState.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(dotSize)
                .shadow(
                    elevation = if (state == VpsState.UNKNOWN) 0.dp else 12.dp,
                    shape = CircleShape,
                    clip = false,
                    ambientColor = tone,
                    spotColor = tone
                )
                .clip(CircleShape)
                .background(tone)
        )
        Column {
            Text(
                stringResource(R.string.panel_vps),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                stringResource(
                    when (state) {
                        VpsState.ONLINE -> R.string.panel_vps_online
                        VpsState.OFFLINE -> R.string.panel_vps_offline
                        VpsState.UNKNOWN -> R.string.panel_vps_unknown
                    }
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
