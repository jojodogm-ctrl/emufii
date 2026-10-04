package eu.emufii.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.emufii.app.ui.theme.PillShape

@Composable
fun SheetHeader(
    icon: ImageBitmap?,
    fallbackLetter: String,
    title: String,
    subtitle: String?,
    accent: Color,
    subtitleInk: Color = accent,
    iconSize: Dp = 58.dp,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(iconSize)
                .then(
                    if (icon != null) Modifier
                    else Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(accent.copy(alpha = 0.14f))
                )
        ) {
            if (icon != null) {
                Image(bitmap = icon, contentDescription = null, modifier = Modifier.fillMaxSize())
            } else {
                Text(
                    fallbackLetter,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    color = accent
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (!subtitle.isNullOrEmpty()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = subtitleInk,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun FactTile(label: String, value: String, ink: Color, modifier: Modifier = Modifier) {
    Column(
        verticalArrangement = Arrangement.spacedBy(3.dp),
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
            .padding(horizontal = 14.dp, vertical = 11.dp)
    ) {
        SheetLabel(label)
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun SheetLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
fun SheetWarning(text: String, ink: Color) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.height(IntrinsicSize.Min)
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .clip(PillShape)
                .background(ink)
        )
        Text(
            accented(text, ink),
            style = MaterialTheme.typography.bodyMedium,
            lineHeight = 22.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.76f)
        )
    }
}

@Composable
fun SheetStep(number: Int, text: String, accent: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .padding(top = 1.dp)
                .size(22.dp)
                .clip(PillShape)
                .background(accent.copy(alpha = 0.16f))
        ) {
            Text(
                number.toString(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Black,
                color = accent
            )
        }
        Text(
            accented(text, accent),
            style = MaterialTheme.typography.bodyMedium,
            lineHeight = 22.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun accented(raw: String, accent: Color): AnnotatedString =
    remember(raw, accent) {
        buildAnnotatedString {
            raw.split('*').forEachIndexed { index, part ->
                if (part.isEmpty()) return@forEachIndexed
                if (index % 2 == 1) {
                    withStyle(SpanStyle(color = accent, fontWeight = FontWeight.Bold)) {
                        append(part)
                    }
                } else {
                    append(part)
                }
            }
        }
    }

/** Unspecified where the page scrolls anyway (portrait). */
val LocalSheetMaxHeight = androidx.compose.runtime.staticCompositionLocalOf { Dp.Unspecified }

fun Modifier.optional(): Modifier = this.layoutId(OPTIONAL)

private const val OPTIONAL = "sheet-optional"

@Composable
fun Optional(content: @Composable () -> Unit) {
    Box(Modifier.optional()) { content() }
}

@Composable
fun FitColumn(
    maxHeight: Dp,
    spacing: Dp,
    modifier: Modifier = Modifier,
    strictOrder: Boolean = false,
    content: @Composable () -> Unit,
) {
    androidx.compose.ui.layout.Layout(content = content, modifier = modifier) { measurables, constraints ->
        val gap = spacing.roundToPx()
        val limit = if (maxHeight == Dp.Unspecified) Int.MAX_VALUE else maxHeight.roundToPx()
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val placeables = measurables.map { it.measure(loose) }
        val optional = measurables.map { it.layoutId == OPTIONAL }
        var used = placeables.withIndex().filter { !optional[it.index] }
            .sumOf { it.value.height } + gap * (placeables.size - 1).coerceAtLeast(0)
        val kept = BooleanArray(placeables.size) { !optional[it] }
        var closed = false
        for (i in placeables.indices) {
            if (!optional[i]) continue
            if (!closed && used + placeables[i].height <= limit) {
                kept[i] = true
                used += placeables[i].height
            } else {
                used -= gap
                closed = strictOrder
            }
        }
        val shown = placeables.filterIndexed { i, _ -> kept[i] }
        val height = shown.sumOf { it.height } + gap * (shown.size - 1).coerceAtLeast(0)
        layout(constraints.maxWidth, height.coerceAtLeast(constraints.minHeight)) {
            var y = 0
            shown.forEach { p ->
                p.placeRelative(0, y)
                y += p.height + gap
            }
        }
    }
}
