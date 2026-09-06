package eu.emufii.app.ui.components.crashlogs

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.emufii.app.R
import eu.emufii.app.crashlogger.CrashLog
import eu.emufii.app.ui.components.GhostButton
import eu.emufii.app.ui.components.PadDialog
import eu.emufii.app.ui.screens.settings.dangerInk

/**
 * The full report in a scrollable monospace panel. Delete lives here rather than inline
 * on each row so the list keeps one focus target per item and directional traversal on a
 * gamepad never doubles back.
 */
@Composable
fun CrashLogsDetailView(
    crash: CrashLog,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val title = stringResource(R.string.crash_logs_detail_title)

    PadDialog(
        title = title,
        onDismiss = onDismiss,
        modifier = modifier,
        actions = {
            GhostButton(
                label = stringResource(R.string.crash_logs_delete),
                onClick = onDelete,
                tint = dangerInk()
            )
            GhostButton(
                label = stringResource(R.string.crash_logs_detail_close),
                onClick = onDismiss
            )
            GhostButton(
                label = stringResource(R.string.crash_logs_detail_copy),
                onClick = { copyToClipboard(context, title, crash.content) }
            )
        }
    ) {
        Text(
            crash.content,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 320.dp)
                .background(Color.Black.copy(alpha = 0.16f), RoundedCornerShape(8.dp))
                .padding(12.dp)
                .verticalScroll(rememberScrollState())
        )
    }
}

private fun copyToClipboard(context: Context, label: String, content: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, content))
    Toast.makeText(
        context,
        context.getString(R.string.crash_logs_copied_toast),
        Toast.LENGTH_SHORT
    ).show()
}
