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
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import eu.emufii.app.ui.CONFIRM_KEYS
import eu.emufii.app.ui.controlRing
import eu.emufii.app.ui.ringColor
import eu.emufii.app.ui.Sfx

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
    shape: Shape = RoundedCornerShape(FIELD_CORNER),
    selectAllOnEdit: Boolean = false
) {
    var editing by remember { mutableStateOf(false) }
    var fieldValue by remember { mutableStateOf(TextFieldValue(value)) }
    if (fieldValue.text != value) fieldValue = TextFieldValue(value, TextRange(value.length))
    val frame = remember { FocusRequester() }
    val field = remember { FocusRequester() }
    val interaction = remember { MutableInteractionSource() }

    val framed by interaction.collectIsFocusedAsState()
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(editing) {
        if (editing) {
            if (selectAllOnEdit) fieldValue = fieldValue.copy(selection = TextRange(0, fieldValue.text.length))
            runCatching { field.requestFocus() }
            keyboard?.show()
        } else {
            keyboard?.hide()
        }
    }

    BackHandler(enabled = editing) {
        editing = false
        runCatching { frame.requestFocus() }
    }

    // The keyboard swallows the first B, so its disappearance ends the edit.
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
        if (label != null) {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = if (isError) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
            )
        }
        Box(
            modifier = Modifier
                .controlRing(shape, enabled = !editing)
                .cardSliceFill(shape)
                .focusRequester(frame)
                .focusable(interactionSource = interaction)
                .onKeyEvent { event ->
                    if (editing) return@onKeyEvent false
                    if (event.key in CONFIRM_KEYS) {
                        if (event.type == KeyEventType.KeyUp) { Sfx.click(); editing = true }
                        true
                    } else {
                        false
                    }
                }
        ) {
            OutlinedTextField(
                value = fieldValue,
                onValueChange = {
                    fieldValue = it
                    if (it.text != value) onValueChange(it.text)
                },
                label = null,
                placeholder = placeholder?.let { { Text(it) } },
                isError = isError,
                singleLine = singleLine,
                shape = shape,
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                colors = OutlinedTextFieldDefaults.colors(
                    cursorColor = ringColor(),
                    focusedBorderColor = ringColor(),
                    unfocusedBorderColor =
                        if (framed) Color.Transparent
                        else MaterialTheme.colorScheme.outline,
                    disabledBorderColor =
                        if (framed) Color.Transparent
                        else MaterialTheme.colorScheme.outline,
                    errorBorderColor =
                        if (framed) Color.Transparent
                        else MaterialTheme.colorScheme.error
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(field)
                    .focusProperties { canFocus = editing }
                    .onFocusChanged { if (editing && !it.isFocused) editing = false }
            )

            // Compose tests children first, and the field consumed taps it did nothing with.
            if (!editing) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .pointerInput(Unit) { detectTapGestures { editing = true } }
                )
            }
        }
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

private val FIELD_CORNER = 16.dp

