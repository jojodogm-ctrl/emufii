package eu.emufii.app.ui.screens

import eu.emufii.app.ui.sounded
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import eu.emufii.app.R
import eu.emufii.app.ui.TrailerSpinner
import kotlinx.coroutines.delay
import eu.emufii.app.ui.components.SoftCard
import eu.emufii.app.ui.wallpaper.TrayBackdrop
import eu.emufii.app.ui.theme.CardShape
import eu.emufii.app.ui.theme.Coral
import androidx.compose.ui.graphics.Color
import eu.emufii.app.ui.controlRing
import eu.emufii.app.ui.components.padEntry
import eu.emufii.app.ui.theme.PillShape
import eu.emufii.app.ui.theme.LocalEmufiiDarkTheme
import eu.emufii.app.ui.theme.Teal


@Composable
fun PreparingScreen(
    label: String,
    /** Null when the caller has no safe exit to offer; nothing is then shown. */
    onGiveUp: (() -> Unit)? = null
) {
    // On a plain surface this screen read as a different app for the ten seconds it is up.
    TrayBackdrop(modifier = Modifier.fillMaxSize(), dark = LocalEmufiiDarkTheme.current)

    // On an already-up tunnel this screen comes and goes in a few frames: cut hard, that
    // read as a glitch. Holding the content back for 400 ms instead only traded the
    // flashing spinner for a blank backdrop.
    var shown by remember(label) { mutableStateOf(false) }
    LaunchedEffect(label) { shown = true }

    /**
     * Twenty seconds: beyond what a cold tunnel needs, under the forty-five of the guard
     * delay, so the player takes back control before the code gives up for them.
     */
    var overdue by remember(label) { mutableStateOf(false) }
    LaunchedEffect(label) {
        delay(OVERDUE_MS)
        overdue = true
    }
    val appearance by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(durationMillis = 250),
        label = "preparing-appearance"
    )

    Box(
        modifier = Modifier.fillMaxSize().padding(24.dp).alpha(appearance),
        contentAlignment = Alignment.Center
    ) {
        SoftCard(modifier = Modifier.widthIn(max = 360.dp)) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                // Material's default 40 dp on a 4 dp stroke is sized for a spinner passing
                // through a list corner; here it is stared at for ten seconds.
                TrailerSpinner(
                    color = MaterialTheme.colorScheme.primary,
                    size = 54.dp,
                    stroke = 5.dp
                )
                Text(
                    label,
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center
                )
                // "A little slow the first time" is true at ten seconds and a lie at thirty.
                Text(
                    stringResource(
                        if (overdue) R.string.prep_taking_long else R.string.prep_first_time
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                // Offered from the first second it would invite giving up on a normal
                // wait; offered never, Home is the only way out.
                if (onGiveUp != null && overdue) {
                    Button(
                        onClick = sounded(onGiveUp),
                        shape = PillShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Transparent,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.controlRing(PillShape).padEntry()
                    ) { Text(stringResource(R.string.prep_give_up)) }
                }
            }
        }
    }
}

private const val OVERDUE_MS = 20_000L
