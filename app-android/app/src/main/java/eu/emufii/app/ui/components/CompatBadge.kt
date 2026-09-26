package eu.emufii.app.ui.components

import eu.emufii.app.ui.LEGACY_AMBIENT
import eu.emufii.app.ui.LEGACY_SPOT
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import eu.emufii.app.R
import eu.emufii.app.compat.CompatRating
import eu.emufii.app.ui.theme.Coral
import eu.emufii.app.ui.theme.ErrorLight
import eu.emufii.app.ui.theme.GoodLight
import eu.emufii.app.ui.theme.InkText
import eu.emufii.app.ui.theme.InkTextMuted
import eu.emufii.app.ui.theme.Teal
import eu.emufii.app.ui.theme.WarnDark
import eu.emufii.app.ui.theme.WarnLight

/**
 * Fixed colours, never the chosen accent: a verdict is the same fact for every player.
 * pourquoi : docs/decisions/theme-duotone-shelves.md § The compatibility badge is the documented exception to the single accent
 */
@Composable
fun CompatBadge(rating: CompatRating, modifier: Modifier = Modifier) {
    val fill = when (rating) {
        CompatRating.PERFECT -> GreenBead
        CompatRating.PARTIAL -> AmberBead
        CompatRating.BROKEN -> RedBead
        CompatRating.UNTESTED -> SlateBead
    }
    val description = compatLabel(rating)

    Box(
        modifier = modifier
            .semantics { contentDescription = description }
            .shadow(
                3.dp,
                CircleShape,
                ambientColor = Color.Black.copy(alpha = LEGACY_AMBIENT),
                spotColor = Color.Black.copy(alpha = LEGACY_SPOT)
            )
            .clip(CircleShape)
            .background(Brush.verticalGradient(fill))
            // Inside the clip: otherwise the rim is a square around the bead.
            .border(1.5.dp, Color.White, CircleShape)
            .padding(3.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(Modifier.size(15.dp), contentAlignment = Alignment.Center) {
            when (rating) {
                CompatRating.PERFECT -> CheckIcon(size = 13.dp, color = Color.White)
                CompatRating.PARTIAL -> WarnIcon(size = 13.dp, color = Color.White)
                CompatRating.BROKEN -> CrossIcon(size = 12.dp, color = Color.White)
                CompatRating.UNTESTED -> TildeIcon(size = 14.dp, color = Color.White)
            }
        }
    }
}

/**
 * Light cut down to the axis's ink, so the bead reads lit from above with no hex of its
 * own; the glyph stays white, so the top of each pair must carry white.
 * pourquoi : docs/decisions/theme-duotone-shelves.md § Semantics (centralised)
 */
private val GreenBead = listOf(GoodLight, Teal.ink)
private val AmberBead = listOf(WarnLight, WarnDark)
private val RedBead = listOf(ErrorLight, Coral.ink)

private val SlateBead = listOf(InkTextMuted, InkText)

@Composable
fun compatLabel(rating: CompatRating): String = stringResource(
    when (rating) {
        CompatRating.PERFECT -> R.string.compat_perfect
        CompatRating.PARTIAL -> R.string.compat_partial
        CompatRating.BROKEN -> R.string.compat_broken
        CompatRating.UNTESTED -> R.string.compat_untested
    }
)
