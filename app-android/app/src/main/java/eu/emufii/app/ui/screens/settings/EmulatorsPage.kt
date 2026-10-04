package eu.emufii.app.ui.screens.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import eu.emufii.app.R
import eu.emufii.app.library.Console
import eu.emufii.app.profile.playerDisplayName
import eu.emufii.app.ps2.Ps2Armsx2Folder
import eu.emufii.app.ps2.Ps2NetworkProfile
import eu.emufii.app.psp.PpssppConfigResult
import eu.emufii.app.psp.PpssppConfigStore
import eu.emufii.app.ui.components.DetailActions
import eu.emufii.app.ui.components.DetailNote
import eu.emufii.app.ui.components.DetailTone
import eu.emufii.app.ui.components.GhostButton
import eu.emufii.app.ui.components.PrimaryButton
import eu.emufii.app.ui.components.padEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun EmulatorsPage(
    ppssppConfig: PpssppConfigStore,
    ppssppReady: Boolean,
    onPpssppReadyChanged: (Boolean) -> Unit,
    ps2Ready: Boolean,
    profileName: String,
    onPs2ReadyChanged: (Boolean) -> Unit,
    autofillOn: Boolean,
    onOpenAutofill: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    SettingsPage(
        title = stringResource(R.string.settings_page_emulators),
        onBack = onBack,
        modifier = modifier
    ) {
        SettingsColumns(
            {
                PpssppBlock(
                    store = ppssppConfig,
                    ready = ppssppReady,
                    onReadyChanged = onPpssppReadyChanged,
                )
            },
            {
                Ps2Block(
                    ready = ps2Ready,
                    profileName = profileName,
                    onReadyChanged = onPs2ReadyChanged,
                )
            },
            {
                AutofillBlock(enabled = autofillOn, onOpen = onOpenAutofill)
            },
        )
    }
}

@Composable
internal fun PpssppBlock(
    store: PpssppConfigStore,
    ready: Boolean,
    onReadyChanged: (Boolean) -> Unit,
) {
    val setup = rememberPpssppSetup(store, onReadyChanged)
    val rootUri = setup.rootUri
    val error = setup.error

    SettingsBlock(
        title = stringResource(R.string.settings_row_ppsspp_config),
        mark = { EmulatorMark(Console.PSP) },
        state = BlockState(
            if (ready) DetailTone.GOOD else DetailTone.WARN,
            stringResource(
                if (ready) R.string.settings_pill_ready else R.string.settings_pill_todo
            )
        )
    ) {
        if (!ready) {
            SettingsSteps(
                stringResource(R.string.settings_ppsspp_step1),
                stringResource(R.string.settings_ppsspp_step2),
                stringResource(R.string.settings_ppsspp_step3),
            )
        } else {
            store.rootLabel()?.let {
                BlockFact(stringResource(R.string.settings_library_fact_folder), it)
            }
            DetailNote(stringResource(R.string.settings_ppsspp_caveat))
        }

        if (!ready && rootUri != null) {
            BlockNotice(stringResource(R.string.settings_ppsspp_config_not_ready))
        }
        error?.let { BlockCaveat(stringResource(it)) }

        DetailActions {
            if (ready) {
                GhostButton(
                    label = stringResource(R.string.settings_ppsspp_config_change),
                    onClick = setup::pick,
                    fillWidth = true,
                    modifier = Modifier.padEntry(),
                )
            } else {
                PrimaryButton(
                    label = stringResource(R.string.settings_ppsspp_config_choose),
                    onClick = setup::pick,
                    modifier = Modifier.padEntry().fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
internal fun Ps2Block(
    ready: Boolean,
    profileName: String,
    onReadyChanged: (Boolean) -> Unit,
) {
    val setup = rememberPs2Setup(profileName, onReadyChanged)
    val rootUri = setup.rootUri
    val busy = setup.busy
    val error = setup.error

    val current = setup.receipt
    SettingsBlock(
        title = stringResource(R.string.settings_row_ps2_profile),
        mark = { EmulatorMark(Console.PS2) },
        state = BlockState(
            when {
                busy -> DetailTone.BUSY
                error != null -> DetailTone.BAD
                ready -> DetailTone.GOOD
                else -> DetailTone.WARN
            },
            stringResource(
                when {
                    busy -> R.string.settings_pill_working
                    error != null -> R.string.settings_pill_failed
                    ready -> R.string.settings_pill_ready
                    else -> R.string.settings_pill_todo
                }
            )
        )
    ) {
        if (!ready) {
            SettingsSteps(
                stringResource(R.string.settings_ps2_step1),
                stringResource(R.string.settings_ps2_step2),
                stringResource(R.string.settings_ps2_step3),
            )
        }

        if (current != null) {
            BlockFact(
                stringResource(R.string.settings_ps2_fact_card),
                current.cardName
            )
            BlockFact(
                stringResource(R.string.settings_ps2_fact_source),
                current.sourceCardName ?: stringResource(R.string.settings_ps2_profile_new_card)
            )
            BlockFact(
                stringResource(R.string.settings_ps2_fact_bios),
                current.biosName ?: stringResource(R.string.settings_ps2_profile_default_bios)
            )
            BlockFact(
                stringResource(R.string.settings_ps2_fact_console),
                current.consoleIdHex
            )
        }

        val folderCardNote = current?.folderCardName?.let { name ->
            when {
                current.savesLeftBehind > 0 -> stringResource(
                    R.string.settings_ps2_profile_folder_partial,
                    name,
                    current.importedSaveCount,
                    current.savesLeftBehind,
                )
                current.importedSaveCount > 0 -> stringResource(
                    R.string.settings_ps2_profile_folder_imported,
                    name,
                    current.importedSaveCount,
                )
                else -> stringResource(R.string.settings_ps2_profile_folder_empty, name)
            }
        }
        error?.let { BlockCaveat(it) }
        val notice = when {
            error != null -> null
            folderCardNote != null -> folderCardNote
            current != null && current.gameOverrideCount > 0 -> pluralStringResource(
                R.plurals.settings_ps2_profile_overrides,
                current.gameOverrideCount,
                current.gameOverrideCount,
            )
            else -> null
        }
        notice?.let { BlockNotice(it) }

        DetailActions {
            if (ready) {
                GhostButton(
                    label = stringResource(R.string.hint_ps2_profile_redo),
                    onClick = setup::prepare,
                    fillWidth = true
                )
            } else {
                PrimaryButton(
                    label = stringResource(
                        if (rootUri == null) R.string.hint_ps2_profile_choose_folder
                        else R.string.hint_ps2_profile_button
                    ),
                    onClick = setup::prepare,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (rootUri != null) {
                GhostButton(
                    label = stringResource(R.string.hint_ps2_profile_change_folder),
                    onClick = setup::changeFolder,
                    fillWidth = true,
                )
            }
        }
    }
}

@Composable
internal fun AutofillBlock(enabled: Boolean, onOpen: () -> Unit) {
    SettingsBlock(
        title = stringResource(R.string.settings_row_autofill),
        mark = { EmulatorMark(Console.THREE_DS) },
        state = BlockState(
            if (enabled) DetailTone.GOOD else DetailTone.WARN,
            stringResource(
                if (enabled) R.string.settings_value_autofill_on
                else R.string.settings_value_autofill_off
            )
        )
    ) {
        DetailNote(stringResource(R.string.settings_autofill_note))
        if (!enabled) BlockNotice(stringResource(R.string.settings_autofill_off))

        DetailActions {
            // Filled while it is off: an update can withdraw the permission, leaving this
            // the only way back to automatic setup.
            if (enabled) {
                GhostButton(
                    label = stringResource(R.string.settings_autofill_open),
                    onClick = onOpen,
                    fillWidth = true
                )
            } else {
                PrimaryButton(
                    label = stringResource(R.string.settings_autofill_open),
                    onClick = onOpen,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
