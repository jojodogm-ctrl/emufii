package eu.emufii.app.ui.screens

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.unit.Dp
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
    BackHandler(enabled = code.isNotEmpty()) { code = code.dropLast(1) }

    CompositionLocalProvider(LocalRingTone provides RingTone.CORAL) {
    EmufiiScaffold(
        title = stringResource(R.string.join_title),
        modifier = modifier,
        onBack = onBack,
        contentScrolls = false,
        autoFocus = false
    ) { _ ->
        LandOn(landing)

        val configuration = LocalConfiguration.current
        val portrait = configuration.screenHeightDp > configuration.screenWidthDp
        val codeColumn: @Composable (Modifier) -> Unit = { columnModifier ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = columnModifier
            ) {
                Text(
                    rom.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    // Outside any Surface: with no explicit colour it falls back to black.
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )

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
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (dark) Teal.bright else Teal.deep,
                        contentColor = if (dark) Teal.ink else Color.White,
                        disabledContainerColor = coralCut(dark).copy(alpha = 0.16f),
                        disabledContentColor = coralCut(dark).copy(alpha = 0.55f)
                    ),
                    modifier = Modifier.width(240.dp).height(50.dp).controlRing(PillShape).padEntry()
                ) {
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
        }
        val keypad: @Composable (Modifier, Dp) -> Unit = { keypadModifier, keypadMaxHeight ->
            Box(modifier = keypadModifier) {
                EmufiiCodeKeyboard(
                    firstKeyFocus = landing,
                    onKey = { c -> if (code.length < CODE_LENGTH) code += c },
                    maxHeight = keypadMaxHeight
                )
            }
        }
        if (portrait) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 14.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                codeColumn(Modifier.fillMaxWidth())
                keypad(Modifier.fillMaxWidth(), configuration.screenHeightDp.dp * 0.42f)
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 24.dp, end = 24.dp, top = 28.dp, bottom = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(22.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                codeColumn(Modifier.weight(1f))
                keypad(Modifier.weight(1.05f), configuration.screenHeightDp.dp * 0.70f)
            }
        }
    }
    }
}

@Composable
private fun CodeSlot(char: Char?, active: Boolean) {
    val dark = LocalEmufiiDarkTheme.current
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = Modifier
            .size(width = 48.dp, height = 66.dp)
            .focusRing(active, shape, width = 3.dp, glowRadius = 16.dp)
            .socket(shape, dark)
            .then(
                if (active) Modifier.background(Teal.soft, shape) else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        if (char != null) {
            key(char) {
                CodeGlyph(
                    char.toString(),
                    style = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        } else if (active) {
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
