package eu.emufii.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import eu.emufii.app.R
import eu.emufii.app.tunnel.TunnelHolder
import androidx.compose.material3.MaterialTheme

@Composable
fun TunnelConflictDialog(
    held: TunnelHolder,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val body = when (held) {
        TunnelHolder.WFC -> R.string.tunnel_conflict_wfc
        else -> R.string.tunnel_conflict_session
    }
    PadDialog(
        title = stringResource(R.string.tunnel_conflict_title),
        onDismiss = onDismiss,
        dismissOnOutside = false,
        actions = {
            GhostButton(
                label = stringResource(R.string.common_cancel),
                onClick = onDismiss
            )
                GhostButton(
                    label = stringResource(R.string.tunnel_conflict_confirm),
                    onClick = onConfirm,
                    tint = MaterialTheme.colorScheme.error
                )
        }
    ) {
        PadDialogText(stringResource(body))
    }
}
