package eu.emufii.app.onetap

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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import eu.emufii.app.secondscreen.SecondScreen
import eu.emufii.app.secondscreen.SecondScreenModel
import eu.emufii.app.ui.SilenceSystemSfx
import eu.emufii.app.ui.components.GhostButton
import eu.emufii.app.ui.components.PadDialogText
import eu.emufii.app.ui.components.PadTextField
import eu.emufii.app.ui.components.PrimaryButton
import eu.emufii.app.ui.components.SoftCard
import kotlinx.coroutines.launch

@Composable
fun OneTapJoinDialog(game: OneTapGame, playerName: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val launcher = remember(context) { OneTapLauncher(context) }
    val configuration = remember { OneTapConfiguration.fromBuild() }
    val client = remember(configuration.adapterUrl) { configuration.adapterUrl?.let(::OneTapClient) }
    val scope = rememberCoroutineScope()
    val name = playerName.trim().ifEmpty { "LePast" }
    var installed by remember { mutableStateOf(launcher.isNativeInstalled()) }
    var input by remember { mutableStateOf("") }
    var session by remember { mutableStateOf<OneTapSession?>(null) }
    var resolving by remember { mutableStateOf(false) }
    var requestSerial by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    val directCode = OneTapRoomCode.parse(input)
    val emufiiCode = runCatching { OneTapClient.sessionCode(input) }.getOrNull()
    val joinedCode = directCode ?: session?.oneTap?.code
    val title = stringResource(R.string.one_tap_title)
    val gameTitle = stringResource(when (game) {
        OneTapGame.MARIO_PARTY_DS -> R.string.one_tap_game_mpds
        OneTapGame.MARIO_KART_DS -> R.string.one_tap_game_mkds
    })
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, launcher) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) installed = launcher.isNativeInstalled()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    DisposableEffect(title, gameTitle) {
        val token = SecondScreen.putAside(SecondScreenModel.Asking(title = title, detail = gameTitle, social = true))
        onDispose { SecondScreen.takeBack(token) }
    }
    fun launched(result: OneTapLaunchResult) {
        when (result) {
            OneTapLaunchResult.Opened -> onDismiss()
            OneTapLaunchResult.NativeNotInstalled -> {
                installed = false
                error = context.getString(R.string.one_tap_native_missing)
            }
            OneTapLaunchResult.InvalidRoomCode -> error = context.getString(R.string.one_tap_code_invalid)
            OneTapLaunchResult.NoBrowser -> error = context.getString(R.string.one_tap_browser_missing)
            OneTapLaunchResult.Failed -> error = context.getString(R.string.err_launch)
        }
    }
    fun resolve() {
        val code = emufiiCode ?: return
        val api = client ?: return
        val serial = ++requestSerial
        resolving = true
        error = null
        scope.launch {
            try {
                val result = api.getSession(code)
                if (serial != requestSerial) return@launch
                result.onSuccess {
                    if (it.game != game) {
                        session = null
                        error = context.getString(R.string.one_tap_wrong_game)
                    } else session = it
                }.onFailure {
                    session = null
                    error = context.getString(when {
                        it is OneTapClientError.Http && it.status == 404 -> R.string.one_tap_not_found
                        it is OneTapClientError.Http && it.status in listOf(401, 403) -> R.string.one_tap_access_denied
                        else -> R.string.one_tap_unreachable
                    })
                }
            } finally { if (serial == requestSerial) resolving = false }
        }
    }

    Dialog(onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        SilenceSystemSfx()
        BoxWithConstraints(Modifier.systemBarsPadding().imePadding().padding(horizontal = 24.dp, vertical = 16.dp)) {
            val compact = maxHeight < 300.dp
            val gap = if (compact) 8.dp else 12.dp
            SoftCard(modifier = Modifier.widthIn(max = 520.dp).heightIn(max = maxHeight)) {
                Column(Modifier.fillMaxWidth().padding(if (compact) 12.dp else 20.dp),
                    verticalArrangement = Arrangement.spacedBy(gap)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                        GhostButton(label = stringResource(R.string.common_cancel), onClick = onDismiss)
                    }
                    Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(gap)) {
                        Text(gameTitle, style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.one_tap_profile, name), style = MaterialTheme.typography.bodyMedium)
                        PadDialogText(stringResource(R.string.one_tap_transfer_hint))
                        if (configuration.frontendUrl != null) {
                            PrimaryButton(label = stringResource(R.string.one_tap_create), modifier = Modifier.fillMaxWidth(),
                                onClick = { launched(launcher.createEmufiiHost(configuration, game, name)) })
                        } else {
                            PadDialogText(stringResource(R.string.one_tap_create_unavailable))
                        }
                        PadTextField(value = input, onValueChange = {
                            input = it
                            requestSerial++
                            resolving = false
                            session = null
                            error = null
                        }, label = stringResource(R.string.one_tap_code_label),
                            placeholder = "ABC-234 / AB2D", modifier = Modifier.fillMaxWidth(),
                            isError = input.isNotBlank() && directCode == null && emufiiCode == null,
                            supportingText = { Text(stringResource(R.string.one_tap_code_hint)) })
                        if (emufiiCode != null) {
                            if (client == null) PadDialogText(stringResource(R.string.one_tap_adapter_unavailable))
                            else PrimaryButton(label = stringResource(if (resolving) R.string.one_tap_resolving else R.string.one_tap_resolve),
                                onClick = ::resolve, enabled = !resolving, modifier = Modifier.fillMaxWidth())
                        }
                        session?.let {
                            PadDialogText(stringResource(when (it.phase) {
                                OneTapPhase.AWAITING_ONE_TAP_HOST -> R.string.one_tap_waiting
                                OneTapPhase.LINKED -> R.string.one_tap_linked
                            }))
                        }
                        if (!installed) PadDialogText(stringResource(R.string.one_tap_native_missing))
                        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PrimaryButton(label = stringResource(R.string.one_tap_native), modifier = Modifier.weight(1f),
                            enabled = joinedCode != null && installed && !resolving,
                            onClick = { joinedCode?.let { launched(launcher.joinNative(it)) } })
                        PrimaryButton(label = stringResource(R.string.one_tap_browser), modifier = Modifier.weight(1f),
                            enabled = joinedCode != null && !resolving,
                            onClick = { joinedCode?.let { launched(launcher.joinBrowser(it)) } })
                    }
                }
            }
        }
    }
}
