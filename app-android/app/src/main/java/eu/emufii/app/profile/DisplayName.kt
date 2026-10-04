package eu.emufii.app.profile

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import eu.emufii.app.R

/** The default pseudo is a French sentinel that travels verbatim; translate it only for display. */
@Composable
fun playerDisplayName(name: String?): String =
    if (name.isNullOrBlank() || name == Profile.DEFAULT_NAME) {
        stringResource(R.string.profile_default_name)
    } else {
        name
    }
