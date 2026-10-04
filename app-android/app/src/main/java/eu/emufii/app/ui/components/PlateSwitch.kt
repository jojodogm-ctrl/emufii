package eu.emufii.app.ui.components

import eu.emufii.app.ui.Motion
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import eu.emufii.app.ui.controlRing
import eu.emufii.app.ui.ringColor
import eu.emufii.app.ui.theme.LocalEmufiiDarkTheme
import eu.emufii.app.ui.theme.plate
import eu.emufii.app.ui.theme.socket
import eu.emufii.app.ui.tap
import androidx.compose.ui.unit.IntOffset
import eu.emufii.app.ui.Sfx
import eu.emufii.app.ui.rememberElastic

private val TRACK_WIDTH = 52.dp
private val TRACK_HEIGHT = 30.dp
private val KNOB = 24.dp
private val PAD = 3.dp

/** The row takes the touch, but only the switch takes focus (canFocus = false on the row). */
@Composable
fun SwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    note: String? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .focusProperties { canFocus = false }
            .tap(role = Role.Switch, sound = Sfx::toggle) { onCheckedChange(!checked) }
            .padding(vertical = 4.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            note?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Box(
            modifier = Modifier
                .controlRing(CircleShape)
                .tap(role = Role.Switch, sound = Sfx::toggle) { onCheckedChange(!checked) }
        ) {
            SwitchFace(checked = checked)
        }
    }
}

@Composable
fun SwitchFace(checked: Boolean) {
    val dark = LocalEmufiiDarkTheme.current
    val axis = ringColor()
    val knob = rememberElastic(
        target = if (checked) TRACK_WIDTH - KNOB - PAD else PAD,
        width = KNOB
    )
    val fill by animateColorAsState(
        targetValue = if (checked) axis.copy(alpha = 0.35f) else Color.Transparent,
        animationSpec = Motion.tint(),
        label = "switch-row-track"
    )
    Box(
        modifier = Modifier
            .size(width = TRACK_WIDTH, height = TRACK_HEIGHT)
            .socket(CircleShape, dark)
            .background(fill, CircleShape),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .offset { IntOffset(knob.start.roundToPx(), 0) }
                .size(width = knob.length, height = KNOB)
                .plate(shape = CircleShape, dark = false, oled = false, lift = 2.dp)
        )
    }
}
