package eu.emufii.app.ui.screens.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
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
import eu.emufii.app.ui.components.RomArtwork
import eu.emufii.app.ui.components.padEntry

/**
 * State on the left, what is left to do on the right, every answer under the button that
 * produced it.
 * pourquoi : docs/decisions/session.md § Two panels, because stacked this screen does not fit
 */
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
        // No `verticalScroll`: a state pane that can hide its state is not doing its job.
        // pourquoi : docs/decisions/session.md § The state panel does not scroll, so it has to fit
        // pourquoi : docs/decisions/session.md § What the panel carries, the front screen gives back in space
        Column(
            modifier = Modifier.width(if (panelLive) 220.dp else 272.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Presence gives way, never the address: the weight reverses Compose's measuring
            // order to guarantee it.
            // pourquoi : docs/decisions/session.md § The state panel does not scroll, so it has to fit
            PresenceCard(
                youName = profile.name,
                others = others,
                isHost = session.role == Session.Role.HOST,
                live = !offline,
                scrollable = true,
                modifier = Modifier.weight(1f, fill = false)
            )
            // The panel already carries both address and port, unasked.
            // pourquoi : docs/decisions/session.md § What the rear panel carries, the front screen does not repeat
            if (!panelLive) {
                ConnectionCard(
                    hostIp = shownAddress,
                    addressLabel = addressLabel,
                    port = shownPort,
                    romName = session.rom?.displayName
                )
            }

            // Only once the panel has freed the room; without one there is no gap to fill.
            // pourquoi : docs/decisions/session.md § What the panel carries, the front screen gives back in space
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
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // The explanation gives way, never the buttons; the fade exists on one screen only.
            // pourquoi : docs/decisions/session.md § What the panel carries, the front screen gives back in space
            val fade = !panelLive
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
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
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (offline) OfflineCard()
                if (session.backend == Backend.PPSSPP) PspHintCard(pspAutomatic)
                EmulatorHintCard(
                    session = session,
                    automationOn = automationOn,
                )
            }

            // With the panel carrying the steps, the front screen does not redraw them.
            // pourquoi : docs/decisions/second-ecran.md § The panel takes the steps, because it is touch
            if (!panelLive) {
                // The first button that exists AND responds: a disabled one does not take focus.
                // pourquoi : docs/decisions/session.md § Down aims at the first button that answers
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
                    waitingForHost = ps2Automatic && waitingForHost,
                    onClick = onLaunchStep,
                    // Last resort: with no step above, this is the first button on the page.
                    modifier = if ((session.backend.hasNetplay && !ps2Automatic) ||
                        (session.backend == Backend.PPSSPP && !pspAutomatic)
                    ) Modifier
                    else Modifier.padEntry()
                )
            }
            status?.let { StatusLine(it) }
        }
    }
}
