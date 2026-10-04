package eu.emufii.app.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.emufii.app.R
import eu.emufii.app.library.Console
import eu.emufii.app.library.EmulatorPick
import eu.emufii.app.library.allEmulators
import eu.emufii.app.ui.components.ConsoleRow
import eu.emufii.app.ui.components.DetailNote
import eu.emufii.app.ui.components.DetailTone

@Composable
internal fun ConsolesPage(
    hidden: Set<Console>,
    onSetVisible: (Console, Boolean) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var pickRevision by remember { mutableIntStateOf(0) }
    val emulators = remember(pickRevision) { allEmulators(context) }

    SettingsPage(
        title = stringResource(R.string.settings_page_consoles),
        onBack = onBack,
        trailing = {
            StatePill(
                DetailTone.GOOD,
                stringResource(
                    R.string.settings_pill_ratio,
                    Console.entries.size - hidden.size,
                    Console.entries.size
                )
            )
        },
        modifier = modifier
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            DetailNote(
                stringResource(R.string.consoles_pick_body),
                modifier = Modifier.widthIn(max = 560.dp)
            )

            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val columns = if (maxWidth >= TWO_COLUMN_ROWS) 2 else 1
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    repeat(columns) { side ->
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            emulators.forEachIndexed { index, info ->
                                if (index % columns != side) return@forEachIndexed
                                ConsoleRow(
                                    info = info,
                                    visible = info.console !in hidden,
                                    onSetVisible = { on -> onSetVisible(info.console, on) },
                                    onPickVariant = { pkg ->
                                        EmulatorPick.choose(context, info.console, pkg)
                                        pickRevision++
                                    },
                                    entry = index == 0
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private val TWO_COLUMN_ROWS = 640.dp
