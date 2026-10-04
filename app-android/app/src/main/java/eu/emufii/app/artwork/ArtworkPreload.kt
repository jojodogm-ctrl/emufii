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

object ArtworkPreload {

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

        // Decode at tile size, not ORIGINAL: the loader keeps what it decoded.
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
