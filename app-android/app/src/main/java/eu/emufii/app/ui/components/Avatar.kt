package eu.emufii.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import eu.emufii.app.profile.avatarPaletteFor
import eu.emufii.app.profile.initialsFor
import eu.emufii.app.ui.theme.Coral
import eu.emufii.app.ui.theme.GlyphInk
import eu.emufii.app.ui.theme.Teal
import eu.emufii.app.ui.theme.Violet
import eu.emufii.app.ui.theme.VioletDark
import java.io.File

@Composable
fun Avatar(
    name: String,
    modifier: Modifier = Modifier,
    imageFile: File? = null,
    size: Dp = 40.dp,
    ring: Color? = null,
    shape: androidx.compose.ui.graphics.Shape = CircleShape
) {
    val context = LocalContext.current
    val (c1, c2) = AVATAR_PALETTE[avatarPaletteFor(name, AVATAR_PALETTE.size)]

    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(Brush.linearGradient(listOf(c1, c2)))
            .then(
                if (ring != null) Modifier.border(BorderStroke(2.dp, ring), shape)
                else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        if (imageFile != null) {
            AsyncImage(
                // Cache key on path and mtime: the path is reused, and friends fetched in one second share an mtime.
                model = ImageRequest.Builder(context)
                    .data(imageFile)
                    .memoryCacheKey("avatar-${imageFile.path}-${imageFile.lastModified()}")
                    .diskCacheKey("avatar-${imageFile.path}-${imageFile.lastModified()}")
                    .build(),
                contentDescription = name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(shape)
            )
        } else {
            val onFace =
                if (c1 == Coral.bright || c1 == Teal.bright) GlyphInk
                else Color.White
            androidx.compose.material3.Text(
                text = initialsFor(name),
                color = onFace,
                fontWeight = FontWeight.Black,
                fontSize = (size.value * 0.36f).sp
            )
        }
    }
}

private val AVATAR_PALETTE = listOf(
    Coral.bright to Teal.bright,
    Coral.bright to Violet,
    Teal.bright to Violet,
    Coral.deep to Coral.bright,
    Teal.deep to Teal.bright,
    Violet to Coral.deep,
    VioletDark to Teal.bright,
    Coral.ink to Coral.bright
)
