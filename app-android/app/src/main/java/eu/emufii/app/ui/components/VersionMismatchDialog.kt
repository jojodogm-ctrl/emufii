package eu.emufii.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import eu.emufii.app.R
import eu.emufii.app.library.Console
import eu.emufii.app.library.EmulatorInfo
import eu.emufii.app.library.emulatorInfo
import eu.emufii.app.secondscreen.SecondScreen
import eu.emufii.app.secondscreen.SecondScreenModel
import eu.emufii.app.ui.ActionShape
import eu.emufii.app.ui.Motion
import eu.emufii.app.ui.SilenceSystemSfx
import eu.emufii.app.ui.popIn
import eu.emufii.app.ui.rememberAnimationsEnabled
import eu.emufii.app.ui.theme.Coral
import eu.emufii.app.ui.theme.LocalAccent
import eu.emufii.app.ui.theme.LocalEmufiiDarkTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun VersionMismatchDialog(
    console: Console,
    hostVersion: String,
    ourVersion: String,
    onContinue: () -> Unit,
    onLeave: () -> Unit
) {
    val context = LocalContext.current
    val dark = LocalEmufiiDarkTheme.current
    val accent = LocalAccent.current
    val accentInk = if (dark) accent.bright else accent.deep
    val alarm = if (dark) Coral.darkBright else Coral.deep
    val animate = rememberAnimationsEnabled()
    val scope = rememberCoroutineScope()

    val emulator by produceState<EmulatorInfo?>(null, console) {
        value = withContext(Dispatchers.IO) { runCatching { emulatorInfo(context, console) }.getOrNull() }
    }

    val scale = remember { Animatable(if (animate) 0.9f else 1f) }
    val alpha = remember { Animatable(if (animate) 0f else 1f) }
    val fill = remember { Animatable(0f) }
    val pop = Motion.pop<Float>()
    val enter = Motion.enter<Float>()
    val exit = Motion.exit<Float>()
    var ready by remember { mutableStateOf(false) }
    var leaving by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        launch { scale.animateTo(1f, pop) }
        launch { alpha.animateTo(1f, enter) }
        fill.animateTo(1f, tween(WAIT_MS, easing = LinearEasing))
        ready = true
    }

    fun leaveWith(answer: () -> Unit) {
        if (leaving) return
        leaving = true
        scope.launch {
            launch { scale.animateTo(0.94f, exit) }
            alpha.animateTo(0f, exit)
            answer()
        }
    }

    val title = stringResource(R.string.version_mismatch_title)
    val body = stringResource(R.string.version_mismatch_body, hostVersion, ourVersion)
    DisposableEffect(title) {
        val token = SecondScreen.putAside(SecondScreenModel.Asking(title = title, detail = body, social = true))
        onDispose { SecondScreen.takeBack(token) }
    }

    Dialog(
        onDismissRequest = { leaveWith(onLeave) },
        properties = DialogProperties(dismissOnClickOutside = false, usePlatformDefaultWidth = false)
    ) {
        SilenceSystemSfx()
        SoftCard(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .widthIn(max = 480.dp)
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                    this.alpha = alpha.value
                    compositingStrategy = CompositingStrategy.ModulateAlpha
                }
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                SheetHeader(
                    icon = emulator?.icon,
                    fallbackLetter = console.backend.emulatorName.take(1),
                    title = title,
                    subtitle = emulator?.name ?: console.backend.emulatorName,
                    accent = alarm,
                    iconSize = 48.dp,
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FactTile(
                        label = stringResource(R.string.version_mismatch_host),
                        value = hostVersion,
                        ink = alarm,
                        modifier = Modifier.weight(1f).popIn()
                    )
                    Text(
                        "≠",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        modifier = Modifier.popIn()
                    )
                    FactTile(
                        label = stringResource(R.string.version_mismatch_you),
                        value = ourVersion,
                        ink = accentInk,
                        modifier = Modifier.weight(1f).popIn()
                    )
                }

                SheetWarning(stringResource(R.string.version_mismatch_hint), alarm)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GhostButton(
                        label = stringResource(R.string.version_mismatch_leave),
                        onClick = { leaveWith(onLeave) },
                        tint = MaterialTheme.colorScheme.error
                    )
                    // Clip only the fill: clipping the wrapper cuts off the focus ring.
                    Box {
                        PrimaryButton(
                            label = stringResource(R.string.version_mismatch_continue),
                            onClick = { leaveWith(onContinue) },
                            enabled = ready
                        )
                        if (!ready) {
                            Box(
                                Modifier
                                    .matchParentSize()
                                    .clip(ActionShape)
                                    .drawBehind {
                                        drawRect(
                                            color = accentInk.copy(alpha = 0.30f),
                                            size = size.copy(width = size.width * fill.value)
                                        )
                                    }
                            )
                        }
                    }
                }
            }
        }
    }
}

private const val WAIT_MS = 3_000
