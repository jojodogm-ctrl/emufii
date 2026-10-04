package eu.emufii.app.artwork

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import eu.emufii.app.library.Rom
import eu.emufii.app.settings.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** [embedded] is pixel art scaled without smoothing; [remote] is smoothed. */
data class TileArt(
    val remote: String?,
    val embedded: java.io.File?,
    val fromFrontend: Boolean = false,
) {
    val isPixelArt: Boolean get() = remote == null
    val model: Any? get() = remote ?: embedded

    val fitsWhole: Boolean get() = isPixelArt || fromFrontend
}

@Composable
fun rememberTileArt(rom: Rom): State<TileArt> {
    val context = LocalContext.current
    val store = remember(context) { ArtworkStore(context.applicationContext) }
    val settings = remember(context) { SettingsStore.get(context) }
    val apiKey by settings.steamGridDbKey.collectAsStateWithLifecycle()
    val folder by settings.frontendFolder.collectAsStateWithLifecycle()
    val frontend by settings.artworkFrontend.collectAsStateWithLifecycle()
    val revision by ArtworkStore.revision.collectAsStateWithLifecycle()
    val state = remember(rom.uri) {
        mutableStateOf(resolvedArt[rom.uri] ?: TileArt(null, rom.iconFile))
    }

    LaunchedEffect(rom.uri, apiKey, folder, frontend, revision) {
        val remote: String = run {
            if (store.chosenFor(rom) != null) return@run store.iconUrl(rom, apiKey)
            val local = withContext(Dispatchers.IO) {
                runCatching {
                    FrontendMedia.uriFor(
                        context,
                        frontend,
                        folder.takeIf { it.isNotBlank() }?.toUri(),
                        rom,
                        FrontendMedia.Kind.ICON
                    )
                }.getOrNull()
            }
            local?.toString() ?: store.iconUrl(rom, apiKey)
        } ?: return@LaunchedEffect

        val fromFrontend = !remote.startsWith("http")
        val art = TileArt(remote, rom.iconFile, fromFrontend)
        resolvedArt[rom.uri] = art
        state.value = art
    }
    return state
}

private val resolvedArt = java.util.concurrent.ConcurrentHashMap<android.net.Uri, TileArt>()
