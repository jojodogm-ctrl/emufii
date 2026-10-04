package eu.emufii.app.ui.screens.library

import androidx.compose.ui.draw.shadow
import eu.emufii.app.ui.LEGACY_AMBIENT
import eu.emufii.app.ui.LEGACY_SPOT
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.emufii.app.library.Console
import eu.emufii.app.library.shortLabel
import eu.emufii.app.ui.theme.InkText

@Composable
internal fun TileTitle(title: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(TILE_TITLE_ROOM)
    ) {
        Text(
            title,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
internal fun ConsoleBadge(console: Console, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(9.dp),
        color = InkText,
        border = BorderStroke(1.5.dp, Color.White),
        modifier = modifier.shadow(
            2.dp,
            RoundedCornerShape(9.dp),
            ambientColor = Color.Black.copy(alpha = LEGACY_AMBIENT),
            spotColor = Color.Black.copy(alpha = LEGACY_SPOT)
        )
    ) {
        Text(
            console.shortLabel,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
        )
    }
}

@Composable
internal fun PlaceholderArtwork(title: String) {
    val (c1, c2) = paletteFor(title)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(c1, c2),
                    start = Offset.Zero,
                    end = Offset.Infinite
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = shortLabel(title),
            color = Color.White,
            fontSize = 36.sp,
            fontWeight = FontWeight.Black
        )
    }
}
