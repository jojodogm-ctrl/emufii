package eu.emufii.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import eu.emufii.app.artwork.rememberTileArt
import eu.emufii.app.library.Rom
import eu.emufii.app.ui.theme.ArtworkShape
import eu.emufii.app.ui.theme.TileShape

/**
 * The library tile's artwork, at whatever size a card needs it.
 *
 * Same shape, same white plate, same borrowed glow as the tile it came from, so
 * a card reads as the tile that grew rather than as a different screen. The glow
 * is the game's own colour, pulled from its artwork, the chrome stays neutral
 * and the content brings the palette.
 *
 * Lives here rather than inside one screen because three of them show the game
 * they are about: the launch card, the DS online screen, and the PSP public one.
 */
@Composable
fun RomArtwork(rom: Rom, size: Dp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val accent = rom.accentArgb?.let { Color(it) }
    Box(
        modifier = modifier
            .size(size)
            .shadow(
                elevation = if (accent != null) 18.dp else 8.dp,
                shape = TileShape,
                // Ambient stays neutral so the glow reads as light under the
                // artwork rather than as a coloured outline around it.
                ambientColor = Color.Black.copy(alpha = 0.22f),
                spotColor = accent ?: Color.Black.copy(alpha = 0.30f)
            )
            .clip(TileShape)
            .background(tilePlate())
    ) {
        // The same image as the tile, including the one the player chose. This
        // thumbnail read the ROM's icon directly, so a game whose artwork had been
        // replaced from SteamGridDB kept its original icon here: you opened a card
        // that no longer showed the same game as the tile you came from.
        val art by rememberTileArt(rom)
        if (art.model != null) {
            AsyncImage(
                model = ImageRequest.Builder(context).data(art.model).build(),
                contentDescription = null,
                // A remote icon is cropped to fill the thumbnail; the ROM's is
                // left whole, because at 48 px cropping removes a visible part of
                // the drawing.
                contentScale = if (art.isPixelArt) ContentScale.Fit else ContentScale.Crop,
                // Pixel art scales up without smoothing, otherwise it turns to
                // mush; a real image does get smoothed.
                filterQuality = if (art.isPixelArt) FilterQuality.None else FilterQuality.High,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(5.dp)
                    .clip(ArtworkShape)
                    .border(2.dp, artworkRim(), ArtworkShape),
                placeholder = ColorPainter(Color.Transparent),
                error = ColorPainter(Color.Transparent)
            )
        }
    }
}
