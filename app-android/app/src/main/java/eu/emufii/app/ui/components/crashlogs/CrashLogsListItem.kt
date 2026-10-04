package eu.emufii.app.ui.components.crashlogs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.emufii.app.crashlogger.CrashLog
import eu.emufii.app.ui.components.SoftCard
import eu.emufii.app.ui.components.padEntry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CrashLogsListItem(
    crash: CrashLog,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    entry: Boolean = false
) {
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US) }

    SoftCard(
        onClick = onClick,
        modifier = modifier.then(if (entry) Modifier.padEntry() else Modifier)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                dateFormat.format(Date(crash.timestamp)),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                crash.preview,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}
