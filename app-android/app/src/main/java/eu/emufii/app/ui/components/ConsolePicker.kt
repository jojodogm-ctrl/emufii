package eu.emufii.app.ui.components

import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import eu.emufii.app.R
import eu.emufii.app.library.Console
import eu.emufii.app.library.EmulatorInfo
import eu.emufii.app.library.allEmulators
import eu.emufii.app.ui.controlRing
import eu.emufii.app.ui.theme.LocalEmufiiDarkTheme
import eu.emufii.app.ui.theme.LocalEmufiiOledTheme
import eu.emufii.app.ui.theme.plate
import eu.emufii.app.ui.theme.socket
import eu.emufii.app.ui.theme.TileShape

@Composable
fun ConsoleGrid(
    hidden: Set<Console>,
    onSetVisible: (Console, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    firstTileIsEntry: Boolean = false,
    compact: Boolean = false,
    oneLine: Boolean = false
) {
    val context = LocalContext.current
    val emulators = remember { allEmulators(context) }

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val minTile = if (compact) MIN_TILE_COMPACT else MIN_TILE
        val fits = ((maxWidth + GRID_GAP) / (minTile + GRID_GAP))
            .toInt()
            .coerceIn(3, emulators.size)
        val columns = if (oneLine) emulators.size else balancedColumns(emulators.size, fits)

        // `controlRing`'s `zIndex` only orders siblings, so not rows.
                var focusedRow by remember { mutableStateOf(-1) }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(GRID_GAP)
        ) {
            emulators.chunked(columns).forEachIndexed { rowIndex, row ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(GRID_GAP),
                    modifier = Modifier.zIndex(if (rowIndex == focusedRow) 1f else 0f)
                ) {
                    row.forEachIndexed { index, info ->
                        ConsoleTile(
                            info = info,
                            visible = info.console !in hidden,
                            onToggle = { onSetVisible(info.console, info.console in hidden) },
                            entry = firstTileIsEntry && rowIndex == 0 && index == 0,
                            compact = compact,
                            onFocused = { if (it) focusedRow = rowIndex },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    repeat(columns - row.size) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

internal fun balancedColumns(count: Int, fits: Int): Int {
    if (count <= fits) return count
    var best = fits
    var bestGap = Int.MAX_VALUE
    for (c in fits downTo 3) {
        val gap = (c - count % c) % c
        if (gap < bestGap) {
            best = c
            bestGap = gap
        }
    }
    return best
}

private val MIN_TILE = 118.dp

private val GRID_GAP = 8.dp

@Composable
private fun ConsoleTile(
    info: EmulatorInfo,
    visible: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    entry: Boolean = false,
    compact: Boolean = false,
    onFocused: (Boolean) -> Unit = {}
) {
    val alpha = if (visible) 1f else 0.45f

    val iconFilter = remember(visible) {
        if (visible) null
        else ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
    }
    val dark = LocalEmufiiDarkTheme.current
    val oled = LocalEmufiiOledTheme.current

    Column(
        modifier = modifier
            .height(if (compact) TILE_HEIGHT_COMPACT else TILE_HEIGHT)
            .onFocusEvent { onFocused(it.hasFocus) }
            // Before clickable, or the focusRequester misses the clickable's focus node.
            .then(if (entry) Modifier.padEntry() else Modifier)
            // Ring before the clip, or its glow is cut to the tile shape.
            .controlRing(TILE_SHAPE)
            .then(
                if (visible) Modifier.plate(shape = TILE_SHAPE, dark = dark, oled = oled, lift = 5.dp)
                else Modifier.socket(TILE_SHAPE, dark)
            )
            .clickable { onToggle() }
            .padding(vertical = 10.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically)
    ) {
        Box(
            modifier = Modifier
                .size(if (compact) 32.dp else 40.dp)
                .alpha(alpha)
                .clip(RoundedCornerShape(11.dp)),
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
                val art = consoleArtwork(info.console, LocalEmufiiDarkTheme.current)
                if (art != null) {
                    Image(
                        painter = painterResource(art),
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
        }
        Text(
            if (compact && info.console == Console.GAMECUBE) info.console.shortLabel else info.console.label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
        Text(
            info.name,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha * 0.85f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
        if (!compact) {
            Text(
                info.version?.let { stringResource(R.string.emulators_version_short, shortVersion(it)) }
                    ?: if (info.installed) stringResource(R.string.emulators_installed_unknown)
                    else stringResource(R.string.emulators_absent_short),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

private val TILE_HEIGHT = 124.dp

private val TILE_HEIGHT_COMPACT = 92.dp

private val MIN_TILE_COMPACT = 92.dp

private val TILE_SHAPE = TileShape

private fun shortVersion(version: String): String =
    version.removePrefix("v").removePrefix("V")
