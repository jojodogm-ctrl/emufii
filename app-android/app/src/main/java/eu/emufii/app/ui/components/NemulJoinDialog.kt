package eu.emufii.app.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import eu.emufii.app.R
import eu.emufii.app.azahar.LaunchResult
import eu.emufii.app.library.Rom
import eu.emufii.app.nemul.NemulLauncher
import eu.emufii.app.nemul.NemulPackage
import eu.emufii.app.nemul.NemulRoomCode
import eu.emufii.app.secondscreen.SecondScreen
import eu.emufii.app.secondscreen.SecondScreenModel
import eu.emufii.app.ui.SilenceSystemSfx

@Composable
fun NemulJoinDialog(rom: Rom, playerName: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val launcher = remember(context) { NemulLauncher(context) }
    var installed by remember { mutableStateOf(launcher.isInstalled()) }
    var code by remember { mutableStateOf("") }
    var waitForHostPreparation by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val hint = stringResource(R.string.nemul_join_body)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, launcher) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) installed = launcher.isInstalled()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    NemulJoinSheet(
        title = stringResource(R.string.nemul_join_title),
        onDismiss = onDismiss,
        gameTitle = rom.displayName,
        actions = {
            GhostButton(label = stringResource(R.string.common_cancel), onClick = onDismiss)
            if (installed) {
                PrimaryButton(
                    label = stringResource(R.string.nemul_join),
                    enabled = NemulRoomCode.isValid(code),
                    onClick = {
                        when (val result = launcher.joinRoom(code, playerName, waitForHostPreparation)) {
                            LaunchResult.Success -> onDismiss()
                            LaunchResult.NotInstalled -> installed = false
                            is LaunchResult.Error -> error = result.message
                            is LaunchResult.NoNetplayUi -> Unit
                        }
                    }
                )
            } else {
                PrimaryButton(
                    label = stringResource(R.string.nemul_download),
                    onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(NemulPackage.DOWNLOAD_URL))
                            )
                        }.onFailure { error = it.message ?: context.getString(R.string.err_launch) }
                    }
                )
            }
        }
    ) {
        Text(rom.displayName, style = MaterialTheme.typography.titleMedium)
        PadDialogText(hint)
        if (installed) {
            PadTextField(
                value = code,
                onValueChange = { code = NemulRoomCode.normalize(it); error = null },
                label = stringResource(R.string.nemul_room_label),
                placeholder = "ABCD",
                isError = code.isNotEmpty() && !NemulRoomCode.isValid(code),
                supportingText = { Text(stringResource(R.string.nemul_room_invalid)) },
                modifier = Modifier.fillMaxWidth()
            )
            SwitchRow(
                label = stringResource(R.string.nemul_wait_for_host),
                checked = waitForHostPreparation,
                onCheckedChange = { waitForHostPreparation = it },
                note = stringResource(R.string.nemul_wait_for_host_hint)
            )
        } else {
            PadDialogText(stringResource(R.string.nemul_missing))
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

/** Keep room entry scrollable above the keyboard while the join/cancel actions stay visible. */
@Composable
private fun NemulJoinSheet(
    title: String,
    gameTitle: String,
    onDismiss: () -> Unit,
    actions: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    DisposableEffect(title, gameTitle) {
        val token = SecondScreen.putAside(
            SecondScreenModel.Asking(title = title, detail = gameTitle, social = false)
        )
        onDispose { SecondScreen.takeBack(token) }
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        SilenceSystemSfx()
        BoxWithConstraints(
            modifier = Modifier
                .systemBarsPadding()
                .imePadding()
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            val compact = maxHeight < 260.dp
            val gap = if (compact) 8.dp else 12.dp
            SoftCard(modifier = Modifier.widthIn(max = 460.dp).heightIn(max = maxHeight)) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(if (compact) 12.dp else 20.dp),
                    verticalArrangement = Arrangement.spacedBy(gap)
                ) {
                    Text(
                        title,
                        style = if (compact) MaterialTheme.typography.titleLarge
                        else MaterialTheme.typography.headlineSmall
                    )
                    Column(
                        modifier = Modifier.weight(1f, fill = false)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(gap)
                    ) {
                        content()
                    }
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
}
