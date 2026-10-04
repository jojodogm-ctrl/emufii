package eu.emufii.app.ui.components

import eu.emufii.app.artwork.COVER_REQUEST_PX
import eu.emufii.app.artwork.CoverTone
import eu.emufii.app.ui.theme.LocalEmufiiOledTheme
import eu.emufii.app.ui.theme.LocalEmufiiDarkTheme
import eu.emufii.app.ui.theme.liftShadow
import eu.emufii.app.ui.sharedCover
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

@Composable
fun RomArtwork(rom: Rom, size: Dp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val accent = rom.accentArgb?.let { Color(it) }
    val art by rememberTileArt(rom)
    Box(
        modifier = modifier
            .sharedCover(rom.uri, mine = true)
            .size(size)
            .liftShadow(
                TileShape,
                lift = if (size >= 100.dp) 16.dp else 6.dp,
                dark = LocalEmufiiDarkTheme.current,
                oled = LocalEmufiiOledTheme.current,
                tintNow = { CoverTone.of(art.model) ?: accent }
            )
            .clip(TileShape)
            .background(tilePlate())
    ) {
        if (art.model != null) {
            AsyncImage(
                model = ImageRequest.Builder(context).data(art.model).size(COVER_REQUEST_PX).build(),
                onSuccess = { CoverTone.learn(art.model, it.result.image) },
                contentDescription = null,
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
