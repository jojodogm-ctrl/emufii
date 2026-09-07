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

/**
 * The two are not drawn the same way: [embedded] is 48 px of pixel art, scaled up
 * without smoothing on pain of mush, [remote] a real image that is smoothed instead.
 */
data class TileArt(
    val remote: String?,
    val embedded: java.io.File?,
    val fromFrontend: Boolean = false,
) {
    val isPixelArt: Boolean get() = remote == null
    val model: Any? get() = remote ?: embedded

    /**
     * Whole-image display: pixel-art icons lose visible parts under a crop, and the
     * artwork a frontend hands us — an ES-DE box front or a Cocoon key art the player
     * re-cropped — is already framed the way it should be shown.
     */
    val fitsWhole: Boolean get() = isPixelArt || fromFrontend
}

/**
 * Starts with what the ROM carries, so the grid is painted immediately: waiting on the
 * network for a library already on disk would show holes at startup. The search then sets
 * off, and the tile repaints if it succeeds.
 */
@Composable
fun rememberTileArt(rom: Rom): State<TileArt> {
    val context = LocalContext.current
    val store = remember(context) { ArtworkStore(context.applicationContext) }
    val settings = remember(context) { SettingsStore.get(context) }
    val apiKey by settings.steamGridDbKey.collectAsStateWithLifecycle()
    val folder by settings.frontendFolder.collectAsStateWithLifecycle()
    val frontend by settings.artworkFrontend.collectAsStateWithLifecycle()
    val revision by ArtworkStore.revision.collectAsStateWithLifecycle()
    val state = remember(rom.uri) { mutableStateOf(TileArt(null, rom.iconFile)) }

    LaunchedEffect(rom.uri, apiKey, folder, frontend, revision) {
        // The frontend comes before the catalogue, and the player's own choice before the
        // frontend, which `iconUrl` already honours: the frontend's artwork sits on the
        // device, was downloaded for this exact file, and in places was re-cropped by hand.
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

        // Anything not on `http(s)://` came off the device — either the frontend
        // lookup or a manual pick against a local tree — and gets whole-image display.
        // SteamGridDB choices stay cropped.
        val fromFrontend = !remote.startsWith("http")
        state.value = TileArt(remote, rom.iconFile, fromFrontend)
    }
    return state
}
