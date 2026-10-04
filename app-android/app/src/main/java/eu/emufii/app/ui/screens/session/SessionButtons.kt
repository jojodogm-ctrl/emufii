package eu.emufii.app.ui.screens.session

import kotlinx.coroutines.delay
import eu.emufii.app.ui.awaitSeen
import androidx.lifecycle.compose.LocalLifecycleOwner
import eu.emufii.app.ui.theme.Teal
import eu.emufii.app.ui.rememberAnimationsEnabled
import eu.emufii.app.ui.rememberAppear
import eu.emufii.app.ui.bloom
import eu.emufii.app.ui.TrailerSpinner
import eu.emufii.app.ui.Sfx
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.core.Animatable
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.emufii.app.R
import androidx.compose.animation.togetherWith
import androidx.compose.animation.scaleOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import eu.emufii.app.ui.Motion
import eu.emufii.app.ui.DrawnCheck
import androidx.compose.ui.text.TextStyle
import eu.emufii.app.ui.theme.liftShadow
import eu.emufii.app.ui.RevealCode
import eu.emufii.app.library.Backend
import eu.emufii.app.session.Session
import eu.emufii.app.ui.ActionShape
import eu.emufii.app.ui.controlRing
import eu.emufii.app.ui.sounded
import eu.emufii.app.ui.tap
import eu.emufii.app.ui.theme.Coral
import eu.emufii.app.ui.theme.LocalEmufiiDarkTheme
import eu.emufii.app.ui.theme.LocalEmufiiOledTheme
import eu.emufii.app.ui.theme.PillShape
import eu.emufii.app.ui.theme.edgeColor
import eu.emufii.app.ui.theme.plate

@Composable
internal fun AutoSetupNetplayButton(
    session: Session,
    netplayDone: Boolean,
    netplayPrepared: Boolean,
    waitingForHost: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    NetplayButtonContainer(
        session = session,
        netPlayReadyStrRes = R.string.session_netplay_open,
        netplayDone = netplayDone,
        netplayPrepared = netplayPrepared,
        waitingForHost = waitingForHost,
        modifier = modifier,
        onClick = onClick
    )
}

@Composable
internal fun ManualSetupNetplayButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val label = stringResource(R.string.session_netplay_manual_open)
    Button(
        onClick = sounded(onClick),
        shape = ActionShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        contentPadding = PaddingValues(0.dp),
        modifier = modifier
            .height(56.dp)
            .controlRing(ActionShape)
            .semantics { contentDescription = label }
    ) {
        InfoMark(color = good())
    }
}

@Composable
private fun NetplayButtonContainer(
    session: Session,
    @StringRes netPlayReadyStrRes: Int,
    modifier: Modifier = Modifier,
    netplayDone: Boolean = false,
    waitingForHost: Boolean = false,
    netplayPrepared: Boolean = false,
    onClick: () -> Unit,
) {
    val enabled = session.rom != null && !waitingForHost
    val busy = LocalNetplayBusy.current && !netplayDone && !waitingForHost
    val primary = MaterialTheme.colorScheme.primary
    val onPrimary = MaterialTheme.colorScheme.onPrimary
    val container by animateColorAsState(
        when {
            netplayDone -> good()
            waitingForHost -> primary.copy(alpha = 0.16f)
            else -> primary
        },
        Motion.tint(),
        label = "netplay-container"
    )
    val content by animateColorAsState(
        if (waitingForHost) primary.copy(alpha = 0.55f) else onPrimary,
        Motion.tint(),
        label = "netplay-content"
    )

    val on = rememberAnimationsEnabled()
    val wake = remember { Animatable(1f) }
    var wasWaiting by remember { mutableStateOf(waitingForHost) }
    val pop = Motion.pop<Float>()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(waitingForHost) {
        if (wasWaiting && !waitingForHost) {
            awaitSeen(lifecycle)
            Sfx.pop()
            if (on) {
                wake.snapTo(WAKE_FROM)
                wake.animateTo(1f, pop)
            }
        }
        wasWaiting = waitingForHost
    }

    Button(
        onClick = sounded(onClick),
        enabled = enabled && !busy,
        shape = ActionShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = content,
            disabledContainerColor = container,
            disabledContentColor = content
        ),
        modifier = modifier
            .height(56.dp)
            .graphicsLayer {
                scaleX = wake.value
                scaleY = wake.value
            }
            .controlRing(ActionShape)
            .then(if (enabled) Modifier else Modifier.focusable())
    ) {
        when {
            netplayDone -> {
                DrawnCheck(
                    done = true,
                    disc = onPrimary.copy(alpha = 0.22f),
                    ink = onPrimary
                )
                Spacer(Modifier.width(10.dp))
            }
            busy || waitingForHost -> {
                TrailerSpinner(
                    color = content,
                    size = 18.dp,
                    stroke = 2.5.dp,
                    fps = if (waitingForHost) 30 else 60,
                    modifier = Modifier.bloom(rememberAppear()::value, blur = 0.dp)
                )
                Spacer(Modifier.width(10.dp))
            }
        }
        CrossLabel(
            stringResource(
                when {
                    waitingForHost -> R.string.session_netplay_waiting_host
                    netplayDone -> R.string.session_netplay_done
                    busy -> R.string.session_netplay_busy
                    netplayPrepared -> R.string.session_netplay_again
                    else -> netPlayReadyStrRes
                },
                // Never hard-code the emulator name in the string.
                session.backend.emulatorName
            )
        )
    }
}

private const val WAKE_AFTER_TICK_MS = 380L

private const val WAKE_FROM = 0.92f

internal val LocalNetplayBusy = compositionLocalOf { false }

@Composable
private fun CrossLabel(text: String) {
    val enter: FiniteAnimationSpec<Float> = Motion.enter()
    val exit: FiniteAnimationSpec<Float> = Motion.exit()
    AnimatedContent(
        targetState = text,
        transitionSpec = {
            (fadeIn(enter) + scaleIn(enter, initialScale = 0.965f)) togetherWith
                (fadeOut(exit) + scaleOut(exit, targetScale = 0.965f))
        },
        label = "button-label"
    ) { label ->
        Text(label, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
internal fun PspSetupButton(
    pspOpened: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = sounded(onClick),
        shape = ActionShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = animateColorAsState(
                if (pspOpened) good() else MaterialTheme.colorScheme.primary,
                Motion.tint(),
                label = "psp-container"
            ).value
        ),
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .controlRing(ActionShape)
    ) {
        if (pspOpened) {
            DrawnCheck(
                done = true,
                disc = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.22f),
                ink = MaterialTheme.colorScheme.onPrimary
            )
            Spacer(Modifier.width(10.dp))
        }
        CrossLabel(
            stringResource(
                if (pspOpened) R.string.session_psp_setup_again
                else R.string.session_psp_setup
            )
        )
    }
}

@Composable
internal fun LaunchButton(
    session: Session,
    netplayPrepared: Boolean,
    modifier: Modifier = Modifier,
    directPs2: Boolean = false,
    waitingForHost: Boolean = false,
    onClick: () -> Unit
) {
    val enabled = launchEnabled(session, netplayPrepared, directPs2, waitingForHost)
    val primary = MaterialTheme.colorScheme.primary
    val container by animateColorAsState(
        if (enabled) primary else primary.copy(alpha = 0.16f),
        Motion.tint(),
        label = "launch-container"
    )
    val content by animateColorAsState(
        if (enabled) MaterialTheme.colorScheme.onPrimary else primary.copy(alpha = 0.55f),
        Motion.tint(),
        label = "launch-content"
    )
    val on = rememberAnimationsEnabled()
    val wake = remember { Animatable(1f) }
    var wasEnabled by remember { mutableStateOf(enabled) }
    val pop = Motion.pop<Float>()
    val launchLifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(enabled) {
        if (!wasEnabled && enabled && on) {
            awaitSeen(launchLifecycle)
            delay(WAKE_AFTER_TICK_MS)
            wake.snapTo(WAKE_FROM)
            wake.animateTo(1f, pop)
        }
        wasEnabled = enabled
    }
    Button(
        onClick = sounded(onClick),
        enabled = enabled,
        shape = ActionShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = content,
            disabledContainerColor = container,
            disabledContentColor = content
        ),
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .graphicsLayer {
                scaleX = wake.value
                scaleY = wake.value
            }
            .controlRing(ActionShape)
    ) {
        Text(
            launchLabel(session, directPs2, waitingForHost),
            style = MaterialTheme.typography.titleMedium
        )
    }
}

@Composable
internal fun launchLabel(
    session: Session,
    directPs2: Boolean,
    waitingForHost: Boolean
): String = when {
    waitingForHost && session.backend == Backend.MELONDS ->
        stringResource(R.string.session_ds_waiting_host)
    waitingForHost -> stringResource(
        R.string.session_netplay_waiting_host,
        session.backend.emulatorName,
    )
    session.rom == null -> stringResource(R.string.session_no_rom)
    session.backend == Backend.AZAHAR ||
            session.backend == Backend.EDEN ||
            session.backend == Backend.PPSSPP ||
            session.backend == Backend.MELONDS ||
            session.backend == Backend.ARMSX2 ->
        stringResource(
            if (session.backend.hasNetplay && !directPs2) R.string.session_launch_step2
            else R.string.session_launch_emulation
        )

    session.backend == Backend.DOLPHIN -> stringResource(R.string.session_dolphin_lobby)
    else -> stringResource(R.string.session_unsupported_short)
}

internal fun launchWaits(session: Session, directPs2: Boolean, waitingForHost: Boolean): Boolean =
    (directPs2 || session.backend == Backend.MELONDS) && waitingForHost

internal fun launchEnabled(
    session: Session,
    netplayPrepared: Boolean,
    directPs2: Boolean,
    waitingForHost: Boolean
): Boolean =
    session.rom != null && session.backend != Backend.NONE && !waitingForHost &&
            (!session.backend.hasNetplay || netplayPrepared || directPs2)

@Composable
internal fun SessionCodeChip(code: String, onCopy: () -> Unit) {
    val dark = LocalEmufiiDarkTheme.current
    // Round with an explicit height, or `Surface(onClick)` reserves 48 dp and paints inside it.
    Surface(
        onClick = sounded(onCopy),
        shape = CircleShape,
        color = if (dark) Teal.bright else Teal.deep,
        modifier = Modifier
            .controlRing(CircleShape)
            .liftShadow(CircleShape, 2.dp, dark, LocalEmufiiOledTheme.current && dark)
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 48.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                stringResource(R.string.session_code_label).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = if (dark) Teal.ink else Color.White.copy(alpha = 0.80f),
                letterSpacing = 1.sp
            )
            val ink = if (dark) Teal.ink else Color.White
            val style = TextStyle(
                fontSize = 20.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp
            )
            if (code.isBlank()) Text("—", style = style, color = ink)
            else RevealCode(code, style = style, color = ink)
        }
    }
}

@Composable
internal fun StatusLine(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = modifier.fillMaxWidth()
    )
}

@Composable
internal fun LeaveButton(
    session: Session,
    onLeave: () -> Unit,
    modifier: Modifier = Modifier,
    fillWidth: Boolean = true
) {
    val dark = LocalEmufiiDarkTheme.current
    val oled = LocalEmufiiOledTheme.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        modifier = (if (fillWidth) modifier.fillMaxWidth() else modifier)
            .heightIn(min = 48.dp)
            .controlRing(PillShape)
            .plate(shape = PillShape, dark = dark, oled = oled, lift = 4.dp, pressed = pressed)
            .tap(interactionSource = interaction, indication = null, onClick = onLeave)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            if (session.role == Session.Role.HOST) stringResource(R.string.session_close)
            else stringResource(R.string.session_leave),
            style = MaterialTheme.typography.labelLarge,
            color = danger()
        )
    }
}

@Composable
private fun CheckMark(color: Color, size: Dp = 18.dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val stroke = Stroke(width = w * 0.16f, cap = StrokeCap.Round)
        val path = Path().apply {
            moveTo(w * 0.16f, w * 0.55f)
            lineTo(w * 0.40f, w * 0.79f)
            lineTo(w * 0.86f, w * 0.24f)
        }
        drawPath(path, color = color, style = stroke)
    }
}

@Composable
private fun InfoMark(color: Color, size: Dp = 24.dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val strokeWidth = w * 0.12f
        drawCircle(color = color, radius = w * 0.44f, style = Stroke(width = strokeWidth))
        drawCircle(color = color, radius = w * 0.07f, center = Offset(w / 2f, w * 0.28f))
        drawLine(
            color = color,
            start = Offset(w / 2f, w * 0.44f),
            end = Offset(w / 2f, w * 0.76f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
    }
}
