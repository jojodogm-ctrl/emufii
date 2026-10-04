package eu.emufii.app.ui.screens.settings

import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import eu.emufii.app.ui.components.SwitchRow
import eu.emufii.app.ui.controlRing
import eu.emufii.app.ui.components.cardSliceFill
import eu.emufii.app.ui.theme.LocalEmufiiDarkTheme
import eu.emufii.app.ui.theme.socket
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.emufii.app.R
import eu.emufii.app.artwork.ArtworkFrontend
import eu.emufii.app.artwork.FrontendMedia
import eu.emufii.app.library.Rom
import eu.emufii.app.settings.SettingsStore
import eu.emufii.app.ui.components.ChevronRight
import eu.emufii.app.ui.components.DetailActions
import eu.emufii.app.ui.components.DetailNote
import eu.emufii.app.ui.components.DetailTone
import eu.emufii.app.ui.components.GhostButton
import eu.emufii.app.ui.components.PadTextField
import eu.emufii.app.ui.components.PrimaryButton
import eu.emufii.app.ui.components.FolderMark
import eu.emufii.app.ui.components.SteamGridDbMark
import eu.emufii.app.ui.components.padEntry
import eu.emufii.app.ui.tap

@Composable
internal fun LibraryPage(
    folder: String?,
    secondFolder: String?,
    scanning: Boolean,
    count: Int?,
    onPickFolder: () -> Unit,
    onPickSecondFolder: () -> Unit,
    onRemoveSecondFolder: () -> Unit,
    onRescan: () -> Unit,
    artworkKey: String,
    onArtworkKeyChange: (String) -> Unit,
    artworkSample: List<Rom>,
    hiddenCount: Int,
    onRestoreHidden: () -> Unit,
    hideIncompatible: Boolean,
    incompatibleCount: Int,
    onSetHideIncompatible: (Boolean) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    SettingsPage(
        title = stringResource(R.string.settings_page_library),
        onBack = onBack,
        modifier = modifier
    ) {
        val head = rememberBlockHeights()
        SettingsColumns(
            {
                FoldersBlock(
                    modifier = Modifier.sameHeightAs(head),
                    folder = folder,
                    secondFolder = secondFolder,
                    scanning = scanning,
                    count = count,
                    onPickFolder = onPickFolder,
                    onPickSecondFolder = onPickSecondFolder,
                    onRemoveSecondFolder = onRemoveSecondFolder,
                    onRescan = onRescan
                )
            },
            {
                ArtworkBlock(
                    modifier = Modifier.sameHeightAs(head),
                    sample = artworkSample,
                    onSourceChanged = onRescan
                )
            },
            {
                HiddenRomsBlock(count = hiddenCount, onRestore = onRestoreHidden)
            },
            {
                FallbackBlock(key = artworkKey, onKeyChange = onArtworkKeyChange)
            },
            {
                IncompatibleBlock(
                    enabled = hideIncompatible,
                    count = incompatibleCount,
                    onSetEnabled = onSetHideIncompatible
                )
            },
        )
    }
}

@Composable
private fun FoldersBlock(
    modifier: Modifier = Modifier,
    folder: String?,
    secondFolder: String?,
    scanning: Boolean,
    count: Int?,
    onPickFolder: () -> Unit,
    onPickSecondFolder: () -> Unit,
    onRemoveSecondFolder: () -> Unit,
    onRescan: () -> Unit
) {
    SettingsBlock(
        modifier = modifier,
        spread = true,
        title = stringResource(R.string.settings_row_folder),
        state = BlockState(
            when {
                scanning -> DetailTone.BUSY
                folder == null -> DetailTone.WARN
                else -> DetailTone.GOOD
            },
            when {
                scanning -> stringResource(R.string.settings_pill_scanning)
                folder == null -> stringResource(R.string.settings_pill_no_folder)
                count != null -> pluralStringResource(R.plurals.settings_pill_games, count, count)
                else -> stringResource(R.string.settings_pill_ready)
            }
        ),
        footer = {
            DetailActions {
                if (folder == null) {
                    PrimaryButton(
                        label = stringResource(R.string.lib_choose_folder),
                        onClick = onPickFolder,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GhostButton(
                            label = stringResource(R.string.settings_library_rescan),
                            onClick = onRescan,
                            fillWidth = true,
                            modifier = Modifier.weight(1f)
                        )
                        if (secondFolder != null) {
                            GhostButton(
                                label = stringResource(R.string.settings_library_remove_second),
                                onClick = onRemoveSecondFolder,
                                fillWidth = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    ) {
        FolderSlot(
            name = folder ?: stringResource(R.string.lib_no_folder_title),
            note = if (folder == null) stringResource(R.string.settings_library_note)
            else stringResource(R.string.settings_library_subfolders),
            onClick = onPickFolder,
            entry = true,
            bandFraction = 0.095f
        )

        if (folder != null) {
            if (secondFolder != null) {
                FolderSlot(
                    name = secondFolder,
                    note = stringResource(R.string.settings_library_second_note),
                    onClick = onPickSecondFolder
                )
            } else {
                EmptyFolderSlot(
                    label = stringResource(R.string.settings_library_add_second),
                    onClick = onPickSecondFolder
                )
            }
        }

        DetailNote(stringResource(R.string.settings_library_folders_body))
    }
}

@Composable
private fun FolderSlot(
    name: String,
    note: String,
    onClick: () -> Unit,
    entry: Boolean = false,
    bandFraction: Float = 0.12f
) {
    val dark = LocalEmufiiDarkTheme.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .then(if (entry) Modifier.padEntry() else Modifier)
            .controlRing(ROW_SHAPE, bandFraction = bandFraction)
            .socket(ROW_SHAPE, dark)
            .clip(ROW_SHAPE)
            .tap(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        FolderMark(size = 26.dp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                name,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                note,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        ChevronRight(
            size = 18.dp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
    }
}

@Composable
private fun EmptyFolderSlot(label: String, onClick: () -> Unit) {
    val outline = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .controlRing(ROW_SHAPE)
            .cardSliceFill(ROW_SHAPE)
            .drawBehind {
                val stroke = 1.5.dp.toPx()
                drawRoundRect(
                    color = outline,
                    topLeft = Offset(stroke / 2, stroke / 2),
                    size = Size(size.width - stroke, size.height - stroke),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(14.dp.toPx()),
                    style = Stroke(
                        width = stroke,
                        pathEffect = PathEffect.dashPathEffect(
                            floatArrayOf(6.dp.toPx(), 6.dp.toPx())
                        )
                    )
                )
            }
            .clip(ROW_SHAPE)
            .tap(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "+",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ArtworkBlock(
    modifier: Modifier = Modifier,
    sample: List<Rom>,
    onSourceChanged: () -> Unit,
) {
    val context = LocalContext.current
    val settingsStore = remember(context) { SettingsStore.get(context) }
    val folder by settingsStore.frontendFolder.collectAsStateWithLifecycle()
    val frontend by settingsStore.artworkFrontend.collectAsStateWithLifecycle()
    val linked = folder.isNotBlank()

    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            val granted = runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                true
            }.getOrDefault(false)
            if (granted) {
                settingsStore.setFrontendFolder(uri.toString())
                FrontendMedia.forget()
                onSourceChanged()
            }
        }
    }
    val pick = { folderPicker.launch(defaultFolderOf(frontend)) }

    SettingsBlock(
        modifier = modifier,
        spread = true,
        title = stringResource(R.string.settings_row_artwork),
        state = BlockState(
            if (linked) DetailTone.GOOD else DetailTone.WARN,
            if (linked) stringResource(frontend.labelRes)
            else stringResource(R.string.settings_artwork_source_none)
        ),
        footer = {
            DetailActions {
                if (!linked) {
                    PrimaryButton(
                        label = stringResource(
                            R.string.settings_frontend_choose,
                            stringResource(frontend.labelRes)
                        ),
                        onClick = pick,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GhostButton(
                            label = stringResource(R.string.settings_cocoon_change),
                            onClick = pick,
                            fillWidth = true,
                            modifier = Modifier.weight(1f)
                        )
                        GhostButton(
                            label = stringResource(R.string.settings_cocoon_forget),
                            onClick = {
                                settingsStore.setFrontendFolder("")
                                FrontendMedia.forget()
                                onSourceChanged()
                            },
                            fillWidth = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    ) {
        ArtworkStrip(sample)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ArtworkFrontend.entries.forEachIndexed { index, option ->
                ChoiceRow(
                    label = stringResource(option.labelRes),
                    selected = option == frontend,
                    onClick = {
                        if (option != frontend) {
                            settingsStore.setArtworkFrontend(option)
                            // The grant is layout-specific: a Cocoon root read as ES-DE finds nothing.
                            settingsStore.setFrontendFolder("")
                            FrontendMedia.forget()
                            onSourceChanged()
                        }
                    },
                    entry = index == 0
                )
            }
        }
        DetailNote(stringResource(R.string.settings_frontend_body))
    }
}

@Composable
private fun FallbackBlock(key: String, onKeyChange: (String) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    SettingsBlock(
        title = stringResource(R.string.settings_row_fallback),
        state = BlockState(
            if (key.isNotBlank()) DetailTone.GOOD else DetailTone.WARN,
            stringResource(
                if (key.isNotBlank()) R.string.settings_artwork_fallback_on
                else R.string.settings_artwork_fallback_off
            )
        ),
        onToggleExpanded = { expanded = !expanded },
        expanded = expanded,
        bandFraction = if (expanded) 0.032f else 0.09f
    ) {
        DetailNote(stringResource(R.string.settings_artwork_body))
        if (expanded) {
            SteamGridDbMark()
            PadTextField(
                value = key,
                onValueChange = onKeyChange,
                label = stringResource(R.string.settings_artwork_field),
                modifier = Modifier.fillMaxWidth()
            )
            DetailNote(stringResource(R.string.settings_artwork_where))
        }
    }
}

@Composable
private fun HiddenRomsBlock(count: Int, onRestore: () -> Unit) {
    SettingsBlock(
        title = stringResource(R.string.settings_row_hidden),
        state = BlockState(
            if (count == 0) DetailTone.GOOD else DetailTone.BUSY,
            if (count == 0) stringResource(R.string.settings_pill_none)
            else pluralStringResource(R.plurals.settings_pill_hidden, count, count)
        )
    ) {
        DetailNote(stringResource(R.string.settings_hidden_body))
        if (count > 0) {
            GhostButton(
                label = stringResource(R.string.settings_hidden_restore),
                onClick = onRestore,
                fillWidth = true
            )
        }
    }
}

@Composable
private fun IncompatibleBlock(enabled: Boolean, count: Int, onSetEnabled: (Boolean) -> Unit) {
    SettingsBlock(
        title = stringResource(R.string.settings_incompatible_title),
        state = BlockState(
            if (enabled) DetailTone.GOOD else DetailTone.BUSY,
            stringResource(
                if (enabled) R.string.settings_value_autofill_on
                else R.string.settings_value_autofill_off
            )
        )
    ) {
        SwitchRow(
            label = stringResource(R.string.settings_incompatible_switch),
            checked = enabled,
            onCheckedChange = onSetEnabled,
            note = pluralStringResource(R.plurals.settings_incompatible_count, count, count)
        )
        DetailNote(stringResource(R.string.settings_incompatible_note))
    }
}

private fun defaultFolderOf(frontend: ArtworkFrontend): Uri = DocumentsContract.buildDocumentUri(
    "com.android.externalstorage.documents",
    frontend.defaultFolderId
)
