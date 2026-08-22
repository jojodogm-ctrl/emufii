package eu.emufii.app.ui.components

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import eu.emufii.app.R
import eu.emufii.app.update.LatestVersion
import eu.emufii.app.update.UpdateInstaller
import eu.emufii.app.update.UpdateOutcome
import kotlinx.coroutines.launch

/**
 * How much room the banner takes at the top of the library.
 *
 * The grid uses it to offset itself by that much. A constant rather than a
 * measurement: the card has only two or three possible lines, and varying a
 * grid's offset with a text measurement would cost an extra layout pass at every
 * opening for a gain nobody sees.
 */
val UPDATE_BANNER_ROOM = 96.dp

/**
 * "A new version exists."
 *
 * Emufii is sideloaded: no store will give notice on its behalf, and a fix that
 * stays on the development machine fixes nothing. This was point S5 of the
 * security review, and the last one left open.
 *
 * The install button pulls the APK from the coordinator, checks its signature
 * against the running app's, then hands it to Android; the three locks are
 * detailed in [UpdateInstaller]. Tapping that button is the only consent asked
 * for: Android 12 and later install an app that updates itself with no
 * confirmation dialog, which was verified here. So the label says "Install", not
 * "Download".
 *
 * Dismissible, and the dismissal holds for that version only: refusing once must
 * not switch the announcement off forever.
 */
@Composable
fun UpdateBanner(
    latest: LatestVersion,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var busy by remember { mutableStateOf(false) }
    // What the last attempt produced, in one sentence. Replaces the notes line
    // rather than adding below it: the card has a fixed height, and a fourth line
    // would push the buttons out of the frame, the flaw the Thor's landscape has
    // already revealed once.
    var failure by remember { mutableStateOf<Int?>(null) }

    fun install() {
        if (busy) return
        busy = true
        failure = null
        scope.launch {
            when (val outcome = UpdateInstaller.downloadAndInstall(context, latest)) {
                is UpdateOutcome.HandedToAndroid -> Unit
                is UpdateOutcome.NeedsPermission ->
                    // The settings screen, not a message: what is missing is two
                    // taps away, and saying so without leading there would send
                    // people searching.
                    runCatching { context.startActivity(outcome.settings) }
                        .onFailure { failure = R.string.update_failed_permission }
                UpdateOutcome.Unavailable -> failure = R.string.update_failed_unavailable
                UpdateOutcome.DownloadFailed -> failure = R.string.update_failed_download
                UpdateOutcome.Rejected -> failure = R.string.update_failed_rejected
            }
            busy = false
        }
    }

    SoftCard(modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 8.dp, top = 14.dp, bottom = 6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                stringResource(R.string.update_available, latest.versionName),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            // The language read off the configuration, not off
            // `Locale.getDefault()`: it is the one the system actually applied to
            // the app, hence the one everything else on this card is written
            // in.
            val locale = LocalConfiguration.current.locales[0]
            val secondLine = failure?.let { stringResource(it) } ?: latest.notesFor(locale)
            secondLine?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (failure != null) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                GhostButton(
                    label = stringResource(R.string.update_later),
                    onClick = { if (!busy) onDismiss() }
                )
                // The link stays on offer when one is published: it leads to a
                // page to read, where the button next to it leads to a binary.
                latest.url?.let { url ->
                    GhostButton(
                        label = stringResource(R.string.update_open),
                        onClick = {
                            if (busy) return@GhostButton
                            // Best-effort: on a device with no browser there is
                            // nothing to open, and bringing the library down over
                            // it would be out of proportion with the stakes.
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }.onFailure { if (it !is ActivityNotFoundException) throw it }
                        }
                    )
                }
                // The install keeps its spinner in place of its label: the
                // download is the one thing here that takes time, and the
                // button is where you look for its progress.
                GhostButton(
                    label = stringResource(R.string.update_install),
                    onClick = { if (!busy) install() },
                    icon = if (!busy) null else { tint ->
                        CircularProgressIndicator(
                            color = tint,
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                    }
                )
            }
        }
    }
}
