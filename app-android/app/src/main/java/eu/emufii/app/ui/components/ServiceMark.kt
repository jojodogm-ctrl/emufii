package eu.emufii.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.emufii.app.R

/**
 * The mark of the service Emufii draws its game icons from.
 *
 * It appears at the only two places where anything is asked of SteamGridDB: the
 * onboarding step and the settings card. A screen that demands a key for a
 * third-party service without showing which one is asking for an act of faith;
 * the logo is what makes the request recognisable, and what lets someone find
 * the right site.
 *
 * Nominative use: the logo serves to name the service, it is neither modified
 * nor recoloured, and nothing here suggests SteamGridDB endorses or supports
 * Emufii. That is also why it keeps its original dark background inside its own
 * pill rather than being cut out to match the theme.
 */
@Composable
fun SteamGridDbMark(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Image(
            painter = painterResource(R.drawable.steamgriddb_logo),
            contentDescription = null,
            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(11.dp))
        )
        Column {
            Text(
                stringResource(R.string.artwork_service_name),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                stringResource(R.string.artwork_service_host),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
