package eu.emufii.app.artwork

import android.content.Context
import androidx.core.net.toUri
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.size.Size
import eu.emufii.app.library.Rom
import eu.emufii.app.settings.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/**
 * What has to be done for the grid to open complete. None of it is essential: every
 * step is wrapped, and a warm-up that stopped you getting in would be worse than none.
 * pourquoi : docs/decisions/jaquettes.md § The grid opens complete, or it fills in under the player's eyes
 */
object ArtworkPreload {

    /**
     * How many covers are decoded ahead. Two screens of grid: what the player sees on
     * arrival, plus the row the first movement uncovers. Beyond that we would fill
     * memory with images nobody looks at.
     */
    private const val DECODED_AHEAD = 24

    private const val TILE_PX = 360

    suspend fun warm(context: Context, roms: List<Rom>) = withContext(Dispatchers.IO) {
        if (roms.isEmpty()) return@withContext
        val app = context.applicationContext
        val settings = SettingsStore.get(app)
        val apiKey = settings.steamGridDbKey.value
        val folder = settings.frontendFolder.value.takeIf { it.isNotBlank() }?.toUri()
        val frontend = settings.artworkFrontend.value
        val store = ArtworkStore(app)

        // Every address: this is where the folder indexes are built, once per console
        // rather than once per console's first tile.
        val models = roms.map { rom ->
            runCatching {
                val local = if (store.chosenFor(rom) == null) {
                    FrontendMedia.uriFor(app, frontend, folder, rom, FrontendMedia.Kind.ICON)
                } else {
                    null
                }
                local?.toString() ?: store.iconUrl(rom, apiKey) ?: rom.iconFile
            }.getOrNull()
        }

        // Decoding, at the tile's size rather than `ORIGINAL`: the loader keeps what it
        // decoded, and one full-resolution cover per game would fill memory with
        // bitmaps the tile shrinks anyway. The single loader, never a new one.
        val loader = SingletonImageLoader.get(app)
        coroutineScope {
            models.take(DECODED_AHEAD).map { model ->
                async {
                    if (model == null) return@async
                    runCatching {
                        loader.execute(
                            ImageRequest.Builder(app)
                                .data(model)
                                .size(Size(TILE_PX, TILE_PX))
                                .build()
                        )
                    }
                }
            }.awaitAll()
        }
    }
}
