package eu.emufii.app.ui.screens.library

import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import eu.emufii.app.ui.Motion
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import eu.emufii.app.ui.RING_IN_MS
import eu.emufii.app.ui.components.consoleArtwork
import eu.emufii.app.ui.focusRing
import eu.emufii.app.ui.gamepadClick
import eu.emufii.app.ui.ringColor
import eu.emufii.app.ui.screens.consolePlate
import eu.emufii.app.ui.screens.gameCount
import eu.emufii.app.ui.tap
import eu.emufii.app.ui.theme.InkText
import eu.emufii.app.ui.theme.LocalEmufiiDarkTheme
import eu.emufii.app.ui.theme.LocalEmufiiOledTheme
import eu.emufii.app.ui.theme.PillShape
import eu.emufii.app.ui.theme.TileShape
import eu.emufii.app.ui.theme.liftShadow

@Composable
internal fun FolderTile(
    folder: Entry.Folder,
    onClick: () -> Unit,
    selected: Boolean,
    padHeld: Boolean,
    band: Float = TILE_BAND,
    rest: () -> Float = { 1f },
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    // Same clock as the ring, and gone the instant the cursor leaves; see the ROM tile.
    val mark by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = if (selected) Motion.morph() else Motion.exit(),
        label = "folder-mark"
    )
    val focusScale = 1f + 0.07f * mark
    val scale by animateFloatAsState(
        targetValue = if (pressed || padHeld) 0.94f else 1f,
        animationSpec = Motion.press(),
        label = "folder-scale"
    )
    val riseX = TILE_RISE * mark
    val riseY = TILE_RISE * mark

    Column(
        modifier = modifier
            .fillMaxWidth()
            .zIndex(if (selected) 1f else 0f),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset(x = -riseX, y = -riseY)
                .aspectRatio(1f)
                .graphicsLayer {
                    val s = scale * focusScale * rest()
                    scaleX = s
                    scaleY = s
                }
                .liftShadow(
                    TileShape,
                    lift = if (selected) 10.dp else 4.dp,
                    dark = LocalEmufiiDarkTheme.current,
                    oled = LocalEmufiiOledTheme.current,
                    tint = paletteFor(folder.console.name).first,
                    tinted = { mark }
                )
                .focusRing(selected, TileShape, bandFraction = band)
                .clip(TileShape)
                .background(consolePlate(folder.console))
                .focusProperties { canFocus = false }
                .tap(interactionSource = interaction, indication = null, onClick = onClick)
                .gamepadClick(interaction, onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            val plate = consoleArtwork(folder.console, LocalEmufiiDarkTheme.current)
            if (plate != null) {
                Image(
                    painter = painterResource(plate),
                    contentDescription = folder.console.label,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(8.dp)
                        .clip(PillShape)
                        .background(Color.Black.copy(alpha = 0.55f))
                        .padding(horizontal = 10.dp, vertical = 3.dp)
                ) {
                    Text(
                        gameCount(folder.roms.size),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White
                    )
                }
            } else {
                // A console added later must not land on an empty tile.
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    BasicText(
                        text = folder.console.label,
                        style = MaterialTheme.typography.headlineSmall.copy(
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            textAlign = TextAlign.Center
                        ),
                        maxLines = 1,
                        autoSize = TextAutoSize.StepBased(
                            minFontSize = 13.sp,
                            maxFontSize = MaterialTheme.typography.headlineSmall.fontSize,
                            stepSize = 0.5.sp
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp)
                    )
                    Text(
                        gameCount(folder.roms.size),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.78f)
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Spacer(Modifier.height(32.dp))
    }
}
