package eu.emufii.app.ui.screens.settings

import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.emufii.app.BuildConfig
import eu.emufii.app.R
import eu.emufii.app.crashlogger.CrashLog
import eu.emufii.app.crashlogger.CrashLogger
import eu.emufii.app.ui.components.DetailActions
import eu.emufii.app.ui.components.DetailNote
import eu.emufii.app.ui.components.GhostButton
import eu.emufii.app.ui.components.PadDialog
import eu.emufii.app.ui.components.PadDialogText
import eu.emufii.app.ui.components.PrimaryButton
import eu.emufii.app.ui.components.crashlogs.CrashLogsDetailView
import eu.emufii.app.ui.components.crashlogs.CrashLogsList
import eu.emufii.app.ui.components.crashlogs.EmptyCrashLogsView
import eu.emufii.app.ui.components.padEntry
import kotlinx.coroutines.launch
import androidx.core.net.toUri

/**
 * The crash logger page: a block holding the reports the app has kept since it last
 * started throwing. Follows the settings page shell, so back returns to the hub.
 */
@Composable
internal fun CrashLogsPage(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var crashLogs by remember { mutableStateOf<List<CrashLog>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedCrash by remember { mutableStateOf<CrashLog?>(null) }
    var confirmingClearAll by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        isLoading = true
        crashLogs = CrashLogger.getCrashLogs()
        isLoading = false
    }

    SettingsPage(
        title = stringResource(R.string.settings_page_crash_logs),
        onBack = onBack,
        modifier = modifier
    ) {
        SettingsBlock(title = null) {
            DetailNote(stringResource(R.string.crash_logs_description))

            when {
                isLoading -> Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }

                crashLogs.isEmpty() -> EmptyCrashLogsView()

                else -> CrashLogsList(
                    crashLogs = crashLogs,
                    onCrashClick = { selectedCrash = it }
                )
            }

            DetailActions {
                // The list, when it exists, is the natural first stop: what the player
                // came here to see. Otherwise the report link inherits the pad entry.
                val reportModifier = if (crashLogs.isEmpty()) {
                    Modifier.padEntry().fillMaxWidth()
                } else {
                    Modifier.fillMaxWidth()
                }
                PrimaryButton(
                    label = stringResource(R.string.crash_logs_report_issue),
                    onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(
                                    Intent.ACTION_VIEW,
                                    context.getString(R.string.crash_logs_report_issue_url).toUri()
                                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                    },
                    modifier = reportModifier
                )
                if (crashLogs.isNotEmpty()) {
                    GhostButton(
                        label = stringResource(R.string.crash_logs_clear_all),
                        onClick = { confirmingClearAll = true },
                        tint = dangerInk(),
                        fillWidth = true
                    )
                }
                // Debug builds only: trip the uncaught-exception handler so the whole
                // flow can be verified end-to-end. Never shipped.
                if (BuildConfig.DEBUG) {
                    GhostButton(
                        label = stringResource(R.string.crash_logs_test_crash),
                        onClick = { throw RuntimeException("Test crash from CrashLogsPage") },
                        tint = dangerInk(),
                        fillWidth = true
                    )
                }
            }
        }
    }

    selectedCrash?.let { crash ->
        CrashLogsDetailView(
            crash = crash,
            onDismiss = { selectedCrash = null },
            onDelete = {
                scope.launch {
                    CrashLogger.deleteCrashLog(crash.fileName)
                    crashLogs = CrashLogger.getCrashLogs()
                    selectedCrash = null
                }
            }
        )
    }

    if (confirmingClearAll) {
        PadDialog(
            title = stringResource(R.string.crash_logs_clear_all_dialog_title),
            onDismiss = { confirmingClearAll = false },
            actions = {
                GhostButton(
                    label = stringResource(R.string.friends_cancel),
                    onClick = { confirmingClearAll = false }
                )
                GhostButton(
                    label = stringResource(R.string.crash_logs_clear_all),
                    tint = dangerInk(),
                    onClick = {
                        scope.launch {
                            CrashLogger.clearAllCrashLogs()
                            crashLogs = emptyList()
                            confirmingClearAll = false
                        }
                    }
                )
            }
        ) {
            PadDialogText(
                stringResource(R.string.crash_logs_clear_all_dialog_message, crashLogs.size)
            )
        }
    }
}
