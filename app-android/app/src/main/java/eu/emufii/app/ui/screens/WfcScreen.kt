package eu.emufii.app.ui.screens

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.emufii.app.R
import eu.emufii.app.azahar.LaunchResult
import eu.emufii.app.library.Rom
import eu.emufii.app.ui.ActionShape
import eu.emufii.app.ui.LocalRingTone
import eu.emufii.app.ui.RingTone
import eu.emufii.app.ui.components.EmufiiScaffold
import eu.emufii.app.ui.components.RomArtwork
import eu.emufii.app.ui.components.SoftCard
import eu.emufii.app.ui.components.padEntry
import eu.emufii.app.ui.components.waitTrim
import eu.emufii.app.ui.controlRing
import eu.emufii.app.ui.sounded
import eu.emufii.app.ui.theme.GoodDark
import eu.emufii.app.ui.theme.GoodLight
import eu.emufii.app.ui.theme.LocalEmufiiDarkTheme
import eu.emufii.app.ui.theme.PillShape
import eu.emufii.app.wfc.MelonDs
import eu.emufii.app.wfc.WfcManager
import eu.emufii.app.wfc.WfcState

/**
 * The diagonal coral-to-teal edge light: the signature of the waiting and connecting
 * flows, a slanted stroke on the neutral card's corner.
 * pourquoi : docs/decisions/theme-duotone-shelves.md § Onboarding / Preparing
 */
@Composable
private fun good() = if (LocalEmufiiDarkTheme.current) GoodDark else GoodLight


/**
 * DS online play: no session and no code. melonDS stays on "auto-obtain DNS", so Android's
 * resolver decides where `nintendowifi.net` goes; Emufii answers it.
 */
@Composable
fun WfcScreen(
    rom: Rom,
    /** Android runs one VpnService at a time; see [eu.emufii.app.tunnel.tunnelHolder]. */
    onRequestTunnelSlot: (proceed: () -> Unit) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val melon = remember { MelonDs(context) }
    val state by WfcManager.state.collectAsStateWithLifecycle()
    var status by remember { mutableStateOf<String?>(null) }

    fun launch() {
        when (val result = melon.launchGame(rom.uri)) {
            LaunchResult.Success -> status = context.getString(R.string.wfc_launched)
            LaunchResult.NotInstalled -> status = context.getString(R.string.wfc_not_installed)
            // Unreachable: WFC goes through Kaeru, not a netplay dialog. Named rather than
            // folded into `else` so a future melonDS netplay path decides here.
            is LaunchResult.NoNetplayUi -> status = context.getString(R.string.wfc_not_installed)
            is LaunchResult.Error -> status = result.message
        }
    }

    val consent = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            WfcManager.start(context)
            launch()
        } else {
            status = context.getString(R.string.wfc_no_vpn)
        }
    }

    val leave = {
        WfcManager.stop(context)
        onBack()
    }

    // The social domain: the pad cursor turns coral here.
    // pourquoi : docs/decisions/theme-duotone-shelves.md § GAMEPAD FOCUS
    CompositionLocalProvider(LocalRingTone provides RingTone.CORAL) {
    EmufiiScaffold(
        title = stringResource(R.string.wfc_title),
        modifier = modifier,
        onBack = leave,
        // Nothing rises under the header: the fade margin would be one more empty band.
        contentScrolls = false
    ) { _ ->
        // Centred on the screen, not under the header: `topPadding` put the card 37 dp too
        // low, this card being about 250 dp of the device's 468.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
        // A card, not a full-width column: on the Thor that came out 784 dp wide, one line
        // of text spanning the screen.
        SoftCard(modifier = Modifier.widthIn(max = 648.dp).waitTrim()) {
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

            // One button, two jobs: a blue Start left on screen invited a second launch.
            // Red, because stopping loses a console still in a game its way to Kaeru;
            // Unreachable counts as running.
            val active = state is WfcState.Active || state is WfcState.Unreachable
            Button(
                onClick = sounded {
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
}

/**
 * Asked of the system rather than hardcoded: the Thor carries a rebrand ("melonDS DualS").
 * A vanished package leaves the raw identifier, and only an uninstall mid-redirection does that.
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

@Composable
private fun WfcStateChip(state: WfcState) {
    val error = state is WfcState.Unreachable || state is WfcState.Error
    val running = state is WfcState.Active
    val tint = when {
        error -> MaterialTheme.colorScheme.error
        running -> good()
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
            color = if (error) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurface
        )
    }
}
