package eu.emufii.app.ui.components.crashlogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import eu.emufii.app.R
import eu.emufii.app.crashlogger.CrashLog
import eu.emufii.app.ui.components.BugMark

/**
 * A stacked column of reports. Not lazy: CrashLogger caps at ten items, and lazy would
 * only cost focus traversal predictability for no gain.
 */
@Composable
fun CrashLogsList(
    crashLogs: List<CrashLog>,
    onCrashClick: (CrashLog) -> Unit,
    modifier: Modifier = Modifier,
    /** True to make the first row the page's pad entry. Off when the caller carries it. */
    firstRowIsEntry: Boolean = true
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        crashLogs.forEachIndexed { index, crash ->
            CrashLogsListItem(
                crash = crash,
                onClick = { onCrashClick(crash) },
                entry = firstRowIsEntry && index == 0
            )
        }
    }
}

@Composable
fun EmptyCrashLogsView(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxWidth().padding(vertical = 48.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            BugMark(
                size = 48.dp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
            Text(
                stringResource(R.string.crash_logs_empty_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Text(
                stringResource(R.string.crash_logs_empty_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
