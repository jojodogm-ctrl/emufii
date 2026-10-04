package eu.emufii.app.ui.screens.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import eu.emufii.app.R
import eu.emufii.app.profile.playerDisplayName
import eu.emufii.app.ps2.Ps2Armsx2Folder
import eu.emufii.app.ps2.Ps2NetworkProfile
import eu.emufii.app.psp.PpssppConfigResult
import eu.emufii.app.psp.PpssppConfigStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Stable
internal class PpssppSetup {
    var rootUri by mutableStateOf<Uri?>(null)
    var error by mutableStateOf<Int?>(null)
    internal var launch: (Uri?) -> Unit = {}
    fun pick() = launch(rootUri)
}

@Composable
internal fun rememberPpssppSetup(
    store: PpssppConfigStore,
    onReadyChanged: (Boolean) -> Unit,
): PpssppSetup {
    val setup = remember(store) { PpssppSetup().apply { rootUri = store.rootUri() } }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            setup.error = when (store.configureRoot(uri)) {
                PpssppConfigResult.Success -> {
                    setup.rootUri = uri
                    onReadyChanged(true)
                    null
                }
                PpssppConfigResult.PermissionMissing -> R.string.ppsspp_config_permission_missing
                PpssppConfigResult.InvalidRoot -> R.string.ppsspp_config_invalid_root
                PpssppConfigResult.ActiveOverrides -> R.string.ppsspp_config_active_overrides
                PpssppConfigResult.NotConfigured -> R.string.ppsspp_config_not_configured
                PpssppConfigResult.UnknownDiscId -> R.string.ppsspp_config_unknown_game
                is PpssppConfigResult.Failure -> R.string.ppsspp_config_write_failed
            }
            if (setup.error != null) onReadyChanged(store.isReady())
        }
    }
    setup.launch = { picker.launch(it) }
    return setup
}

@Stable
internal class Ps2Setup {
    var rootUri by mutableStateOf<Uri?>(null)
    var receipt by mutableStateOf<Ps2NetworkProfile.Receipt?>(null)
    var busy by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    internal var prepareAt: (Uri) -> Unit = {}
    internal var launch: (Uri?) -> Unit = {}

    fun prepare() {
        val uri = rootUri
        if (uri == null) launch(null) else prepareAt(uri)
    }

    fun changeFolder() = launch(rootUri)
}

@Composable
internal fun rememberPs2Setup(
    profileName: String,
    onReadyChanged: (Boolean) -> Unit,
): Ps2Setup {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val setup = remember {
        Ps2Setup().apply {
            rootUri = Ps2NetworkProfile.rootUri(context)
            receipt = Ps2NetworkProfile.receipt(context)
        }
    }
    val saveTitle = playerDisplayName(profileName)

    setup.prepareAt = { uri ->
        Ps2NetworkProfile.clearReady(context)
        onReadyChanged(false)
        setup.busy = true
        setup.error = null
        scope.launch {
            when (val outcome = withContext(Dispatchers.IO) {
                Ps2Armsx2Folder.prepare(context, uri, saveTitle)
            }) {
                is Ps2Armsx2Folder.Outcome.Success -> {
                    Ps2NetworkProfile.recordPrepared(context, outcome.prepared)
                    setup.receipt = Ps2NetworkProfile.receipt(context)
                    onReadyChanged(true)
                }
                Ps2Armsx2Folder.Outcome.NotArmsx2Folder ->
                    setup.error = context.getString(R.string.settings_ps2_profile_bad_folder)
                Ps2Armsx2Folder.Outcome.MissingWritePermission ->
                    setup.error = context.getString(R.string.settings_ps2_profile_no_write)
                is Ps2Armsx2Folder.Outcome.InvalidMemoryCard ->
                    setup.error = context.getString(R.string.settings_ps2_profile_invalid_card, outcome.name)
                is Ps2Armsx2Folder.Outcome.SourceChanged ->
                    setup.error = context.getString(R.string.settings_ps2_profile_source_changed, outcome.name)
                is Ps2Armsx2Folder.Outcome.AmbiguousBios ->
                    setup.error = context.getString(
                        R.string.settings_ps2_profile_ambiguous_bios,
                        outcome.candidates.joinToString(", "),
                    )
                is Ps2Armsx2Folder.Outcome.BiosUnavailable ->
                    setup.error = context.getString(R.string.settings_ps2_profile_bios_unavailable, outcome.name)
                is Ps2Armsx2Folder.Outcome.BiosUnreadable ->
                    setup.error = context.getString(R.string.settings_ps2_profile_bios_unreadable, outcome.name)
                is Ps2Armsx2Folder.Outcome.WriteFailed -> setup.error = outcome.detail
            }
            setup.busy = false
        }
    }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            val granted = runCatching {
                context.contentResolver.takePersistableUriPermission(uri, flags)
                Ps2NetworkProfile.setRootUri(context, uri)
            }.getOrDefault(false)
            if (granted) {
                setup.rootUri = uri
                setup.receipt = null
                setup.prepareAt(uri)
            } else setup.error = context.getString(R.string.settings_ps2_profile_no_write)
        }
    }
    setup.launch = { folderPicker.launch(it) }
    return setup
}
