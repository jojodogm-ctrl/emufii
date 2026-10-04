package eu.emufii.app.ui.components

import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.emufii.app.R
import eu.emufii.app.library.Rom
import eu.emufii.app.library.RomsRepository
import eu.emufii.app.notify.FriendEvent
import eu.emufii.app.ui.theme.GoodDark
import eu.emufii.app.ui.theme.GoodLight
import eu.emufii.app.ui.theme.LocalEmufiiDarkTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun FriendBadge(event: FriendEvent, size: Dp, modifier: Modifier = Modifier) {
    val name = event.name ?: stringResource(R.string.notify_friend_unnamed)
    when (event) {
        is FriendEvent.CameOnline -> Box(modifier.size(size)) {
            Avatar(name = name, size = size)
            OnlineBead(
                size = size * 0.34f,
                modifier = Modifier.align(Alignment.BottomEnd).offset(x = size * 0.06f, y = size * 0.06f)
            )
        }
        is FriendEvent.StartedPlaying -> Box(modifier.size(size)) {
            val rom = rememberLibraryRom(event.game)
            val shape = RoundedCornerShape(size * 0.26f)
            if (rom != null) {
                RomArtwork(rom = rom, size = size, modifier = Modifier.clip(shape))
            } else {
                Box(
                    Modifier
                        .size(size)
                        .clip(shape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) { Text("🎮", fontSize = (size.value * 0.5f).sp) }
            }
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = size * 0.14f, y = size * 0.14f)
                    .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
            ) { Avatar(name = name, size = size * 0.48f) }
        }
    }
}

@Composable
private fun OnlineBead(size: Dp, modifier: Modifier = Modifier) {
    val tone = if (LocalEmufiiDarkTheme.current) GoodDark else GoodLight
    val breath by rememberInfiniteTransition(label = "bead").animateFloat(
        initialValue = 0.6f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(tween(1800, easing = LinearOutSlowInEasing), RepeatMode.Reverse),
        label = "bead-breath"
    )
    val ring = MaterialTheme.colorScheme.surface
    Box(
        modifier
            .size(size)
            .drawBehind {
                val r = this.size.minDimension * 1.1f
                drawCircle(
                    Brush.radialGradient(listOf(tone.copy(alpha = 0.3f * breath), Color.Transparent), center, r),
                    radius = r
                )
                drawCircle(ring)
                drawCircle(
                    Brush.radialGradient(
                        listOf(Color.White.copy(alpha = 0.6f).compositeOver(tone), tone),
                        center = Offset(this.size.width * 0.38f, this.size.height * 0.34f),
                        radius = this.size.minDimension * 0.6f
                    ),
                    radius = this.size.minDimension * 0.36f
                )
            }
    )
}

@Composable
private fun rememberLibraryRom(title: String?): Rom? {
    val context = LocalContext.current
    val repo = remember(context) { RomsRepository.get(context) }
    val rom by produceState<Rom?>(null, title) {
        if (title == null) return@produceState
        value = withContext(Dispatchers.IO) {
            val want = titleKey(title)
            runCatching { repo.cachedOrScan() }.getOrDefault(emptyList())
                .firstOrNull { titleKey(it.displayName) == want }
        }
    }
    return rom
}

/** Letters and digits only: "Animal Crossing™: New Horizons" must match "Animal Crossing: New Horizons". */
internal fun titleKey(title: String): String =
    java.text.Normalizer.normalize(title, java.text.Normalizer.Form.NFD)
        .lowercase()
        .filter { it.isLetterOrDigit() }
