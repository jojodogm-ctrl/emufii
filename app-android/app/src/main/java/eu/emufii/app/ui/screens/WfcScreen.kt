package eu.emufii.app.ui.screens

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import eu.emufii.app.ui.ActionShape
import eu.emufii.app.ui.controlRing
import eu.emufii.app.ui.components.RomArtwork
import eu.emufii.app.ui.theme.AccentGreen
import eu.emufii.app.ui.theme.PillShape
import eu.emufii.app.R
import eu.emufii.app.azahar.LaunchResult
import eu.emufii.app.library.Rom
import eu.emufii.app.ui.components.EmufiiScaffold
import eu.emufii.app.ui.components.SoftCard
import eu.emufii.app.ui.components.padEntry
import eu.emufii.app.wfc.MelonDs
import eu.emufii.app.wfc.WfcManager
import eu.emufii.app.wfc.WfcState

/**
 * DS online play. No session, no code, no other player to wait for, the console
 * dials a revival server, so the only thing Emufii has to do is make sure it dials
 * the right one.
 *
 * That is the whole point of the tunnel behind this screen: melonDS keeps the
 * console on "auto-obtain DNS", and in that mode Android's resolver decides where
 * `nintendowifi.net` goes. Emufii answers, so the player configures nothing.
 */
@Composable
fun WfcScreen(
    rom: Rom,
    /**
     * Android runs one VpnService at a time, so this asks the app for the slot
     * and only then runs what we passed. It can put a confirmation in the way,
     * see [eu.emufii.app.tunnel.tunnelHolder].
     */
    onRequestTunnelSlot: (proceed: () -> Unit) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val melon = remember { MelonDs(context) }
    val state by WfcManager.state.collectAsState()
    var status by remember { mutableStateOf<String?>(null) }

    fun launch() {
        when (val result = melon.launchGame(rom.uri)) {
            LaunchResult.Success -> status = context.getString(R.string.wfc_launched)
            LaunchResult.NotInstalled -> status = context.getString(R.string.wfc_not_installed)
            // Unreachable here: WFC goes through Kaeru, not a netplay dialog, so
            // MelonDs never probes for one. Named rather than folded into `else`
            // so a future melonDS netplay path has to make a decision here.
            is LaunchResult.NoNetplayUi -> status = context.getString(R.string.wfc_not_installed)
            is LaunchResult.Error -> status = result.message
        }
    }

    // Consent is Android's, not ours: the first run pops the system VPN dialog,
    // and refusing it has to leave the app usable rather than stuck.
    val consent = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            WfcManager.start(context)
            launch()
        } else {
            status = context.getString(R.string.wfc_no_vpn)
        }
    }

    // Leaving by the header is what stops the redirection; this screen used to
    // carry its own back button at the bottom for that.
    val leave = {
        WfcManager.stop(context)
        onBack()
    }

    EmufiiScaffold(
        title = stringResource(R.string.wfc_title),
        modifier = modifier,
        onBack = leave,
        // A game, an explanation, a button: it fits. Nothing rises under the
        // header, so the fade margin would be one more empty band.
        contentScrolls = false
    ) { _ ->
        // Centred on the screen, not under the header. Reserving `topPadding`
        // centred the card within what was left *below* the title, hence 37 dp
        // too low, visibly off on a screen where it is the only object. The header
        // floats above the background and has no room to reserve until the content
        // reaches it; this card is about 250 dp of the device's 468, and never
        // does.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
        // A card set in the middle, not a column stretched across the full
        // width. The screen was built in portrait: on the Thor its card and its
        // button were 784 dp wide, the line of text spanned the whole screen, well
        // past what can be read in one go, and two large gaps were left above and
        // below. The same layout as the launch card, of which it is the sibling: a
        // game, what is about to happen to it, a button.
        SoftCard(modifier = Modifier.widthIn(max = 648.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                horizontalArrangement = Arrangement.spacedBy(26.dp)
            ) {
                Column(
                    modifier = Modifier.width(186.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    RomArtwork(rom, size = 120.dp)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            rom.displayName,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            stringResource(R.string.launch_mode_online, rom.console.label),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            stringResource(R.string.wfc_card_title),
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            stringResource(R.string.wfc_card_body),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    WfcStateChip(state)

            // One button, two jobs, because there are only ever two things to do
            // here. Once the redirection is up the useful action is no longer
            // "start", it is "stop", and stopping is what the player has to do
            // before the phone can carry a session again. Leaving a blue Start
            // button on screen at that point invited a second launch and hid the
            // only control that matters.
            //
            // Red is the app's one destructive colour, and this is the app's one
            // destructive button: it ends the redirection, so a console still in
            // a game loses its way to Kaeru.
            // Unreachable counts as running: the redirection is still up and
            // still the thing to switch off, even while Kaeru is silent.
            val active = state is WfcState.Active || state is WfcState.Unreachable
            Button(
                onClick = {
                    if (active) {
                        leave()
                    } else {
                        onRequestTunnelSlot {
                            val intent = WfcManager.prepare(context)
                            if (intent != null) {
                                consent.launch(intent)
                            } else {
                                WfcManager.start(context)
                                launch()
                            }
                        }
                    }
                },
                shape = ActionShape,
                colors = if (active) {
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                } else {
                    ButtonDefaults.buttonColors()
                },
                modifier = Modifier.fillMaxWidth().height(56.dp)
                    .controlRing(ActionShape).padEntry()
            ) {
                Text(
                    stringResource(if (active) R.string.wfc_stop else R.string.wfc_launch),
                    style = MaterialTheme.typography.titleMedium
                )
            }

            status?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
                }
            }
        }
        }
    }
}

/**
 * The name the emulator gives itself, from its package.
 *
 * The pill used to display `me.magnum.melondualds`, a technical identifier
 * inside an interface. Asked of the system rather than hardcoded: the Thor
 * carries a rebrand ("melonDS DualS"), another device will have the original
 * melonDS, and each has to read the name of the one it holds. If the package has
 * disappeared in the meantime the identifier remains, which is ugly but true,
 * and only happens when the emulator was uninstalled while the redirection was
 * running.
 */
@Composable
private fun appLabel(packageName: String): String {
    val pm = LocalContext.current.packageManager
    return remember(packageName) {
        runCatching {
            pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
        }.getOrDefault(packageName)
    }
}

/**
 * The redirection's state, as a pill rather than a line of text.
 *
 * It is the only thing on this screen that changes by itself, and a grey
 * sentence among other grey sentences did not say so. The pill reuses the
 * session header's: a coloured dot and two words.
 */
@Composable
private fun WfcStateChip(state: WfcState) {
    val error = state is WfcState.Unreachable || state is WfcState.Error
    val running = state is WfcState.Active
    val tint = when {
        error -> MaterialTheme.colorScheme.error
        running -> AccentGreen
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = Modifier
            .clip(PillShape)
            .background(tint.copy(alpha = 0.16f))
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(tint))
        Text(
            when (val s = state) {
                WfcState.Idle -> stringResource(R.string.wfc_state_idle)
                is WfcState.Active -> stringResource(R.string.wfc_state_active, appLabel(s.scopedTo))
                is WfcState.Unreachable -> stringResource(R.string.wfc_state_unreachable)
                WfcState.Stopping -> stringResource(R.string.wfc_state_stopping)
                is WfcState.Error -> stringResource(R.string.wfc_state_error, s.message)
            },
            style = MaterialTheme.typography.labelMedium,
            // On a tinted background: the text colour comes from the tint, never
            // from grey, which is what keeps the contrast in both themes.
            color = if (error) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurface
        )
    }
}
