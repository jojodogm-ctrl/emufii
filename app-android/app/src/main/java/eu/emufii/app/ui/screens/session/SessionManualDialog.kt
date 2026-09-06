package eu.emufii.app.ui.screens.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import eu.emufii.app.R
import eu.emufii.app.azahar.NetplayPlan
import eu.emufii.app.ui.components.CopyMark
import eu.emufii.app.ui.components.GhostButton
import eu.emufii.app.ui.components.PadDialog
import eu.emufii.app.ui.components.PadDialogText
import eu.emufii.app.ui.controlRing
import eu.emufii.app.ui.copyToClipboard
import eu.emufii.app.ui.tap

/**
 * Shown when the automatic path can't reach the emulator: the same values the driver would
 * fill are listed here so the player can type them in themselves.
 */
@Composable
internal fun SessionManualDialog(
    plan: NetplayPlan,
    addressLabel: String,
    emulatorName: String,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val hint = stringResource(R.string.session_manual_hint, emulatorName)
    PadDialog(
        title = stringResource(R.string.session_netplay_manual_open),
        onDismiss = onDismiss,
        panelDetail = hint,
        actions = {
            GhostButton(
                label = stringResource(R.string.common_done),
                onClick = onDismiss,
            )
        }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            PadDialogText(hint)
            ManualRow(addressLabel, plan.ip) {
                copyToClipboard(context, addressLabel, plan.ip)
            }
            val portLabel = stringResource(R.string.session_port)
            ManualRow(portLabel, plan.port.toString()) {
                copyToClipboard(context, portLabel, plan.port.toString())
            }
            plan.roomName?.let { raw ->
                val value = raw.removePrefix("Emufii ").trim()
                if (value.isNotEmpty()) {
                    val label = stringResource(R.string.session_manual_room_name)
                    ManualRow(label, value) { copyToClipboard(context, label, value) }
                }
            }
            plan.preferredGame?.let { value ->
                val label = stringResource(R.string.session_game)
                ManualRow(label, value) { copyToClipboard(context, label, value) }
            }
            plan.password?.let { value ->
                val label = stringResource(R.string.session_manual_password)
                ManualRow(label, value) { copyToClipboard(context, label, value) }
            }
        }
    }
}

@Composable
private fun ManualRow(
    label: String,
    value: String,
    onCopy: () -> Unit,
) {
    val copyDesc = stringResource(R.string.session_manual_copy, label)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                value,
                style = MaterialTheme.typography.bodyLarge,
                fontFamily = FontFamily.Monospace,
            )
        }
        Box(
            modifier = Modifier
                .size(40.dp)
                .controlRing(CircleShape)
                .tap(onClick = onCopy)
                .semantics { contentDescription = copyDesc },
            contentAlignment = Alignment.Center,
        ) {
            CopyMark(color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

