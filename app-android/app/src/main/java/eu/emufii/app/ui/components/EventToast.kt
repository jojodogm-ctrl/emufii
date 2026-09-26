package eu.emufii.app.ui.components

import eu.emufii.app.ui.ShadowsFollow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.emufii.app.ui.popIn
import eu.emufii.app.ui.bloom
import eu.emufii.app.ui.rememberAppear
import eu.emufii.app.ui.theme.LocalEmufiiDarkTheme
import eu.emufii.app.ui.theme.LocalEmufiiOledTheme
import eu.emufii.app.ui.theme.PillShape
import eu.emufii.app.ui.theme.plate

/**
 * A passing event, top centre: rises 30 dp into place, holds two seconds, leaves on the
 * exit spring. Nothing to dismiss; [who] puts an avatar at its head.
 * pourquoi : docs/decisions/matiere-et-mouvement-trailer.md § Recipes
 */
@Composable
fun EventToast(
    message: String?,
    modifier: Modifier = Modifier,
    who: String? = null,
    onGone: () -> Unit,
) {
    LaunchedEffect(message) {
        if (message != null) {
            kotlinx.coroutines.delay(HOLD_MS)
            onGone()
        }
    }
    // Held across the exit, or the pill leaves empty.
    var shown by remember { mutableStateOf<Pair<String, String?>?>(null) }
    if (message != null) shown = message to who
    val dark = LocalEmufiiDarkTheme.current
    // Not `AnimatedVisibility`: its fade goes through a buffer the pill's size, and the
    // pill's shadow was cut square until the fade ended.
    val appear by rememberAppear(message != null)
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        if (message != null || appear > 0.001f) ShadowsFollow({ appear }) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .padding(WindowInsets.statusBars.asPaddingValues())
                    .padding(top = 12.dp)
                    .bloom({ appear }, blur = 0.dp, rise = 30.dp)
                    .widthIn(max = 420.dp)
                    .plate(PillShape, dark, LocalEmufiiOledTheme.current && dark, lift = 16.dp)
                    .padding(start = if (shown?.second != null) 8.dp else 18.dp, end = 18.dp)
                    .padding(vertical = 8.dp)
            ) {
                shown?.second?.let { Avatar(name = it, size = 30.dp, modifier = Modifier.popIn()) }
                Text(
                    shown?.first.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private const val HOLD_MS = 2_000L
