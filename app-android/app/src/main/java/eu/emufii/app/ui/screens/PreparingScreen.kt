package eu.emufii.app.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import eu.emufii.app.R
import eu.emufii.app.ui.components.SoftCard
import eu.emufii.app.ui.wallpaper.TrayBackdrop
import eu.emufii.app.ui.theme.LocalEmufiiDarkTheme

@Composable
fun PreparingScreen(label: String) {
    // Sits on the same backdrop as everywhere else. On a plain surface this
    // screen read as a different app for the ten seconds it's up.
    TrayBackdrop(modifier = Modifier.fillMaxSize(), dark = LocalEmufiiDarkTheme.current)

    // Faded in rather than snapped on.
    //
    // The launch card now carries the first leg of the flow with a spinner of
    // its own, so this screen is only ever reached for the tunnel, and when the
    // tunnel happens to be up already, it comes and goes in a few frames. Cut
    // hard, that read as a glitch between the card and the session.
    //
    // An earlier attempt held the content back for 400 ms instead, which just
    // traded a flashing spinner for a blank backdrop, worse, because a bare
    // wallpaper says nothing at all. Fading from the first frame keeps the
    // screen honest when the wait is real and makes it a soft pulse when it is
    // not.
    var shown by remember(label) { mutableStateOf(false) }
    LaunchedEffect(label) { shown = true }
    val appearance by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(durationMillis = 250),
        label = "preparing-appearance"
    )

    Box(
        modifier = Modifier.fillMaxSize().padding(24.dp).alpha(appearance),
        contentAlignment = Alignment.Center
    ) {
        // On a plate, not floating on the tray.
        //
        // The two lines and the spinner sat straight on the wallpaper, which
        // made this the one screen in the app whose content was not an object:
        // the engraving of the tray ran right behind the text, and the whole
        // thing read as a system overlay rather than as a room of Emufii. A
        // panel gives the wait somewhere to happen.
        SoftCard(modifier = Modifier.widthIn(max = 360.dp)) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                Text(
                    label,
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center
                )
                Text(
                    stringResource(R.string.prep_first_time),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
