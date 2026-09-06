package eu.emufii.app.ui.screens.session

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

/**
 * Green once the room is actually joined, not once the emulator was merely opened.
 * pourquoi : docs/decisions/session.md § Two proofs that a room exists, and the second is knowingly weaker
 */
@Composable
internal fun AutoSetupNetplayButton(
    session: Session,
    netplayDone: Boolean,
    /**
     * The button greys out and says so rather than sending the guest to a room that does not exist.
     * pourquoi : docs/decisions/session.md § Host then guest is not a comfort detail
     */
    waitingForHost: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    NetplayButtonContainer(
        session = session,
        netPlayReadyStrRes = R.string.session_netplay_open,
        netplayDone = netplayDone,
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
    Button(
        onClick = sounded(onClick),
        enabled = enabled,
        shape = ActionShape,
        colors = if (netplayDone) {
            ButtonDefaults.buttonColors(containerColor = good())
        } else {
            ButtonDefaults.buttonColors()
        },
        modifier = modifier
            .height(56.dp)
            .controlRing(ActionShape)
            // Greyed out but still reachable: focus says where you are, not that a click lands.
            // pourquoi : docs/decisions/session.md § Down aims at the first button that answers
            .then(if (enabled) Modifier else Modifier.focusable())
    ) {
        if (netplayDone) {
            CheckMark(color = MaterialTheme.colorScheme.onPrimary)
            Spacer(Modifier.width(10.dp))
        }
        Text(
            stringResource(
                when {
                    waitingForHost -> R.string.session_netplay_waiting_host
                    netplayDone -> R.string.session_netplay_done
                    netplayPrepared -> R.string.session_netplay_again
                    else -> netPlayReadyStrRes
                },
                // The emulator this session drives: "Azahar" was hard-coded in the string, so a
                // Switch session announced the wrong program by name.
                session.backend.emulatorName
            ),
            style = MaterialTheme.typography.titleMedium
        )
    }
}

/**
 * PPSSPP has no netplay to drive: this opens the emulator, and the label says exactly that.
 * pourquoi : docs/decisions/session.md § The per-console cards, and what each must prevent
 */
@Composable
internal fun PspSetupButton(
    pspOpened: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = sounded(onClick),
        shape = ActionShape,
        colors = if (pspOpened) {
            ButtonDefaults.buttonColors(containerColor = good())
        } else {
            ButtonDefaults.buttonColors()
        },
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .controlRing(ActionShape)
    ) {
        if (pspOpened) {
            CheckMark(color = MaterialTheme.colorScheme.onPrimary)
            Spacer(Modifier.width(10.dp))
        }
        Text(
            stringResource(
                if (pspOpened) R.string.session_psp_setup_again
                else R.string.session_psp_setup
            ),
            style = MaterialTheme.typography.titleMedium
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
    Button(
        onClick = sounded(onClick),
        enabled = launchEnabled(session, netplayPrepared, directPs2, waitingForHost),
        shape = ActionShape,
        // Material's grey-on-grey slab read as an absence rather than a button waiting for step 1.
        colors = ButtonDefaults.buttonColors(
            disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
            disabledContentColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
        ),
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .controlRing(ActionShape)
    ) {
        Text(
            launchLabel(session, directPs2, waitingForHost),
            style = MaterialTheme.typography.titleMedium
        )
    }
}

/**
 * pourquoi : docs/decisions/second-ecran.md § The panel takes the steps, because it is touch
 */
@Composable
internal fun launchLabel(
    session: Session,
    directPs2: Boolean,
    waitingForHost: Boolean
): String = when {
    waitingForHost -> stringResource(
        R.string.session_netplay_waiting_host,
        session.backend.emulatorName,
    )
    // Joining a game you do not own is a different case from an unsupported console.
    session.rom == null -> stringResource(R.string.session_no_rom)
    session.backend == Backend.AZAHAR ||
            session.backend == Backend.EDEN ||
            session.backend == Backend.PPSSPP ||
            // The PS2 is included: ARMSX2's `MainActivity` is exported and takes a `content://`.
            session.backend == Backend.ARMSX2 ->
        // Numbered only where a step 1 sits above it.
        stringResource(
            if (session.backend.hasNetplay && !directPs2) R.string.session_launch_step2
            else R.string.session_launch_emulation
        )

    session.backend == Backend.MELONDS_WFC -> stringResource(R.string.session_wfc_not_a_session)
    // Dolphin has no step 2, and saying so beats "not yet supported".
    // pourquoi : docs/decisions/session.md § The per-console cards, and what each must prevent
    session.backend == Backend.DOLPHIN -> stringResource(R.string.session_dolphin_lobby)
    else -> stringResource(R.string.session_unsupported_short)
}

/** Greyed until the room step has run: launching first is what this pair prevents. */
internal fun launchEnabled(
    session: Session,
    netplayPrepared: Boolean,
    directPs2: Boolean,
    waitingForHost: Boolean
): Boolean =
    session.rom != null && session.backend != Backend.NONE && !waitingForHost &&
            (!session.backend.hasNetplay || netplayPrepared || directPs2)

/**
 * What you read out loud to someone else, so it stays visible at all times; a tap copies.
 * pourquoi : docs/decisions/session.md § This screen's drawing decisions
 */
@Composable
internal fun SessionCodeChip(code: String, onCopy: () -> Unit) {
    val dark = LocalEmufiiDarkTheme.current
    // Round with an explicit height, or `Surface(onClick)` reserves 48 dp and paints inside it.
    // pourquoi : docs/decisions/theme-duotone-shelves.md § Session / Join, coral domain
    Surface(
        onClick = sounded(onCopy),
        shape = CircleShape,
        color = if (dark) Coral.bright else Coral.deep,
        border = BorderStroke(1.dp, edgeColor(dark, oled = false)),
        shadowElevation = 4.dp,
        modifier = Modifier.controlRing(CircleShape)
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
                color = if (dark) Coral.ink else Color.White.copy(alpha = 0.80f),
                letterSpacing = 1.sp
            )
            Text(
                code.ifBlank { "—" },
                fontSize = 20.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp,
                color = if (dark) Coral.ink else Color.White
            )
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
    /** False in the header, where the button hugs the edge. */
    fillWidth: Boolean = true
) {
    // A moulded pill like everything else pressable: the destructive control must not be the
    // one made of nothing.
    // pourquoi : docs/decisions/session.md § This screen's drawing decisions
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

/**
 * Drawn rather than imported: it sits where it is put, where a glyph is centred on its line
 * box rather than its ink.
 * pourquoi : docs/decisions/session.md § This screen's drawing decisions
 */
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
