package eu.emufii.app.ui.screens.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.emufii.app.R
import eu.emufii.app.dolphin.DolphinTarget
import eu.emufii.app.library.Backend
import eu.emufii.app.library.Console
import eu.emufii.app.library.EmulatorInfo
import eu.emufii.app.library.emulatorInfo
import eu.emufii.app.ps2.Ps2GameSettings
import eu.emufii.app.ps2.Ps2Target
import eu.emufii.app.psp.HOST_SENTINEL
import eu.emufii.app.session.Session
import eu.emufii.app.ui.components.FactTile
import eu.emufii.app.ui.components.FitColumn
import eu.emufii.app.ui.components.LocalSheetMaxHeight
import eu.emufii.app.ui.components.Optional
import androidx.compose.ui.unit.Dp
import eu.emufii.app.ui.components.GhostButton
import eu.emufii.app.ui.components.SectionHeader
import eu.emufii.app.ui.components.SheetHeader
import eu.emufii.app.ui.components.SheetLabel
import eu.emufii.app.ui.components.SheetStep
import eu.emufii.app.ui.components.SheetWarning
import eu.emufii.app.ui.components.SoftCard
import eu.emufii.app.ui.components.accented
import eu.emufii.app.ui.copyToClipboard
import eu.emufii.app.ui.theme.Coral
import eu.emufii.app.ui.theme.LocalEmufiiDarkTheme
import eu.emufii.app.ui.theme.Teal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun EmulatorHintCard(
    session: Session,
    automationOn: Boolean,
    players: Int = 0,
) {
    if (session.rom == null) {
        MissingRomCard()
        return
    }
    val isHost = session.role == Session.Role.HOST
    when (session.backend) {
        Backend.AZAHAR -> AzaharHintCard(
            console = session.console,
            automationOn = automationOn,
            isHost = isHost,
            address = "${session.hostIp}:${session.port}",
            players = players
        )
        Backend.EDEN -> EdenHintCard(
            console = session.console,
            automationOn = automationOn,
            // With a VPS room the host joins too; Create would open a second, empty room.
            isHost = isHost && session.room == null,
            shownHost = isHost,
            address = "${session.room?.host ?: session.hostIp}:" +
                (session.room?.port?.toString() ?: session.port),
            players = players,
            onServer = session.room != null
        )

        Backend.DOLPHIN -> DolphinHintCard(
            console = session.console,
            automationOn = automationOn,
            isHost = isHost,
            address = "${session.hostIp}:${DolphinTarget.DEFAULT_PORT}",
            players = players
        )

        Backend.ARMSX2 -> Ps2HintCard(
            console = session.console,
            automationOn = session.rom.let {
                Ps2GameSettings.canConfigure(LocalContext.current, it)
            },
            isHost = isHost,
            address = "${session.hostIp}:${Ps2Target.DEFAULT_PORT}",
            players = players
        )

        Backend.PPSSPP -> Unit
        // Nothing to type, the address travels in the launch; what is left is the order.
        Backend.MELONDS -> DsWirelessHintCard(isHost = isHost, players = players)
        Backend.NONE -> UnsupportedHintCard(session.console?.label)
    }
}

private data class SheetInks(val accent: Color, val alarm: Color)

@Composable
private fun sheetInks(): SheetInks {
    val dark = LocalEmufiiDarkTheme.current
    return SheetInks(
        accent = if (dark) Teal.darkBright else Teal.deep,
        alarm = if (dark) Coral.darkBright else Coral.deep,
    )
}

@Composable
private fun HintSheet(
    console: Console?,
    title: String,
    subtitle: String? = null,
    content: @Composable (SheetInks) -> Unit,
) {
    val context = LocalContext.current
    val inks = sheetInks()
    // Off the main thread: an icon decode is not free.
    val emulator by produceState<EmulatorInfo?>(null, console) {
        value = console?.let {
            withContext(Dispatchers.IO) { runCatching { emulatorInfo(context, it) }.getOrNull() }
        }
    }
    val maxHeight = LocalSheetMaxHeight.current
    SoftCard {
        FitColumn(
            maxHeight = if (maxHeight == Dp.Unspecified) maxHeight else maxHeight - SheetPadV * 2,
            spacing = 12.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = SheetPadV),
        ) {
            val installed = emulator?.installed
            SheetHeader(
                icon = emulator?.icon,
                fallbackLetter = title.take(1),
                title = title,
                subtitle = if (installed == false) stringResource(R.string.console_sheet_not_installed)
                else subtitle ?: emulator?.version,
                accent = inks.accent,
                subtitleInk = if (installed == false) inks.alarm else inks.accent,
                iconSize = 44.dp,
            )
            content(inks)
        }
    }
}

private val SheetPadV = 16.dp

private fun maxPlayers(backend: Backend): Int = when (backend) {
    Backend.MELONDS -> eu.emufii.app.wfc.MelonDsPackage.MAX_NETPLAY_PLAYERS
    Backend.DOLPHIN -> 4
    else -> 8
}

@Composable
private fun PlayersTile(players: Int, max: Int, modifier: Modifier) {
    FactTile(
        label = stringResource(R.string.hint_sheet_players),
        value = stringResource(R.string.hint_ds_players, players, max),
        ink = MaterialTheme.colorScheme.onSurface,
        modifier = modifier
    )
}

@Composable
private fun RoleSetupTiles(
    isHost: Boolean,
    automatic: Boolean,
    inks: SheetInks,
    address: String? = null,
    players: Int = 0,
    maxPlayers: Int = 0,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        FactTile(
            label = stringResource(R.string.hint_sheet_role),
            value = stringResource(if (isHost) R.string.hint_sheet_host else R.string.hint_sheet_guest),
            ink = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        FactTile(
            label = stringResource(R.string.hint_sheet_setup),
            value = stringResource(if (automatic) R.string.hint_sheet_auto else R.string.hint_sheet_manual),
            ink = inks.accent,
            modifier = Modifier.weight(1f)
        )
        if (!automatic && address != null) {
            FactTile(
                label = stringResource(R.string.hint_sheet_address),
                value = address,
                ink = inks.accent,
                modifier = Modifier.weight(1.4f)
            )
        }
        if (players > 0 && maxPlayers > 0) PlayersTile(players, maxPlayers, Modifier.weight(1f))
    }
}

@Composable
private fun Tip(text: String, inks: SheetInks) {
    Text(
        accented(text, inks.accent),
        style = MaterialTheme.typography.bodyMedium,
        lineHeight = 20.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun Lead(text: String, inks: SheetInks) {
    Text(
        accented(text, inks.accent),
        style = MaterialTheme.typography.bodyLarge,
        lineHeight = 22.sp,
        color = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
private fun AzaharHintCard(
    console: Console?,
    automationOn: Boolean,
    isHost: Boolean,
    address: String,
    players: Int,
) {
    HintSheet(console, "Azahar") { inks ->
        RoleSetupTiles(isHost, automationOn, inks, address, players = players, maxPlayers = maxPlayers(Backend.AZAHAR))
        if (!automationOn) Lead(stringResource(R.string.hint_azahar_manual), inks)
        // Loud on both paths: getting it wrong produces an error that accuses the address.
        SheetWarning(stringResource(R.string.hint_azahar_username), inks.alarm)
        SheetWarning(stringResource(R.string.hint_same_version), inks.alarm)
        Optional { SheetWarning(stringResource(R.string.brief_3ds_warning), inks.alarm) }
        Optional { Tip(stringResource(R.string.hint_azahar_blame), inks) }
    }
}

@Composable
private fun EdenHintCard(
    console: Console?,
    automationOn: Boolean,
    isHost: Boolean,
    shownHost: Boolean,
    address: String,
    players: Int,
    onServer: Boolean,
) {
    HintSheet(console, "Eden") { inks ->
        RoleSetupTiles(shownHost, automationOn, inks, address, players = players, maxPlayers = maxPlayers(Backend.EDEN))
        Lead(
            stringResource(if (isHost) R.string.hint_eden_host else R.string.hint_eden_guest),
            inks
        )
        // A differing game version lets the room form, then the game never starts.
        SheetWarning(stringResource(R.string.hint_same_version), inks.alarm)
        Optional { SheetWarning(stringResource(R.string.hint_eden_nickname), inks.alarm) }
        if (onServer) Optional { Tip(stringResource(R.string.hint_eden_server), inks) }
    }
}

@Composable
private fun DolphinHintCard(
    console: Console?,
    automationOn: Boolean,
    isHost: Boolean,
    address: String,
    players: Int,
) {
    HintSheet(console, "Dolphin") { inks ->
        RoleSetupTiles(isHost, automationOn, inks, address, players = players, maxPlayers = maxPlayers(Backend.DOLPHIN))
        if (!automationOn) Lead(stringResource(R.string.hint_dolphin_manual), inks)
        Lead(
            stringResource(if (isHost) R.string.hint_dolphin_host else R.string.hint_dolphin_guest),
            inks
        )
        SheetWarning(stringResource(R.string.hint_dolphin_same_dump), inks.alarm)
        // Mismatched saves desync silently.
        SheetWarning(stringResource(R.string.hint_dolphin_same_save), inks.alarm)
        Optional { Tip(stringResource(R.string.hint_dolphin_together), inks) }
    }
}

@Composable
private fun Ps2HintCard(
    console: Console?,
    automationOn: Boolean,
    isHost: Boolean,
    address: String,
    players: Int,
) {
    HintSheet(console, "ARMSX2") { inks ->
        RoleSetupTiles(isHost, automationOn, inks, address, players = players, maxPlayers = maxPlayers(Backend.ARMSX2))
        if (!automationOn) Lead(stringResource(R.string.hint_ps2_manual), inks)
        Lead(stringResource(if (isHost) R.string.hint_ps2_host else R.string.hint_ps2_guest), inks)
        SheetWarning(stringResource(R.string.hint_ps2_lan_only), inks.alarm)
        // Per ARMSX2: with the network adapter attached some games stop responding to the pad.
        SheetWarning(stringResource(R.string.hint_ps2_pad), inks.alarm)
        Optional { Tip(stringResource(R.string.hint_ps2_online), inks) }
    }
}

@Composable
private fun DsWirelessHintCard(isHost: Boolean, players: Int) {
    HintSheet(Console.DS, stringResource(R.string.hint_ds_title), "WatermelonDS") { inks ->
        RoleSetupTiles(
            isHost, automatic = true, inks = inks,
            players = players, maxPlayers = maxPlayers(Backend.MELONDS)
        )
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SheetLabel(stringResource(R.string.hint_sheet_steps))
            val steps = if (isHost) {
                listOf(R.string.hint_ds_host_1, R.string.hint_ds_host_2)
            } else {
                listOf(R.string.hint_ds_guest_1, R.string.hint_ds_guest_2)
            }
            steps.forEachIndexed { i, res -> SheetStep(i + 1, stringResource(res), inks.accent) }
        }
        SheetWarning(stringResource(R.string.hint_ds_same_rom), inks.alarm)
    }
}

@Composable
private fun MissingRomCard() {
    SoftCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SectionHeader(stringResource(R.string.hint_missing_rom_title))
            Text(
                stringResource(R.string.hint_missing_rom_body),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
internal fun OfflineCard() {
    SoftCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SectionHeader(stringResource(R.string.session_offline_title))
            Text(
                stringResource(R.string.session_offline_body),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
internal fun PspHintCard(automatic: Boolean, players: Int = 0) {
    val context = LocalContext.current
    LaunchedEffect(automatic) {
        if (!automatic) copyToClipboard(context, "Emufii", HOST_SENTINEL)
    }
    HintSheet(Console.PSP, "PPSSPP") { inks ->
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FactTile(
                label = stringResource(R.string.hint_sheet_setup),
                value = stringResource(
                    if (automatic) R.string.hint_sheet_auto else R.string.hint_sheet_manual
                ),
                ink = inks.accent,
                modifier = Modifier.weight(1f)
            )
            FactTile(
                label = stringResource(R.string.hint_sheet_address),
                value = HOST_SENTINEL,
                ink = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            if (players > 0) PlayersTile(players, maxPlayers(Backend.PPSSPP), Modifier.weight(1f))
        }
        if (automatic) {
            Text(
                stringResource(R.string.hint_psp_automatic_ready),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = good()
            )
        } else {
            // Once, in PPSSPP's own menus: it draws its own interface and cannot be driven.
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    stringResource(R.string.hint_psp_step1),
                    stringResource(R.string.hint_psp_step2, HOST_SENTINEL),
                    stringResource(R.string.hint_psp_step2b),
                    stringResource(R.string.hint_psp_step3),
                ).forEachIndexed { i, step -> SheetStep(i + 1, step, inks.accent) }
            }
            GhostButton(
                label = stringResource(R.string.hint_psp_copy),
                onClick = { copyToClipboard(context, "Emufii", HOST_SENTINEL) }
            )
        }
        SheetWarning(stringResource(R.string.hint_psp_exit_before_switch), inks.alarm)
        // The one setting that changes how the game feels, and it is not in the emulator.
        Optional { Tip(stringResource(R.string.hint_psp_step4), inks) }
        Optional {
            Text(
                stringResource(R.string.hint_psp_wifi),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Optional { Tip(stringResource(R.string.hint_psp_wifi_why), inks) }
    }
}

@Composable
private fun WfcNotASessionCard() {
    SoftCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SectionHeader(stringResource(R.string.hint_wfc_title))
            Text(
                stringResource(R.string.hint_wfc_body),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun UnsupportedHintCard(consoleLabel: String?) {
    SoftCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SectionHeader(consoleLabel ?: stringResource(R.string.hint_unknown_console))
            Text(
                stringResource(R.string.hint_unsupported_body),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
