package eu.emufii.app.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import eu.emufii.app.ui.CONFIRM_KEYS
import eu.emufii.app.ui.controlRing

/**
 * A text field that waits to be asked before opening.
 *
 * The flaw is Compose's and not ours: an `OutlinedTextField` that takes focus
 * opens the soft keyboard. With a gamepad, where focus moves by traversing the
 * screen, merely *passing over* a field was enough for the keyboard to spring
 * up, cover the page and capture the directions; you were no longer traversing
 * a settings screen, you were falling into it.
 *
 * Here the field is not a step in the traversal: its frame is. The frame
 * announces itself with the usual green ring, and A, or a finger, goes into the
 * field. B comes back out and returns focus to the frame, so you carry on from
 * where you were instead of dropping back to the start of the screen.
 *
 * `canFocus` is denied to the field while not editing, and that is what really
 * keeps it out of the traversal: merely making it non-clickable would have left
 * it catching focus from a direction.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PadTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    supportingText: (@Composable () -> Unit)? = null,
    isError: Boolean = false,
    singleLine: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
    shape: Shape = RoundedCornerShape(FIELD_CORNER)
) {
    var editing by remember { mutableStateOf(false) }
    val frame = remember { FocusRequester() }
    val field = remember { FocusRequester() }
    val interaction = remember { MutableInteractionSource() }

    /** True while the frame holds the cursor, which is when the ring is drawn. */
    val framed by interaction.collectIsFocusedAsState()
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(editing) {
        if (editing) {
            runCatching { field.requestFocus() }
            keyboard?.show()
        } else {
            keyboard?.hide()
        }
    }

    // Leaving the field returns focus to the frame. Without that, closing the
    // keyboard left focus in a field that had become non-focusable, hence
    // nowhere: the directions stopped responding and the screen had to be
    // touched.
    BackHandler(enabled = editing) {
        editing = false
        runCatching { frame.requestFocus() }
    }

    // The keyboard swallows the first B, and the `BackHandler` above never sees
    // it, measured on the Thor: one press closed the keyboard while leaving the
    // field open and ringless, and a second was needed to get out. So it is the
    // keyboard's disappearance, and not the key, that ends the edit; the
    // `BackHandler` stays for the case where the keyboard is not there (a gamepad
    // with a physical keyboard, a hidden IME).
    //
    // [opened] exists because the keyboard is not visible yet at the instant we
    // enter the field: without it, the edit would close as soon as it opened.
    val imeVisible = WindowInsets.isImeVisible
    var opened by remember { mutableStateOf(false) }
    LaunchedEffect(editing, imeVisible) {
        if (!editing) {
            opened = false
        } else if (imeVisible) {
            opened = true
        } else if (opened) {
            editing = false
            runCatching { frame.requestFocus() }
        }
    }

    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                // The ring *is* the field's outline while the cursor is here,
                // and that is the only arrangement that holds.
                //
                // Two earlier tries both failed on the Thor, and measuring them
                // is what settled it. Drawing the ring on the same bounds put its
                // stroke on top of the field's own outline, two lines slightly
                // out of register. Insetting the field and widening the ring's
                // radius to match was meant to make them concentric, and did not:
                // measured at 4x, the gap was 4 dp at the sides and 11 dp at the
                // top, because `OutlinedTextField` does not fill the frame it is
                // given. No radius makes two curves parallel when the space
                // between them is not even to begin with.
                //
                // So the field's own border goes transparent under the cursor and
                // the ring takes its place, on the field's exact bounds and
                // shape. One outline at a time: there is nothing left to align.
                //
                // Before the `focusable`, and the order is everything. The ring
                // reads focus through `onFocusEvent`, which only sees the nodes
                // below it in the chain: placed after, it never saw the frame's
                // focus and stayed dark while the cursor was very much there, the
                // field scrolling to the centre of the screen without displaying
                // anything, which reads as a vanished cursor.
                .controlRing(shape, enabled = !editing)
                .focusRequester(frame)
                .focusable(interactionSource = interaction)
                .onKeyEvent { event ->
                    if (editing) return@onKeyEvent false
                    if (event.key in CONFIRM_KEYS) {
                        // Opened on release, as everywhere else in the app; the
                        // key-down is swallowed so one press does not count
                        // twice.
                        if (event.type == KeyEventType.KeyUp) editing = true
                        true
                    } else {
                        false
                    }
                }
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                label = label?.let { { Text(it) } },
                placeholder = placeholder?.let { { Text(it) } },
                isError = isError,
                singleLine = singleLine,
                shape = shape,
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                // Transparent exactly when the ring is drawn, so the two never
                // show at once. Editing puts the cursor inside the field, the
                // ring goes out, and Material's own outline comes back to say
                // where the caret is.
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor =
                        if (framed) Color.Transparent
                        else MaterialTheme.colorScheme.outline,
                    disabledBorderColor =
                        if (framed) Color.Transparent
                        else MaterialTheme.colorScheme.outline
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(field)
                    .focusProperties { canFocus = editing }
                    // The field can also lose focus other than through B, the
                    // keyboard closed with the system gesture, for instance. We
                    // then go back to frame mode, otherwise the ring never
                    // returns and the screen looks frozen.
                    .onFocusChanged { if (editing && !it.isFocused) editing = false }
            )

            // The finger did not reach the frame, and that is what stopped the
            // keyboard opening on touch. Reported on the onboarding screen, it is
            // true everywhere this field is used.
            //
            // The detection sat on the frame, below the field. But Compose tests
            // the children first, and `BasicTextField` installs its own pointer
            // handler to place the caret: it consumed the tap, then requested a
            // focus that `canFocus = false` refused. The gesture therefore
            // vanished between the two, with nothing moving on screen.
            //
            // This surface is drawn after the field, hence touched before it. It
            // only exists outside editing: once inside, the field has to get the
            // taps back so its caret can be placed.
            if (!editing) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .pointerInput(Unit) { detectTapGestures { editing = true } }
                )
            }
        }
        // Outside the frame, and therefore outside the ring: the helper text is
        // not the control being aimed at. It keeps the place and the style
        // `OutlinedTextField` gave it.
        if (supportingText != null) {
            Box(modifier = Modifier.padding(start = 20.dp, top = 4.dp)) {
                ProvideTextStyle(
                    MaterialTheme.typography.bodySmall.copy(
                        color = if (isError) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) { supportingText() }
            }
        }
    }
}

/** The field's radius. The ring needs it to trace a parallel outline. */
private val FIELD_CORNER = 16.dp

