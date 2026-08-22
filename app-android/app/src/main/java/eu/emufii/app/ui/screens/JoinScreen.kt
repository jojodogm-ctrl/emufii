package eu.emufii.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.emufii.app.ui.controlRing
import eu.emufii.app.R
import eu.emufii.app.network.CoordinatorClient
import eu.emufii.app.session.RomRef
import eu.emufii.app.session.SessionCodes
import eu.emufii.app.ui.components.EmufiiScaffold
import eu.emufii.app.ui.components.padEntry
import eu.emufii.app.ui.theme.LocalEmufiiDarkTheme
import eu.emufii.app.ui.theme.socket
import eu.emufii.app.ui.focusRing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import eu.emufii.app.ui.theme.PillShape
import eu.emufii.app.ui.theme.PlateDark
import eu.emufii.app.ui.theme.PlateLight
import eu.emufii.app.ui.theme.EdgeDark
import eu.emufii.app.ui.theme.EdgeLight

/** A session code is six characters; the hyphen only helps to read it. */
private const val CODE_LENGTH = 6

/**
 * Entering the code you were given.
 *
 * This used to be a full-width `OutlinedTextField` with its label and its helper
 * text, centred in a column, a form where there is only one thing to type, and
 * whose field spanned the screen's 784 dp for six characters.
 *
 * Six boxes instead. We know in advance how many are needed, so we may as well
 * show it: progress is visible without reading, the current box carries the
 * accent, and the code is displayed at the size you read it at arm's length. The
 * input field still exists, invisible, under the boxes, because it is what
 * brings the keyboard, the selection and pasting without our having to rewrite
 * them.
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
    val focus = remember { FocusRequester() }
    val complete = code.length == CODE_LENGTH

    // The IME key closes the keyboard, and stops there.
    //
    // `ImeAction` only draws the key; without a `KeyboardActions` to answer it,
    // pressing it did nothing at all — the IME stayed up over the screen and the
    // back button was the only way out, on a screen whose whole job is to take
    // six characters. What it must not do is start the session: the screen
    // already has a Join button for that, and a keyboard key that launches
    // straight from the last character takes the decision out of the player's
    // hands, with no chance to reread the code.
    //
    // Dismissing also matters for the Join button itself: the code can be
    // complete while the IME is still up, and a session would then start under a
    // keyboard nobody closed.
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val dismissKeyboard = {
        keyboard?.hide()
        focusManager.clearFocus()
    }

    // No automatic keyboard, and that is deliberate.
    //
    // In landscape on this machine the IME opens fullscreen (extract mode) and
    // covers everything: you arrived on a bare text editor, having never seen the
    // six boxes or the name of the game. The keyboard comes when the boxes are
    // touched, that is, once you have decided to type.
    //
    // Fullscreen itself is not ours to control, the IME decides that on a short
    // screen, but being subjected to it without having seen the screen is.

    EmufiiScaffold(
        title = stringResource(R.string.join_title),
        modifier = modifier,
        onBack = onBack,
        contentScrolls = false
    ) { _ ->
        // Centred on the screen, not under the header.
        //
        // Reserving `topPadding` centred the whole thing within what was left
        // below the title: 90 px too low. Nothing here reaches the header, the
        // block being 212 dp of the device's 468, so there is no room to reserve
        // for it. The same margin difference as on the PSP online screen (32
        // against 12) makes up for the title's visual weight in the corner.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 24.dp, end = 24.dp, top = 32.dp, bottom = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Text(
                    rom.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    // Laid on the background, outside any Surface: with no
                    // explicit colour it falls back to black and disappears in
                    // the dark theme.
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )

                BasicTextField(
                    value = code,
                    onValueChange = { new ->
                        // The hyphen is typed or not, it is the same code; we
                        // keep only what counts, and never more than six.
                        code = new.uppercase()
                            .filter { it.isLetterOrDigit() }
                            .take(CODE_LENGTH)
                    },
                    singleLine = true,
                    textStyle = TextStyle(color = Color.Transparent),
                    // The field's caret has nothing to draw: the boxes are what
                    // show where you are.
                    cursorBrush = SolidColor(Color.Transparent),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        keyboardType = KeyboardType.Ascii,
                        imeAction = ImeAction.Done,
                        // No autocorrect, and that is not about spelling: it is
                        // what stops the keyboard opening a *composing region*
                        // on the field. The field's own caret and text are both
                        // transparent, so the pale block sitting in the lit
                        // socket was the keyboard's composing highlight, drawn
                        // over a code that has nothing to correct.
                        autoCorrectEnabled = false
                    ),
                    keyboardActions = KeyboardActions(onDone = { dismissKeyboard() }),
                    modifier = Modifier.focusRequester(focus),
                    decorationBox = {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(9.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            repeat(CODE_LENGTH) { i ->
                                CodeSlot(
                                    char = code.getOrNull(i),
                                    // The current box, and the last one once the
                                    // code is complete: the accent has to land
                                    // somewhere.
                                    active = i == code.length.coerceAtMost(CODE_LENGTH - 1)
                                )
                                // The dash is a reading aid, in the middle, and
                                // not a character to type.
                                if (i == 2) Separator()
                            }
                        }
                    }
                )

                Text(
                    stringResource(R.string.join_code_example),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Button(
                    onClick = {
                        dismissKeyboard()
                        onSubmitCode(SessionCodes.normalize(code))
                    },
                    enabled = complete,
                    shape = PillShape,
                    // Material's disabled pill is grey on grey, under 3:1 on this
                    // tray: the button read as an absence rather than as a
                    // control waiting for six characters. Tinted from the accent
                    // instead, it stays visibly the thing that will take you in.
                    colors = ButtonDefaults.buttonColors(
                        disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                        disabledContentColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                    ),
                    modifier = Modifier.width(260.dp).height(52.dp).controlRing(PillShape).padEntry()
                ) { Text(stringResource(R.string.join_action)) }
            }
        }
    }
}

/**
 * One socket in the code strip, empty or filled, lit when it is its turn.
 *
 * A recess rather than a plate: a code is typed *into* something. The lit one
 * carries the cursor's own ring, the same object the tiles wear, so "where am
 * I" has one answer everywhere in the app instead of a coloured border here and
 * a glow there.
 */
@Composable
private fun CodeSlot(char: Char?, active: Boolean) {
    val dark = LocalEmufiiDarkTheme.current
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = Modifier
            .size(width = 56.dp, height = 72.dp)
            .focusRing(active, shape, width = 3.dp, glowRadius = 16.dp)
            .socket(shape, dark)
            // A floor darker than the tray. At the tray's own value the six
            // recesses vanished into the ground and the strip read as a row of
            // ghosts; a code field has to look like somewhere a character goes.
            .background(if (dark) Color(0x33000000) else Color(0x14101A2A), shape),
        contentAlignment = Alignment.Center
    ) {
        if (char != null) {
            Text(
                char.toString(),
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        } else if (active) {
            // A caret, and a thin one. The empty active slot used to paint a
            // pale block the size of a glyph, which read as a character already
            // typed: the player's own code looked half entered before they had
            // touched a key.
            Caret()
        }
    }
}

/** The blink of a text cursor: a bar, on and off, nothing else moving. */
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
                MaterialTheme.colorScheme.primary.copy(alpha = alpha),
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
