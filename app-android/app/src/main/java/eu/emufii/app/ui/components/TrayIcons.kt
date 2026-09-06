package eu.emufii.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A 24-unit square, round caps, round joins, one weight: anything added is drawn to
 * those three rules.
 * pourquoi : docs/decisions/lancement-et-navigation.md § The app's icons are drawn, not typed
 */

private const val WEIGHT = 2.6f / 24f

@Composable
private fun TrayIcon(
    size: Dp,
    color: Color,
    modifier: Modifier = Modifier,
    draw: (Path.(Float) -> Unit)
) {
    Canvas(modifier = modifier.size(size)) {
        val unit = this.size.minDimension / 24f
        val path = Path().apply { draw(unit) }
        drawPath(
            path = path,
            color = color,
            style = Stroke(
                width = this.size.minDimension * WEIGHT,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
    }
}

@Composable
fun ChevronLeft(modifier: Modifier = Modifier, size: Dp = 20.dp, color: Color) =
    TrayIcon(size, color, modifier) { u ->
        moveTo(15f * u, 5f * u); lineTo(9f * u, 12f * u); lineTo(15f * u, 19f * u)
    }

@Composable
fun ChevronRight(modifier: Modifier = Modifier, size: Dp = 20.dp, color: Color) =
    TrayIcon(size, color, modifier) { u ->
        moveTo(9f * u, 5f * u); lineTo(15f * u, 12f * u); lineTo(9f * u, 19f * u)
    }

@Composable
fun CrossIcon(modifier: Modifier = Modifier, size: Dp = 18.dp, color: Color) =
    TrayIcon(size, color, modifier) { u ->
        moveTo(6.5f * u, 6.5f * u); lineTo(17.5f * u, 17.5f * u)
        moveTo(17.5f * u, 6.5f * u); lineTo(6.5f * u, 17.5f * u)
    }

@Composable
fun CheckIcon(modifier: Modifier = Modifier, size: Dp = 18.dp, color: Color) =
    TrayIcon(size, color, modifier) { u ->
        moveTo(5f * u, 12.5f * u); lineTo(10f * u, 17.5f * u); lineTo(19f * u, 6.5f * u)
    }

@Composable
fun SignalMark(modifier: Modifier = Modifier, size: Dp = 40.dp, color: Color) =
    TrayIcon(size, color, modifier) { u ->
        addArc(
            androidx.compose.ui.geometry.Rect(
                Offset(4f * u, 4f * u), Size(16f * u, 16f * u)
            ), -70f, -50f
        )
        addArc(
            androidx.compose.ui.geometry.Rect(
                Offset(8f * u, 8f * u), Size(8f * u, 8f * u)
            ), -70f, -50f
        )
        addOval(
            androidx.compose.ui.geometry.Rect(
                Offset(11f * u, 15f * u), Size(2f * u, 2f * u)
            )
        )
    }

@Composable
fun FolderMark(modifier: Modifier = Modifier, size: Dp = 44.dp, color: Color) =
    TrayIcon(size, color, modifier) { u ->
        moveTo(3.5f * u, 18.5f * u)
        lineTo(3.5f * u, 6f * u)
        lineTo(9.5f * u, 6f * u)
        lineTo(11.5f * u, 8.5f * u)
        lineTo(20.5f * u, 8.5f * u)
        lineTo(20.5f * u, 18.5f * u)
        close()
    }

/**
 * No triangle around the mark: an outline inside an outline reads as cramped.
 * pourquoi : docs/decisions/lancement-et-navigation.md § The app's icons are drawn, not typed
 */
@Composable
fun WarnIcon(modifier: Modifier = Modifier, size: Dp = 14.dp, color: Color) =
    TrayIcon(size, color, modifier) { u ->
        moveTo(12f * u, 5f * u); lineTo(12f * u, 14f * u)
        moveTo(12f * u, 18.6f * u); lineTo(12f * u, 18.6f * u)
    }

@Composable
@Suppress("Unused")
fun BlockedIcon(modifier: Modifier = Modifier, size: Dp = 14.dp, color: Color) =
    TrayIcon(size, color, modifier) { u ->
        addOval(
            androidx.compose.ui.geometry.Rect(
                Offset(4f * u, 4f * u),
                Size(16f * u, 16f * u)
            )
        )
        moveTo(7.2f * u, 7.2f * u); lineTo(16.8f * u, 16.8f * u)
    }

/** Not tried yet: the quietest of the four marks, the other three being verdicts. */
@Composable
fun TildeIcon(modifier: Modifier = Modifier, size: Dp = 14.dp, color: Color) =
    TrayIcon(size, color, modifier) { u ->
        moveTo(4.5f * u, 14f * u)
        cubicTo(7f * u, 8.5f * u, 9.5f * u, 8.5f * u, 12f * u, 12f * u)
        cubicTo(14.5f * u, 15.5f * u, 17f * u, 15.5f * u, 19.5f * u, 10f * u)
    }

/**
 * One mark per settings page: in a menu where every row looks alike, the eye finds a
 * page by its shape before reading its name.
 * pourquoi : docs/decisions/reglages-ecran.md § One icon per page, and not one more
 */

@Composable
fun PersonMark(modifier: Modifier = Modifier, size: Dp = 20.dp, color: Color) =
    TrayIcon(size, color, modifier) { u ->
        addOval(
            androidx.compose.ui.geometry.Rect(
                Offset(8f * u, 3.5f * u), Size(8f * u, 8f * u)
            )
        )
        moveTo(4.5f * u, 20.5f * u)
        cubicTo(4.5f * u, 15.5f * u, 19.5f * u, 15.5f * u, 19.5f * u, 20.5f * u)
    }

@Composable
fun ShelfMark(modifier: Modifier = Modifier, size: Dp = 20.dp, color: Color) =
    TrayIcon(size, color, modifier) { u ->
        moveTo(5f * u, 4.5f * u); lineTo(5f * u, 17f * u)
        moveTo(10f * u, 4.5f * u); lineTo(10f * u, 17f * u)
        moveTo(15f * u, 5.5f * u); lineTo(18.5f * u, 16.5f * u)
        moveTo(3f * u, 19.5f * u); lineTo(21f * u, 19.5f * u)
    }

@Composable
fun GridMark(modifier: Modifier = Modifier, size: Dp = 20.dp, color: Color) =
    TrayIcon(size, color, modifier) { u ->
        addRoundRect(
            androidx.compose.ui.geometry.RoundRect(
                left = 4f * u, top = 4f * u, right = 10.5f * u, bottom = 10.5f * u,
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.5f * u)
            )
        )
        addRoundRect(
            androidx.compose.ui.geometry.RoundRect(
                left = 13.5f * u, top = 4f * u, right = 20f * u, bottom = 10.5f * u,
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.5f * u)
            )
        )
        addRoundRect(
            androidx.compose.ui.geometry.RoundRect(
                left = 4f * u, top = 13.5f * u, right = 10.5f * u, bottom = 20f * u,
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.5f * u)
            )
        )
        // The fourth is not drawn: it is the hidden console.
    }

@Composable
fun ChipMark(modifier: Modifier = Modifier, size: Dp = 20.dp, color: Color) =
    TrayIcon(size, color, modifier) { u ->
        addRoundRect(
            androidx.compose.ui.geometry.RoundRect(
                left = 6.5f * u, top = 6.5f * u, right = 17.5f * u, bottom = 17.5f * u,
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f * u)
            )
        )
        moveTo(10f * u, 3.5f * u); lineTo(10f * u, 6.5f * u)
        moveTo(14f * u, 3.5f * u); lineTo(14f * u, 6.5f * u)
        moveTo(10f * u, 17.5f * u); lineTo(10f * u, 20.5f * u)
        moveTo(14f * u, 17.5f * u); lineTo(14f * u, 20.5f * u)
        moveTo(3.5f * u, 10f * u); lineTo(6.5f * u, 10f * u)
        moveTo(3.5f * u, 14f * u); lineTo(6.5f * u, 14f * u)
        moveTo(17.5f * u, 10f * u); lineTo(20.5f * u, 10f * u)
        moveTo(17.5f * u, 14f * u); lineTo(20.5f * u, 14f * u)
    }

@Composable
fun PaintMark(modifier: Modifier = Modifier, size: Dp = 20.dp, color: Color) =
    TrayIcon(size, color, modifier) { u ->
        moveTo(12f * u, 3.5f * u)
        cubicTo(12f * u, 3.5f * u, 5f * u, 11.5f * u, 5f * u, 15.2f * u)
        cubicTo(5f * u, 19f * u, 8.2f * u, 20.5f * u, 12f * u, 20.5f * u)
        cubicTo(15.8f * u, 20.5f * u, 19f * u, 19f * u, 19f * u, 15.2f * u)
        cubicTo(19f * u, 11.5f * u, 12f * u, 3.5f * u, 12f * u, 3.5f * u)
        close()
    }

@Composable
fun SlidersMark(modifier: Modifier = Modifier, size: Dp = 20.dp, color: Color) =
    TrayIcon(size, color, modifier) { u ->
        moveTo(4f * u, 8.5f * u); lineTo(20f * u, 8.5f * u)
        moveTo(4f * u, 15.5f * u); lineTo(20f * u, 15.5f * u)
        addOval(
            androidx.compose.ui.geometry.Rect(
                Offset(7f * u, 5.5f * u), Size(6f * u, 6f * u)
            )
        )
        addOval(
            androidx.compose.ui.geometry.Rect(
                Offset(13f * u, 12.5f * u), Size(6f * u, 6f * u)
            )
        )
    }

@Composable
fun InfoMark(modifier: Modifier = Modifier, size: Dp = 20.dp, color: Color) =
    TrayIcon(size, color, modifier) { u ->
        addOval(
            androidx.compose.ui.geometry.Rect(
                Offset(3.5f * u, 3.5f * u), Size(17f * u, 17f * u)
            )
        )
        moveTo(12f * u, 11f * u); lineTo(12f * u, 16.5f * u)
        moveTo(12f * u, 7.6f * u); lineTo(12f * u, 7.6f * u)
    }

/**
 * The report page: an oval body with two antennae, two side legs and a spine down the
 * middle. Drawn on the same 24-unit grid as every other tray glyph.
 */
@Composable
fun BugMark(modifier: Modifier = Modifier, size: Dp = 20.dp, color: Color) =
    TrayIcon(size, color, modifier) { u ->
        addOval(
            androidx.compose.ui.geometry.Rect(
                Offset(7f * u, 7.5f * u), Size(10f * u, 12f * u)
            )
        )
        moveTo(12f * u, 8.5f * u); lineTo(12f * u, 18.5f * u)
        moveTo(3.5f * u, 12f * u); lineTo(7f * u, 12f * u)
        moveTo(17f * u, 12f * u); lineTo(20.5f * u, 12f * u)
        moveTo(3.5f * u, 17.5f * u); lineTo(7f * u, 16f * u)
        moveTo(17f * u, 16f * u); lineTo(20.5f * u, 17.5f * u)
        moveTo(9f * u, 4f * u); lineTo(10.5f * u, 7f * u)
        moveTo(15f * u, 4f * u); lineTo(13.5f * u, 7f * u)
    }

@Composable
fun PencilMark(modifier: Modifier = Modifier, size: Dp = 14.dp, color: Color) =
    TrayIcon(size, color, modifier) { u ->
        moveTo(4.5f * u, 19.5f * u)
        lineTo(5.5f * u, 15f * u)
        lineTo(16f * u, 4.5f * u)
        lineTo(19.5f * u, 8f * u)
        lineTo(9f * u, 18.5f * u)
        close()
        moveTo(13.5f * u, 7f * u); lineTo(17f * u, 10.5f * u)
    }

/**
 * It lived in two copies at different proportions; a glyph is the same everywhere.
 * pourquoi : docs/decisions/lancement-et-navigation.md § The app's icons are drawn, not typed
 */
@Composable
fun LensMark(modifier: Modifier = Modifier, size: Dp = 20.dp, color: Color) =
    TrayIcon(size, color, modifier) { u ->
        addOval(
            androidx.compose.ui.geometry.Rect(
                Offset(3.5f * u, 3.5f * u), Size(13f * u, 13f * u)
            )
        )
        moveTo(15.8f * u, 15.8f * u); lineTo(20.5f * u, 20.5f * u)
    }
