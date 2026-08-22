package eu.emufii.app.profile

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import eu.emufii.app.R

/**
 * A player's pseudo as it should be *read*, rather than as it is stored.
 *
 * Pseudos travel over the wire verbatim, so a player who never picked one
 * arrives as [Profile.DEFAULT_NAME], a French word, on everyone's screen,
 * whatever language they run the app in. Substituting here rather than at the
 * source keeps the sentinel stable (it is persisted, and [Profile.isNamed]
 * compares against it) while letting each side read the placeholder in its own
 * language.
 */
@Composable
fun playerDisplayName(name: String?): String =
    if (name.isNullOrBlank() || name == Profile.DEFAULT_NAME) {
        stringResource(R.string.profile_default_name)
    } else {
        name
    }
