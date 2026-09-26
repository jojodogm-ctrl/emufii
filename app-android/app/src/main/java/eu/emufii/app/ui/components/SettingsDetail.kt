package eu.emufii.app.ui.components

import eu.emufii.app.ui.LEGACY_AMBIENT
import eu.emufii.app.ui.LEGACY_SPOT
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import eu.emufii.app.ui.theme.LocalEmufiiDarkTheme
import eu.emufii.app.ui.theme.socket
import eu.emufii.app.ui.theme.GoodLight
import eu.emufii.app.ui.theme.GoodDark
import eu.emufii.app.ui.theme.InfoLight
import eu.emufii.app.ui.theme.InfoDark
import eu.emufii.app.ui.theme.WarnLight
import eu.emufii.app.ui.theme.WarnDark
import eu.emufii.app.ui.theme.ErrorLight
import eu.emufii.app.ui.theme.ErrorDark

/**
 * The inside of an unfolded settings row: three things and nothing else, [DetailNote],
 * [DetailActions], [DetailStatus].
 * pourquoi : docs/decisions/coquille-ecrans.md § An expanded row is made of three things, and nothing else
 */
@Composable
fun DetailNote(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
    )
}

/**
 * Nothing here decides what a button looks like: the caller passes a filled
 * [PrimaryButton] first and [GhostButton]s after. This owns only the gap between them.
 */
@Composable
fun DetailActions(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) { content() }
}

/** Four, matching the beads the library already uses. */
enum class DetailTone { GOOD, BUSY, WARN, BAD }

data class DetailFact(val label: String, val value: String)

/**
 * [facts] are the identifiers behind the headline, a filename, a BIOS, a console id: they
 * are looked *up*, not read, so they go in a column of aligned rows. [caveat] is the one
 * thing that can be wrong while the state is still good, so it qualifies rather than
 * replaces, and sits at the bottom in the error colour.
 */
@Composable
fun DetailStatus(
    tone: DetailTone,
    headline: String,
    modifier: Modifier = Modifier,
    facts: List<DetailFact> = emptyList(),
    caveat: String? = null,
) {
    val dark = LocalEmufiiDarkTheme.current
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .socket(shape, dark)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            // Top, not centre: a translation can push a headline onto two lines, and a bead
            // floating mid-sentence stops reading as its mark.
            verticalAlignment = Alignment.Top
        ) {
            StateBead(tone)
            Text(
                headline,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        if (facts.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                for (fact in facts) FactRow(fact)
            }
        }
        caveat?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

/** 84 dp holds the longest label the app has in either language without wrapping it. */
@Composable
private fun FactRow(fact: DetailFact) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            fact.label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(84.dp)
        )
        Text(
            fact.value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
    }
}

/** Deliberately the shape, lighting and glyphs of the compatibility bead on a game tile. */
@Composable
fun StateBead(tone: DetailTone, size: Dp = 14.dp) {
    val dark = LocalEmufiiDarkTheme.current
    val fill = when (tone) {
        DetailTone.GOOD -> if (dark) GoodDark else GoodLight
        DetailTone.BUSY -> if (dark) InfoDark else InfoLight
        DetailTone.WARN -> if (dark) WarnDark else WarnLight
        DetailTone.BAD -> if (dark) ErrorDark else ErrorLight
    }
    Box(
        modifier = Modifier
            .shadow(
                3.dp,
                CircleShape,
                ambientColor = Color.Black.copy(alpha = LEGACY_AMBIENT),
                spotColor = Color.Black.copy(alpha = LEGACY_SPOT)
            )
            .clip(CircleShape)
            .background(fill)
            .border(1.5.dp, Color.White, CircleShape)
            .padding(3.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(Modifier.size(size), contentAlignment = Alignment.Center) {
            when (tone) {
                DetailTone.GOOD -> CheckIcon(size = size * (12f / 14f), color = Color.White)
                DetailTone.BUSY -> TildeIcon(size = size * (13f / 14f), color = Color.White)
                DetailTone.WARN -> WarnIcon(size = size * (12f / 14f), color = Color.White)
                DetailTone.BAD -> CrossIcon(size = size * (11f / 14f), color = Color.White)
            }
        }
    }
}
