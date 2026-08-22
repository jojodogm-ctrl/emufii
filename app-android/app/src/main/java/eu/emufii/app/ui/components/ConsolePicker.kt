package eu.emufii.app.ui.components

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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.style.TextAlign
import eu.emufii.app.R
import eu.emufii.app.library.Console
import eu.emufii.app.library.EmulatorInfo
import eu.emufii.app.library.allEmulators
import eu.emufii.app.ui.controlRing

/**
 * The consoles and the emulators that play them, on one screen, as tiles.
 *
 * This was two onboarding pages until 2026-08-19: an inventory to read, then a
 * list of switches. They asked the same question twice. The tile carries the
 * emulator's icon and version *and* is the control, so "what plays this" and
 * "do I want it" are one glance and one press.
 *
 * A grid rather than rows because it has to fit without scrolling, and it is the
 * only shape that does: seven rows need more height than this screen has, seven
 * tiles need two lines of it. It is also the shape the library already uses, so
 * the page reads as a preview of what the player is about to get.
 */
@Composable
fun ConsoleGrid(
    hidden: Set<Console>,
    onSetVisible: (Console, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    // Read once: a row costs a package query and an icon rasterisation, and the
    // answer cannot change without the player leaving to install something,
    // which recreates this anyway.
    val emulators = remember { allEmulators(context) }

    // How many fit on a line, from the width this grid is actually given.
    //
    // Measured here rather than taken from the screen. The onboarding hands it
    // the full width and gets all seven on the Thor, which is the layout the
    // page was drawn for: one line, nothing to scroll, the whole answer at once.
    // The settings panel is the same screen but some 90 dp narrower once the
    // card and its padding are paid for, and a screen-wide count put seven tiles
    // in that space too: "GameCube" came out as "GameCu" and a version as
    // "v2126.0-va". A tile that has to abbreviate its own console has stopped
    // doing its job.
    //
    // [MIN_TILE] is the width at which a tile still holds the longest console
    // name and the longest version on one line each. Below three columns the
    // grid stops being a grid, so that is the floor.
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val columns = ((maxWidth + GRID_GAP) / (MIN_TILE + GRID_GAP))
            .toInt()
            .coerceIn(3, emulators.size)

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(GRID_GAP)
        ) {
            emulators.chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(GRID_GAP)) {
                    row.forEach { info ->
                        ConsoleTile(
                            info = info,
                            visible = info.console !in hidden,
                            onToggle = { onSetVisible(info.console, info.console in hidden) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    // The last line keeps the tile width of the ones above it.
                    // Without this the leftovers stretch, and a grid whose bottom
                    // row is made of wider tiles reads as a mistake, not a grid.
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/** The narrowest a tile can be and still spell out its console and version. */
private val MIN_TILE = 118.dp

private val GRID_GAP = 8.dp

@Composable
private fun ConsoleTile(
    info: EmulatorInfo,
    visible: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Off is dimmed, not greyed out and not removed: the tile still has to say
    // which console it is, because turning one back on is the other half of the
    // gesture and a blank square gives nothing to aim at.
    val alpha = if (visible) 1f else 0.38f

    Column(
        modifier = modifier
            // Ring before clip, always. After it, the glow is cut to the tile's
            // own shape and fills it with a hard-edged wash instead of spreading
            // outwards, which is the trap the cards already carry a note about.
            .controlRing(TILE_SHAPE)
            .clip(TILE_SHAPE)
            .clickable { onToggle() }
            .background(tilePlate())
            .padding(vertical = 8.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .alpha(alpha)
                .clip(RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (info.icon != null) {
                Image(
                    bitmap = info.icon,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                // The console's own shorthand rather than a question mark: an
                // absent emulator is the ordinary case on a fresh device, and the
                // tile still has to name its machine.
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
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
            maxLines = 1,
            textAlign = TextAlign.Center
        )
        Text(
            // The emulator's own name, on its own line, never translated: it is
            // a product. A tile saying only "Switch" left the page unable to
            // answer the question it exists to answer, which is what to install:
            // the icon says it to whoever already knows the mark, and to nobody
            // else. The console names the games, this names the program.
            info.name,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha * 0.85f),
            maxLines = 1,
            textAlign = TextAlign.Center
        )
        Text(
            // The version only, never "Installed, version x": the sentence does
            // not fit a tile 110 dp wide, and the number is the part worth
            // reading. What is absent says so in words, because an empty line
            // there would read as a version we failed to find.
            info.version?.let { stringResource(R.string.emulators_version_short, shortVersion(it)) }
                ?: if (info.installed) stringResource(R.string.emulators_installed_unknown)
                else stringResource(R.string.emulators_absent_short),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
            maxLines = 1,
            textAlign = TextAlign.Center
        )
        // A bar rather than a switch, and that is a height decision as much as a
        // visual one. A `Switch` under a tile costs some 40 dp, which is what
        // took this page over the edge of a 468 dp screen: the switches came out
        // clipped and the line under the grid was pushed off entirely. The tile
        // is the control anyway, so what is needed here is a state to read, not
        // a second target to hit.
        Box(
            modifier = Modifier
                .padding(top = 2.dp)
                .width(28.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(
                    if (visible) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
                )
        )
    }
}

/** The tile's corner, matching the library's own. */
private val TILE_SHAPE = RoundedCornerShape(16.dp)

/**
 * The version as it fits on a tile.
 *
 * PPSSPP names its builds "v1.20.4", already carrying the letter the label adds,
 * and "vv1.20.4" is what came out on the Thor. Trimming here rather than
 * dropping the prefix from the string: the other five report a bare number, and
 * a column of versions with one of them unmarked reads worse than either.
 */
private fun shortVersion(version: String): String =
    version.removePrefix("v").removePrefix("V")
