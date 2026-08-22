package eu.emufii.app.ui.components

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

/**
 * What Emufii knows about a game, on the game's own tile.
 *
 * A moulded bead, lit from above like everything else on the tray: a vertical
 * gradient from the lighter top of the colour to its deeper bottom, a white rim,
 * and a real drop shadow under it. The rim is not decoration — it is the only
 * reason a small mark stays legible over box art we do not control, and it is
 * the same device the console badge uses, so the two read as one family.
 *
 * **Three marks, one per verdict**, and a game nobody has rated shows nothing.
 * That last distinction is the one that matters: an unrated game and a game
 * known to work must not look alike, or the badge stops being information and
 * becomes decoration. A tick means somebody checked.
 *
 * It was built the other way round first — nothing drawn for a game that works,
 * on the reasoning that a library is mostly working games and badging them all
 * would put a mark on almost every tile. That reasoning holds for the *density*
 * and was wrong about the *meaning*: silence already means "unknown" here, so
 * spending it on "verified" as well left the two indistinguishable.
 *
 * On the colours: `DESIGN.md` keeps the chrome achromatic and reserves the one
 * accent for the cursor. This is the documented exception and it is a narrow
 * one — a mark that appears only on games somebody has actually judged, in three
 * fixed colours, none of which is the accent. Fixed rather than following the
 * chosen accent on purpose: a verdict is the same fact for every player, and a
 * badge that changed colour with a personal setting would be saying something
 * about the setting instead of about the game.
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
            .shadow(3.dp, CircleShape)
            .clip(CircleShape)
            .background(Brush.verticalGradient(fill))
            // Inside the clip, so the rim follows the bead's own edge rather
            // than a square around it.
            .border(1.5.dp, Color.White, CircleShape)
            .padding(3.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(Modifier.size(15.dp), contentAlignment = Alignment.Center) {
            when (rating) {
                CompatRating.PERFECT -> CheckIcon(size = 13.dp, color = Color.White)
                CompatRating.PARTIAL -> WarnIcon(size = 13.dp, color = Color.White)
                // A cross, and not the crossed circle it was: inside a bead that
                // is already a circle, a second outline just thickened the rim
                // and the bar across it read as a scratch. A bare cross against
                // the tick is also the plainer pair — one says yes, one says no,
                // and the triangle between them says "with caveats".
                CompatRating.BROKEN -> CrossIcon(size = 12.dp, color = Color.White)
                CompatRating.UNTESTED -> TildeIcon(size = 14.dp, color = Color.White)
            }
        }
    }
}

/**
 * The three beads, and why they are these exact colours.
 *
 * The glyph inside is white on all three, so the constraint is the *top* of each
 * gradient — its lightest point — which has to carry white on its own. A first
 * pass used the obvious bright green and amber and measured 1.6:1 there:
 * invisible. Each pair is therefore the lightest shade of its hue that still
 * clears 3.2:1 against white, with the bottom a further step down, so the bead
 * reads as lit from above without any part of it losing the mark it carries.
 */
private val GreenBead = listOf(Color(0xFF12A55C), Color(0xFF0C6A3B))
private val AmberBead = listOf(Color(0xFFC78005), Color(0xFF865603))
private val RedBead = listOf(Color(0xFFEB5D47), Color(0xFFD83218))

/**
 * Grey, and the only bead that is not a colour.
 *
 * "Not tried yet" is not a verdict, so it does not get a verdict's voice. A
 * slate bead sits back on the tile where the three coloured ones step forward,
 * which is exactly the weight the fact deserves.
 */
private val SlateBead = listOf(Color(0xFF86909C), Color(0xFF656E7B))

/**
 * The verdict in words, for the places that have room to say it.
 *
 * Shared rather than written twice: the bead reads it out to a screen reader
 * and the launch card prints it, and two copies of this table would drift the
 * day a fifth verdict appears, in the silent direction — a badge whose spoken
 * name no longer matches the line under the title.
 */
@Composable
fun compatLabel(rating: CompatRating): String = stringResource(
    when (rating) {
        CompatRating.PERFECT -> R.string.compat_perfect
        CompatRating.PARTIAL -> R.string.compat_partial
        CompatRating.BROKEN -> R.string.compat_broken
        CompatRating.UNTESTED -> R.string.compat_untested
    }
)
