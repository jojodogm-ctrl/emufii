package eu.emufii.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.emufii.app.R
import eu.emufii.app.library.EmulatorInfo
import eu.emufii.app.library.EmulatorVariant
import eu.emufii.app.ui.controlRing
import eu.emufii.app.ui.theme.LocalEmufiiDarkTheme
import eu.emufii.app.ui.theme.socket
import eu.emufii.app.ui.tap
import eu.emufii.app.ui.Sfx

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ConsoleRow(
    info: EmulatorInfo,
    visible: Boolean,
    onSetVisible: (Boolean) -> Unit,
    onPickVariant: (String) -> Unit,
    modifier: Modifier = Modifier,
    entry: Boolean = false
) {
    val dark = LocalEmufiiDarkTheme.current
    val alpha = if (visible) 1f else 0.45f
    val iconFilter = remember(visible) {
        if (visible) null
        else ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .socket(ROW_SHAPE, dark)
            // Like [SwitchRow]: toggles under a finger, but is not a cursor stop.
            .focusProperties { canFocus = false }
            .tap(role = Role.Switch) { onSetVisible(!visible) }
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .alpha(alpha)
                .clip(RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (info.icon != null) {
                Image(
                    bitmap = info.icon,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    colorFilter = iconFilter,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Text(
                    info.console.shortLabel,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Text(
            info.console.label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.weight(1f)
        ) {
            if (info.variants.isEmpty()) {
                Text(
                    absentLine(info),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            } else {
                info.variants.forEach { variant ->
                    VariantChip(
                        variant = variant,
                        selected = variant.packageName == info.installedPackage,
                        enabled = visible,
                        onClick = { onPickVariant(variant.packageName) }
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                // Thicker than elsewhere: the switch is a small pill ending a wide row.
                .controlRing(
                    androidx.compose.foundation.shape.CircleShape,
                    bandFraction = 0.165f
                )
                .then(if (entry) Modifier.padEntry() else Modifier)
                .tap(role = Role.Switch, sound = Sfx::toggle) { onSetVisible(!visible) }
        ) {
            SwitchFace(checked = visible)
        }
    }
}

@Composable
private fun VariantChip(
    variant: EmulatorVariant,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val tint =
        if (selected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .alpha(if (enabled) 1f else 0.45f)
            .controlRing(CHIP_SHAPE)
            .clip(CHIP_SHAPE)
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.08f)
            )
            .tap(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            variant.label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        variant.version?.let {
            Text(
                shortVersion(it),
                style = MaterialTheme.typography.labelSmall,
                color = tint.copy(alpha = 0.7f),
                maxLines = 1
            )
        }
    }
}

@Composable
private fun absentLine(info: EmulatorInfo): String =
    "${info.name} · ${stringResource(R.string.emulators_absent_short)}"

private val ROW_SHAPE = RoundedCornerShape(16.dp)
private val CHIP_SHAPE = RoundedCornerShape(9.dp)

/** PPSSPP versions already start with "v". */
private fun shortVersion(version: String): String =
    version.removePrefix("v").removePrefix("V")
