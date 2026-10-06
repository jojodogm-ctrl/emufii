package eu.emufii.app.ui.screens.session

import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.BoxWithConstraints
import eu.emufii.app.ui.components.LocalSheetMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.offset
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import eu.emufii.app.library.Backend
import eu.emufii.app.library.Rom
import eu.emufii.app.network.Member
import eu.emufii.app.profile.Profile
import eu.emufii.app.session.Session
import eu.emufii.app.session.netplayPlan
import eu.emufii.app.ui.EntryScroll
import eu.emufii.app.ui.LocalEntryScroll
import eu.emufii.app.ui.components.RomArtwork
import eu.emufii.app.ui.components.padEntry

@Suppress("LongParameterList")
@Composable
internal fun SessionLandscapeLayout(
    session: Session,
    profile: Profile,
    topPadding: Dp,
    bottomInset: Dp,
    modifier: Modifier,
    panelLive: Boolean,
    others: List<Member>,
    offline: Boolean,
    shownAddress: String,
    shownPort: String?,
    addressLabel: String,
    sessionArt: Rom?,
    automationOn: Boolean,
    pspAutomatic: Boolean,
    ps2Automatic: Boolean,
    netplayDone: Boolean,
    netplayPrepared: Boolean,
    pspOpened: Boolean,
    waitingForHost: Boolean,
    status: String?,
    onNetplayStep: () -> Unit,
    onLaunchStep: () -> Unit,
    onPspSetup: () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxSize()
            .padding(
                top = topPadding,
                bottom = bottomInset + 16.dp,
                start = 20.dp,
                end = 20.dp
            ),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        val stack =
            if (panelLive) Arrangement.spacedBy(12.dp, Alignment.CenterVertically)
            else Arrangement.spacedBy(12.dp)
        Column(
            modifier = Modifier
                .width(if (panelLive) 220.dp else 272.dp)
                .fillMaxHeight()
                .padding(
                    bottom = if (panelLive) (topPadding - bottomInset - 16.dp).coerceAtLeast(0.dp)
                    else 0.dp
                ),
            verticalArrangement = stack
        ) {
            PresenceCard(
                youName = profile.name,
                youPicture = profile.avatarFile,
                others = others,
                isHost = session.role == Session.Role.HOST,
                live = !offline,
                fitted = true,
                modifier = Modifier.weight(1f, fill = false)
            )
            if (!panelLive) {
                ConnectionCard(
                    hostIp = shownAddress,
                    addressLabel = addressLabel,
                    port = shownPort,
                    romName = session.rom?.displayName
                )
            }

            if (panelLive) {
                sessionArt?.let { art ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false),
                        contentAlignment = Alignment.Center
                    ) {
                        BoxWithConstraints {
                            val side = minOf(maxWidth, maxHeight)
                            if (side >= 96.dp) {
                                RomArtwork(rom = art, size = minOf(side, 208.dp))
                            }
                        }
                    }
                }
            }
        }

        Column(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            verticalArrangement = stack,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val fade = !panelLive
            val paneScroll = rememberScrollState()
            val pane = remember(paneScroll) { EntryScroll(paneScroll) }
            CompositionLocalProvider(LocalEntryScroll provides pane) {
            BoxWithConstraints(
                modifier = Modifier
                    .then(if (panelLive) Modifier.widthIn(max = 640.dp) else Modifier)
                    .fillMaxWidth()
                    .weight(1f, fill = panelLive)
            ) {
            val screenMiddle = (maxHeight - topPadding + bottomInset + 16.dp) / 2
            CompositionLocalProvider(LocalSheetMaxHeight provides maxHeight - PANE_BLEED * 2) {
            Column(
                modifier = Modifier
                    .then(
                        if (!panelLive) Modifier else Modifier.layout { measurable, constraints ->
                            val p = measurable.measure(constraints)
                            val room = constraints.maxHeight
                            val y = (screenMiddle.roundToPx() - p.height / 2)
                                .coerceIn(0, (room - p.height).coerceAtLeast(0))
                            layout(p.width, room) { p.placeRelative(0, y) }
                        }
                    )
                    .fillMaxWidth()
                    .shadowBleed(PANE_BLEED)
                    .then(
                        if (!fade) Modifier else Modifier
                            .graphicsLayer {
                                compositingStrategy = CompositingStrategy.Offscreen
                            }
                            .drawWithContent {
                                drawContent()
                                drawRect(
                                    brush = Brush.verticalGradient(
                                        0f to Color.Transparent,
                                        0.05f to Color.Black,
                                        0.94f to Color.Black,
                                        1f to Color.Transparent
                                    ),
                                    blendMode = BlendMode.DstIn
                                )
                            }
                    )
                    .verticalScroll(paneScroll)
                    .padding(vertical = PANE_BLEED),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (offline) OfflineCard()
                if (session.backend == Backend.PPSSPP) PspHintCard(pspAutomatic, players = others.size + 1)
                EmulatorHintCard(
                    session = session,
                    automationOn = automationOn,
                    players = others.size + 1,
                )
            }
            }
            }

            if (!panelLive) {
                // The first button that exists AND responds: a disabled one does not take focus.
                Spacer(Modifier.height(2.dp))
                if (session.backend.hasNetplay && !ps2Automatic) {
                    var showManualDialog by remember { mutableStateOf(false) }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AutoSetupNetplayButton(
                            session = session,
                            netplayDone = netplayDone,
                            netplayPrepared = netplayPrepared,
                            waitingForHost = waitingForHost,
                            onClick = onNetplayStep,
                            modifier = Modifier
                                .weight(0.9f)
                                .padEntry()
                        )

                        Spacer(Modifier.width(12.dp))

                        ManualSetupNetplayButton(
                            onClick = { showManualDialog = true },
                            modifier = Modifier.weight(0.1f)
                        )
                    }
                    if (showManualDialog) {
                        session.netplayPlan(profile.name)?.let { plan ->
                            SessionManualDialog(
                                plan = plan,
                                addressLabel = addressLabel,
                                emulatorName = session.backend.emulatorName,
                                onDismiss = { showManualDialog = false },
                            )
                        }
                    }
                }
                if (session.backend == Backend.PPSSPP && !pspAutomatic) {
                    PspSetupButton(
                        pspOpened = pspOpened,
                        onClick = onPspSetup,
                        modifier = if (session.backend.hasNetplay) Modifier
                        else Modifier.padEntry()
                    )
                }
                LaunchButton(
                    session = session,
                    netplayPrepared = netplayPrepared,
                    directPs2 = ps2Automatic,
                    waitingForHost = launchWaits(session, ps2Automatic, waitingForHost),
                    onClick = onLaunchStep,
                    modifier = if ((session.backend.hasNetplay && !ps2Automatic) ||
                        (session.backend == Backend.PPSSPP && !pspAutomatic)
                    ) Modifier
                    else Modifier.padEntry()
                )
            }
            }
            status?.let { StatusLine(it) }
        }
    }
}

private val PANE_BLEED = 32.dp

private fun Modifier.shadowBleed(bleed: Dp): Modifier = layout { measurable, constraints ->
    val extra = bleed.roundToPx()
    val placeable = measurable.measure(constraints.offset(vertical = 2 * extra))
    val height = (placeable.height - 2 * extra).coerceAtLeast(0)
    layout(placeable.width, height) { placeable.place(0, -extra) }
}
