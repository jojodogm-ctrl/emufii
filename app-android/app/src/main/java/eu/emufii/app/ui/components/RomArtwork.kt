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
import eu.emufii.app.ui.theme.InkText
import eu.emufii.app.ui.theme.TileShape

/**
 * Same shape, plate and borrowed glow as the tile it came from, so a card reads as the
 * tile that grew; the glow is the game's own colour, pulled from its artwork. Shared
 * because three screens show the game they are about: the launch card, DS online, PSP.
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
                // Ambient stays neutral so the glow reads as light under the artwork
                // rather than as a coloured outline around it.
                ambientColor = InkText.copy(alpha = 0.22f),
                spotColor = accent ?: InkText.copy(alpha = 0.30f)
            )
            .clip(TileShape)
            .background(tilePlate())
    ) {
        // Reading the ROM's icon directly kept the original art here for a game replaced
        // from SteamGridDB: the card no longer showed the tile you came from.
        val art by rememberTileArt(rom)
        if (art.model != null) {
            AsyncImage(
                model = ImageRequest.Builder(context).data(art.model).build(),
                contentDescription = null,
                // The ROM's own icon is left whole: at 48 px, cropping removes a visible
                // part of the drawing. ES-DE serves box fronts, cropped the same way.
                contentScale = if (art.fitsWhole) ContentScale.Fit else ContentScale.Crop,
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
