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
import java.io.File

/**
 * A player, as a circle.
 *
 * With a picture, it's the picture. Without, initials on a colour derived from
 * the name, stable, so someone keeps the same colour from one session to the
 * next and becomes recognisable without ever uploading anything.
 */
@Composable
fun Avatar(
    name: String,
    modifier: Modifier = Modifier,
    imageFile: File? = null,
    size: Dp = 40.dp,
    ring: Color? = null
) {
    val context = LocalContext.current
    val (c1, c2) = AVATAR_PALETTE[avatarPaletteFor(name, AVATAR_PALETTE.size)]

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(c1, c2)))
            .then(
                if (ring != null) Modifier.border(BorderStroke(2.dp, ring), CircleShape)
                else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        if (imageFile != null) {
            AsyncImage(
                // The file path never changes, so without a cache key tied to
                // its mtime Coil would keep serving the previous picture after
                // the user picks a new one.
                model = ImageRequest.Builder(context)
                    .data(imageFile)
                    .memoryCacheKey("avatar-${imageFile.lastModified()}")
                    .diskCacheKey("avatar-${imageFile.lastModified()}")
                    .build(),
                contentDescription = name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(CircleShape)
            )
        } else {
            androidx.compose.material3.Text(
                text = initialsFor(name),
                color = Color.White,
                fontWeight = FontWeight.Black,
                fontSize = (size.value * 0.36f).sp
            )
        }
    }
}

/**
 * Twelve rather than eight: with two or three people on screen at once, a
 * collision is immediately visible, two players sharing a colour read as the
 * same person at a glance. More hues make that rarer without ever ruling it out.
 */
private val AVATAR_PALETTE = listOf(
    Color(0xFF6C5CE7) to Color(0xFF00CEC9),
    Color(0xFFFD79A8) to Color(0xFFE84393),
    Color(0xFF00B894) to Color(0xFF55EFC4),
    Color(0xFFFDCB6E) to Color(0xFFE17055),
    Color(0xFF74B9FF) to Color(0xFF0984E3),
    Color(0xFFA29BFE) to Color(0xFF6C5CE7),
    Color(0xFFFF7675) to Color(0xFFD63031),
    Color(0xFF81ECEC) to Color(0xFF00CEC9),
    Color(0xFFF6B93B) to Color(0xFFE58E26),
    Color(0xFF38ADA9) to Color(0xFF079992),
    Color(0xFFB53471) to Color(0xFF833471),
    Color(0xFF5758BB) to Color(0xFF1B1464)
)
