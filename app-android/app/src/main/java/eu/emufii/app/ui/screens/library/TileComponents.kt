package eu.emufii.app.ui.screens.library

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

/**
 * Fades out at the end when it overflows. Two lines always reserved.
 * pourquoi : docs/decisions/bibliotheque.md § Holding A, and the title that fades out
 */
@Composable
internal fun TileTitle(title: String, modifier: Modifier = Modifier) {
    val style = MaterialTheme.typography.labelMedium
    val boxHeight = TILE_TITLE_ROOM

    // Applied to every title it made a name that fitted look truncated, "Crash of the
    // Titans" losing "Titans".
    var overflows by remember(title) { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(boxHeight)
            // The gradient applies to the text's rendering, hence the `DstIn` on a layer.
            // pourquoi : docs/decisions/performance-rendu.md § An offscreen layer is not a drawing setting
            .then(
                if (overflows) {
                    Modifier.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                } else {
                    Modifier
                }
            )
            .drawWithContent {
                drawContent()
                if (overflows) {
                    drawRect(
                        brush = Brush.verticalGradient(
                            0.55f to Color.Black,
                            1f to Color.Transparent
                        ),
                        blendMode = BlendMode.DstIn
                    )
                }
            }
    ) {
        Text(
            title,
            style = style,
            // Three lines in a box showing two, so an over-long title fades downwards
            // instead of stopping dead.
            maxLines = 3,
            // No Ellipsis: the dots eat three characters, and the fade already says it.
            overflow = TextOverflow.Clip,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground,
            onTextLayout = { overflows = it.lineCount > 2 },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
internal fun ConsoleBadge(console: Console, modifier: Modifier = Modifier) {
    // A dark translucent chip vanished on dark box art; the white contour holds over
    // artwork we do not control.
    Surface(
        shape = RoundedCornerShape(9.dp),
        color = InkText,
        border = BorderStroke(1.5.dp, Color.White),
        shadowElevation = 2.dp,
        modifier = modifier
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
