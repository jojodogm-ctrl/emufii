package eu.emufii.app.ui.components

import eu.emufii.app.ui.LEGACY_AMBIENT
import eu.emufii.app.ui.LEGACY_SPOT
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.emufii.app.R
import eu.emufii.app.network.CoordinatorClient
import eu.emufii.app.network.RelayRegion
import eu.emufii.app.network.RelayRegions
import eu.emufii.app.secondscreen.VpsState
import eu.emufii.app.secondscreen.VpsStatus
import eu.emufii.app.ui.CONFIRM_KEYS
import eu.emufii.app.ui.Sfx
import eu.emufii.app.ui.focusRing
import eu.emufii.app.ui.tap
import eu.emufii.app.ui.theme.ErrorDark
import eu.emufii.app.ui.theme.ErrorLight
import eu.emufii.app.ui.theme.GoodDark
import eu.emufii.app.ui.theme.GoodLight
import eu.emufii.app.ui.theme.LocalEmufiiDarkTheme
import eu.emufii.app.ui.theme.LocalEmufiiOledTheme
import eu.emufii.app.ui.theme.PillShape
import eu.emufii.app.ui.theme.plate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

enum class LampMode {
    STATIC,

    FOCUSABLE,

    REMOTE
}

object ServerPicker {
    private val _aimed = MutableStateFlow(false)
    val aimed: StateFlow<Boolean> = _aimed.asStateFlow()

    private val _open = MutableStateFlow(false)
    val open: StateFlow<Boolean> = _open.asStateFlow()

    private val _from = MutableStateFlow(LampMode.STATIC)
    val from: StateFlow<LampMode> = _from.asStateFlow()

    private val _index = MutableStateFlow(0)
    val index: StateFlow<Int> = _index.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val client by lazy { CoordinatorClient() }

    private fun entries() = 1 + RelayRegions.options.value.size

    fun aim() {
        _aimed.value = true
    }

    fun release() {
        _open.value = false
        _aimed.value = false
    }

    fun openMenu(from: LampMode = if (_aimed.value) LampMode.REMOTE else LampMode.FOCUSABLE) {
        _from.value = from
        val manual = RelayRegions.manual.value
        val at = RelayRegions.options.value.indexOfFirst { it.region.id == manual }
        _index.value = if (manual == null || at < 0) 0 else at + 1
        _open.value = true
        scope.launch { RelayRegions.refresh(client) }
    }

    fun close() {
        _open.value = false
    }

    fun pick(entry: Int) {
        val options = RelayRegions.options.value
        RelayRegions.setManual(if (entry == 0) null else options.getOrNull(entry - 1)?.region?.id)
        _open.value = false
    }

    private fun step(by: Int) {
        val next = (_index.value + by).coerceIn(0, entries() - 1)
        // No sound here: the aimed row's ring plays it, and both did, twice per step.
        _index.value = next
    }

    fun handleKey(event: KeyEvent, remote: Boolean): Boolean {
        val down = event.type == KeyEventType.KeyDown
        if (event.key in CONFIRM_KEYS) {
            // On release: the press that opened must not also pick.
            if (event.type == KeyEventType.KeyUp) {
                Sfx.click()
                if (_open.value) pick(_index.value) else openMenu()
            }
            return true
        }
        if (_open.value) {
            if (down) when (event.key) {
                Key.DirectionUp -> step(-1)
                Key.DirectionDown -> step(1)
                Key.ButtonB, Key.Back -> close()
                else -> {}
            }
            return true
        }
        if (remote && down) {
            when (event.key) {
                Key.DirectionUp, Key.ButtonB, Key.Back -> release()
                else -> {}
            }
            return true
        }
        return false
    }
}

@Composable
fun VpsLamp(modifier: Modifier = Modifier, dotSize: Dp = 15.dp, mode: LampMode = LampMode.STATIC) {
    LaunchedEffect(Unit) { VpsStatus.keepPolling() }

    val state by VpsStatus.state.collectAsStateWithLifecycle()
    val dark = LocalEmufiiDarkTheme.current
    val options by RelayRegions.options.collectAsStateWithLifecycle()
    val manual by RelayRegions.manual.collectAsStateWithLifecycle()
    val auto by RelayRegions.auto.collectAsStateWithLifecycle()
    val open by ServerPicker.open.collectAsStateWithLifecycle()
    val aimed by ServerPicker.aimed.collectAsStateWithLifecycle()
    val client = remember { CoordinatorClient() }
    LaunchedEffect(Unit) { RelayRegions.refresh(client) }
    val current = RelayRegions.resolved(options, manual, auto) ?: options.firstOrNull()?.region?.id

    val rtt = options.firstOrNull { it.region.id == current }?.rttMs
    val target = when (state) {
        VpsState.ONLINE -> latencyColor(rtt, dark)
        VpsState.OFFLINE -> if (dark) ErrorDark else ErrorLight
        VpsState.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val tone by animateColorAsState(target, tween(500), label = "lamp-tone")
    val breath = eu.emufii.app.ui.rememberSlowBreath()
    val glow = if (state == VpsState.UNKNOWN) 0f else breath

    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val settling by animateFloatAsState(if (open) 1f else 0f, tween(if (open) 0 else 220), label = "lamp-settle")
    val lit = settling == 0f && when (mode) {
        LampMode.STATIC -> false
        LampMode.FOCUSABLE -> focused
        LampMode.REMOTE -> aimed
    }
    if (mode != LampMode.STATIC) {
        DisposableEffect(Unit) { onDispose { ServerPicker.close() } }
    }

    Box(modifier) {
        Row(
            modifier = when (mode) {
                LampMode.STATIC -> Modifier
                LampMode.FOCUSABLE -> Modifier
                    .focusRing(lit, PillShape, width = 3.dp, glowRadius = 16.dp)
                    .onFocusChanged { if (!it.isFocused) ServerPicker.close() }
                    .onPreviewKeyEvent { e -> focused && ServerPicker.handleKey(e, remote = false) }
                    .focusable(interactionSource = interaction)
                    .clip(PillShape)
                    .tap(interactionSource = interaction, indication = null) {
                        if (open) ServerPicker.close() else ServerPicker.openMenu(LampMode.FOCUSABLE)
                    }
                LampMode.REMOTE -> Modifier
                    .focusRing(lit, PillShape, width = 3.dp, glowRadius = 16.dp)
                    .plate(shape = PillShape, dark = dark, oled = LocalEmufiiOledTheme.current, lift = 3.dp)
                    .clip(PillShape)
                    .tap(interactionSource = interaction, indication = null) {
                        if (open) ServerPicker.close() else ServerPicker.openMenu(LampMode.REMOTE)
                    }
            }.padding(
                horizontal = when (mode) { LampMode.STATIC -> 0.dp; LampMode.REMOTE -> 18.dp; else -> 10.dp },
                vertical = if (mode == LampMode.REMOTE) 10.dp else 4.dp
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(dotSize)
                    .drawBehind {
                        if (glow > 0f) {
                            val r = size.minDimension * 1.15f
                            drawCircle(
                                Brush.radialGradient(
                                    listOf(tone.copy(alpha = 0.26f * glow), tone.copy(alpha = 0f)),
                                    center = center,
                                    radius = r
                                ),
                                radius = r
                            )
                        }
                    }
                    .drawBehind {
                        drawCircle(
                            Brush.radialGradient(
                                listOf(Color.White.copy(alpha = 0.6f).compositeOver(tone), tone),
                                center = androidx.compose.ui.geometry.Offset(size.width * 0.35f, size.height * 0.3f),
                                radius = size.minDimension * 0.75f
                            )
                        )
                    }
            )
            Column {
                Text(
                    stringResource(R.string.panel_vps),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                val status = stringResource(
                    when (state) {
                        VpsState.ONLINE -> R.string.panel_vps_online
                        VpsState.OFFLINE -> R.string.panel_vps_offline
                        VpsState.UNKNOWN -> R.string.panel_vps_unknown
                    }
                )
                val where = options.firstOrNull { it.region.id == current }?.let { regionName(it.region) }
                Text(
                    if (where == null) status else "$status · $where",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f)
                )
            }
        }
        if (mode != LampMode.STATIC) {
            ServerMenu(
                visible = open && ServerPicker.from.collectAsStateWithLifecycle().value == mode,
                centred = mode == LampMode.REMOTE
            )
        }
    }
}

@Composable
private fun ServerMenu(visible: Boolean, centred: Boolean) {
    val shown = remember { MutableTransitionState(false) }
    shown.targetState = visible
    if (!shown.currentState && !shown.targetState) return

    val options by RelayRegions.options.collectAsStateWithLifecycle()
    val manual by RelayRegions.manual.collectAsStateWithLifecycle()
    val auto by RelayRegions.auto.collectAsStateWithLifecycle()
    val index by ServerPicker.index.collectAsStateWithLifecycle()
    val dark = LocalEmufiiDarkTheme.current
    val oled = LocalEmufiiOledTheme.current
    val autoName = options.firstOrNull { it.region.id == auto }?.let { regionName(it.region) }
    val shape = RoundedCornerShape(26.dp)

    val density = LocalDensity.current
    val below = with(density) { IntOffset(18.dp.roundToPx(), 150 - 18.dp.roundToPx()) }
    Popup(
        popupPositionProvider = remember(centred, below) {
            object : PopupPositionProvider {
                override fun calculatePosition(
                    anchorBounds: IntRect,
                    windowSize: IntSize,
                    layoutDirection: LayoutDirection,
                    popupContentSize: IntSize
                ): IntOffset = if (centred) {
                    IntOffset(
                        (windowSize.width - popupContentSize.width) / 2,
                        (windowSize.height - popupContentSize.height) / 2
                    )
                } else {
                    IntOffset(anchorBounds.right - popupContentSize.width + below.x, anchorBounds.top + below.y)
                }
            }
        },
        onDismissRequest = { ServerPicker.close() },
        // Not focusable: the lamp forwards keys; a focusable popup would swallow them.
        properties = PopupProperties(focusable = false)
    ) {
        val corner = if (centred) TransformOrigin(0.5f, 0.5f) else TransformOrigin(1f, 0f)
        AnimatedVisibility(
            visibleState = shown,
            enter = fadeIn(tween(160)) +
                scaleIn(spring(dampingRatio = 0.74f, stiffness = 480f), initialScale = 0.82f, transformOrigin = corner) +
                slideInVertically(spring(dampingRatio = 0.8f, stiffness = 420f)) { -it / 8 },
            exit = fadeOut(tween(130, delayMillis = 30)) +
                scaleOut(tween(170, easing = FastOutLinearInEasing), targetScale = 0.9f, transformOrigin = corner) +
                slideOutVertically(tween(170, easing = FastOutLinearInEasing)) { -it / 12 }
        ) {
            CompositionLocalProvider(LocalMenuScope provides this) {
            Column(
                modifier = Modifier
                    .padding(36.dp)
                    .width(340.dp)
                    .plate(shape = shape, dark = dark, oled = oled, lift = 16.dp)
                    .padding(horizontal = 12.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    stringResource(R.string.relay_title),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 12.dp, bottom = 4.dp)
                )
                ServerRow(
                    modifier = Modifier.staggered(0),
                    badge = "🌐",
                    title = stringResource(R.string.relay_auto),
                    detail = autoName?.let { stringResource(R.string.relay_auto_now, it) }
                        ?: stringResource(R.string.relay_auto_hint),
                    rtt = null,
                    auto = true,
                    chosen = manual == null,
                    aimed = index == 0,
                    onTap = { ServerPicker.pick(0) }
                )
                options.forEachIndexed { i, o ->
                    ServerRow(
                        modifier = Modifier.staggered(i + 1),
                        badge = regionEmoji(o.region.id),
                        title = regionName(o.region),
                        detail = o.rttMs?.let { stringResource(R.string.relay_rtt, it.roundToInt()) }
                            ?: stringResource(R.string.relay_rtt_unknown),
                        rtt = o.rttMs,
                        auto = false,
                        chosen = manual == o.region.id,
                        aimed = index == i + 1,
                        onTap = { ServerPicker.pick(i + 1) }
                    )
                }
            }
            }
        }
    }
}

@Composable
private fun Modifier.staggered(i: Int): Modifier = with(LocalMenuScope.current ?: return this) {
    this@staggered.animateEnterExit(
        enter = slideInVertically(tween(340, delayMillis = 40 + i * 45, easing = LinearOutSlowInEasing)) { it / 2 },
        exit = ExitTransition.None
    )
}

private val LocalMenuScope = staticCompositionLocalOf<AnimatedVisibilityScope?> { null }

@Composable
private fun ServerRow(
    modifier: Modifier = Modifier,
    badge: String,
    title: String,
    detail: String,
    rtt: Double?,
    auto: Boolean,
    chosen: Boolean,
    aimed: Boolean,
    onTap: () -> Unit
) {
    val dark = LocalEmufiiDarkTheme.current
    val good = if (dark) GoodDark else GoodLight
    val fill by animateColorAsState(
        if (aimed) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f) else Color.Transparent,
        label = "server-row"
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .focusRing(aimed, RoundedCornerShape(18.dp), width = 3.dp, glowRadius = 14.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(fill)
            .tap(onClick = onTap)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(
                    if (chosen) good.copy(alpha = 0.22f)
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(badge, fontSize = 24.sp)
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (chosen) FontWeight.Bold else FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (!auto) SignalBars(rtt)
        Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
            if (chosen) {
                Box(
                    Modifier.size(22.dp).clip(CircleShape).background(good),
                    contentAlignment = Alignment.Center
                ) {
                    Text("✓", fontWeight = FontWeight.Black, color = Color.White, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun SignalBars(rtt: Double?) {
    val dark = LocalEmufiiDarkTheme.current
    val lit = when {
        rtt == null -> 0
        rtt < 60 -> 3
        rtt < 130 -> 2
        else -> 1
    }
    val on = latencyColor(rtt, dark)
    val off = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Bottom) {
        listOf(8.dp, 13.dp, 18.dp).forEachIndexed { i, h ->
            Box(
                Modifier
                    .width(5.dp)
                    .height(h)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (i < lit) on else off)
            )
        }
    }
    Spacer(Modifier.width(2.dp))
}

@Composable
private fun regionName(r: RelayRegion): String = when (r.id) {
    "eu" -> stringResource(R.string.region_eu)
    "na" -> stringResource(R.string.region_na)
    else -> r.name.ifBlank { r.id }
}

@Composable
fun Modifier.serverMenuBackdrop(mode: LampMode): Modifier {
    val open by ServerPicker.open.collectAsStateWithLifecycle()
    val from by ServerPicker.from.collectAsStateWithLifecycle()
    val on = open && from == mode
    val t by animateFloatAsState(
        targetValue = if (on) 1f else 0f,
        animationSpec = if (on) tween(280, easing = LinearOutSlowInEasing) else tween(200, easing = FastOutLinearInEasing),
        label = "server-backdrop"
    )
    val radius = with(LocalDensity.current) { 14.dp.toPx() }
    return this
        .graphicsLayer {
            // No effect at rest: a zero blur still costs an offscreen pass.
            renderEffect = if (t > 0.01f) BlurEffect(radius * t, radius * t, TileMode.Clamp) else null
        }
        .drawWithContent {
            drawContent()
            if (t > 0f) drawRect(Color.Black.copy(alpha = 0.32f * t))
        }
}

private fun latencyColor(rtt: Double?, dark: Boolean): Color = when {
    rtt == null || rtt < 60 -> if (dark) GoodDark else GoodLight
    rtt < 130 -> Color(0xFFF2B233)
    else -> if (dark) ErrorDark else ErrorLight
}

private fun regionEmoji(id: String): String = when (id) {
    "eu" -> "🇪🇺"
    "na" -> "🇺🇸"
    else -> "🌐"
}
