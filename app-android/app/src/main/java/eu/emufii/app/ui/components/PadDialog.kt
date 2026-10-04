package eu.emufii.app.ui.components

import eu.emufii.app.ui.sounded
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import eu.emufii.app.secondscreen.SecondScreen
import eu.emufii.app.secondscreen.SecondScreenModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import eu.emufii.app.ui.ActionShape
import eu.emufii.app.ui.controlRing
import eu.emufii.app.ui.theme.LocalEmufiiDarkTheme
import eu.emufii.app.ui.theme.LocalAccent
import eu.emufii.app.ui.SilenceSystemSfx

@Composable
fun PrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leading: (@Composable () -> Unit)? = null
) {
    val dark = LocalEmufiiDarkTheme.current
    val accent = LocalAccent.current
    val container = if (dark) accent.bright else accent.deep
    val ink = if (dark) accent.ink else Color.White
    Button(
        onClick = sounded(onClick),
        enabled = enabled,
        shape = ActionShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = ink,
            disabledContainerColor = container.copy(alpha = 0.16f),
            disabledContentColor = container.copy(alpha = 0.55f)
        ),
        modifier = modifier.heightIn(min = 48.dp).controlRing(ActionShape)
    ) {
        if (leading != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                leading()
                Text(label, style = MaterialTheme.typography.labelLarge)
            }
        } else {
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** Replaces `AlertDialog`, whose `TextButton`s lose the pad cursor with the focus veil off. */
@Composable
fun PadDialog(
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    dismissOnOutside: Boolean = true,
    panelDetail: String? = null,
    panelSocial: Boolean = false,
    actions: @Composable () -> Unit,
    content: @Composable () -> Unit
) {
    DisposableEffect(title, panelDetail, panelSocial) {
        val token = SecondScreen.putAside(
            SecondScreenModel.Asking(
                title = title,
                detail = panelDetail.orEmpty(),
                social = panelSocial
            )
        )
        onDispose { SecondScreen.takeBack(token) }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnClickOutside = dismissOnOutside,
            dismissOnBackPress = true,
            usePlatformDefaultWidth = false
        )
    ) {
        SilenceSystemSfx()
        SoftCard(
            modifier = modifier
                .padding(horizontal = 24.dp)
                .widthIn(max = 460.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(title, style = MaterialTheme.typography.headlineSmall)
                content()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    actions()
                }
            }
        }
    }
}

@Composable
fun PadDialogText(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
