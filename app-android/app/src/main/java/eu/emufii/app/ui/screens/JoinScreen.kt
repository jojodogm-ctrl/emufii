package eu.emufii.app.ui.screens

import eu.emufii.app.ui.theme.Teal
import eu.emufii.app.ui.sounded
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.emufii.app.ui.controlRing
import eu.emufii.app.R
import androidx.compose.ui.text.TextStyle
import androidx.compose.runtime.key
import eu.emufii.app.ui.CodeGlyph
import eu.emufii.app.network.CoordinatorClient
import eu.emufii.app.session.RomRef
import eu.emufii.app.session.SessionCodes
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import eu.emufii.app.ui.components.EmufiiCodeKeyboard
import eu.emufii.app.ui.components.EmufiiScaffold
import eu.emufii.app.ui.components.LandOn
import eu.emufii.app.ui.components.padEntry
import eu.emufii.app.secondscreen.LEGEND_CAP
import eu.emufii.app.secondscreen.PadHint
import eu.emufii.app.secondscreen.PadHintRow
import eu.emufii.app.ui.theme.LocalEmufiiDarkTheme
import eu.emufii.app.ui.theme.socket
import eu.emufii.app.ui.focusRing
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import eu.emufii.app.ui.theme.PillShape
import eu.emufii.app.ui.theme.Coral
import eu.emufii.app.ui.LocalRingTone
import eu.emufii.app.ui.RingTone
import eu.emufii.app.ui.ringColor

private const val CODE_LENGTH = 6

private fun coralCut(dark: Boolean) = if (dark) Teal.darkBright else Teal.deep

/**
 * Six boxes rather than a form. The app's own keypad replaces the invisible field.
 * pourquoi : docs/decisions/coquille-ecrans.md § Join: the app's keyboard rather than an invisible field
 * pourquoi : docs/decisions/coquille-ecrans.md § Six slots rather than a field
 */
@Composable
fun JoinScreen(
    rom: RomRef,
    client: CoordinatorClient,
    onBack: () -> Unit,
    onSubmitCode: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var code by remember { mutableStateOf("") }
    val complete = code.length == CODE_LENGTH
    val dark = LocalEmufiiDarkTheme.current
    val landing = remember { FocusRequester() }

    // B erases a box and leaves the screen only once the code is empty: the keypad has no delete key.
    // pourquoi : docs/decisions/coquille-ecrans.md § The code keyboard is not the search keyboard
    BackHandler(enabled = code.isNotEmpty()) { code = code.dropLast(1) }

    // The social domain: the pad cursor turns coral here.
    // pourquoi : docs/decisions/theme-duotone-shelves.md § GAMEPAD FOCUS
    CompositionLocalProvider(LocalRingTone provides RingTone.CORAL) {
    EmufiiScaffold(
        title = stringResource(R.string.join_title),
        modifier = modifier,
        onBack = onBack,
        contentScrolls = false,
        // The scaffold would put the cursor on the first control, the Join button.
        autoFocus = false
    ) { _ ->
        LandOn(landing)

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 24.dp, end = 24.dp, top = 28.dp, bottom = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(22.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    rom.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    // Outside any Surface: with no explicit colour it falls back to black.
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )

                // Above the boxes: under them the template read as a seventh, fainter line.
                Text(
                    stringResource(R.string.join_code_example),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(CODE_LENGTH) { i ->
                        CodeSlot(
                            char = code.getOrNull(i),
                            // The current box, the last once the code is complete: the accent has to land somewhere.
                            active = i == code.length.coerceAtMost(CODE_LENGTH - 1)
                        )
                        if (i == 2) Separator()
                    }
                }

                Box(modifier = Modifier.height(LEGEND_CAP)) {
                    if (code.isNotEmpty()) PadHintRow(PadHint.ERASE)
                }

                Button(
                    onClick = sounded { onSubmitCode(SessionCodes.normalize(code)) },
                    enabled = complete,
                    shape = PillShape,
                    // Joining is a link: a coral pill, the deep cut on light, the bright one on dark.
                    // pourquoi : docs/decisions/theme-duotone-shelves.md § Two semantic axes
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (dark) Teal.bright else Teal.deep,
                        contentColor = if (dark) Teal.ink else Color.White,
                        disabledContainerColor = coralCut(dark).copy(alpha = 0.16f),
                        disabledContentColor = coralCut(dark).copy(alpha = 0.55f)
                    ),
                    modifier = Modifier.width(240.dp).height(50.dp).controlRing(PillShape).padEntry()
                ) {
                    // The disabled button says what is missing; a grey "Join" did not say why.
                    Text(
                        if (complete) stringResource(R.string.join_action)
                        else pluralStringResource(
                            R.plurals.join_code_remaining,
                            CODE_LENGTH - code.length,
                            CODE_LENGTH - code.length
                        )
                    )
                }
            }

            // The keypad carries the cursor on arrival: the only thing here to press.
            Box(modifier = Modifier.weight(1.05f)) {
                EmufiiCodeKeyboard(
                    firstKeyFocus = landing,
                    onKey = { c -> if (code.length < CODE_LENGTH) code += c },
                    maxHeight = LocalConfiguration.current.screenHeightDp.dp * 0.70f
                )
            }
        }
    }
    }
}

/**
 * A recess rather than a plate: a code is typed into something.
 * pourquoi : docs/decisions/coquille-ecrans.md § Six slots rather than a field
 */
@Composable
private fun CodeSlot(char: Char?, active: Boolean) {
    val dark = LocalEmufiiDarkTheme.current
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = Modifier
            // Sized for the left column: at 56 dp, six sockets and their dash overflowed it.
            .size(width = 48.dp, height = 66.dp)
            .focusRing(active, shape, width = 3.dp, glowRadius = 16.dp)
            .socket(shape, dark)
            .then(
                if (active) Modifier.background(Teal.soft, shape) else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        if (char != null) {
            // Keyed on the character, so a correction drops the new one in again.
            key(char) {
                CodeGlyph(
                    char.toString(),
                    style = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        } else if (active) {
            // The empty active slot used to paint a pale block the size of a glyph.
            Caret()
        }
    }
}

@Composable
private fun Caret() {
    val blink = rememberInfiniteTransition(label = "caret")
    val alpha by blink.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "caret-blink"
    )
    Box(
        modifier = Modifier
            .size(width = 3.dp, height = 34.dp)
            .background(
                ringColor().copy(alpha = alpha),
                RoundedCornerShape(2.dp)
            )
    )
}

@Composable
private fun Separator() {
    Box(
        modifier = Modifier
            .padding(horizontal = 3.dp)
            .size(width = 12.dp, height = 2.dp)
            .clip(PillShape)
            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f))
    )
}
