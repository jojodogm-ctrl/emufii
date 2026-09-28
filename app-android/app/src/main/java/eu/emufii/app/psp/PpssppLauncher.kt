package eu.emufii.app.psp

import android.content.Context
import android.content.Intent
import android.net.Uri
import eu.emufii.app.R
import eu.emufii.app.azahar.LaunchResult
import eu.emufii.app.library.Rom
import eu.emufii.app.session.RomRef
import eu.emufii.app.library.Console
import eu.emufii.app.library.EmulatorPick

/**
 * Belongs to nobody and never changes: the relay translates it towards the current
 * session's host, so the player never retypes an address. Must stay identical to
 * `relay/firewall.js` and to the coordinator.
 */
const val HOST_SENTINEL = "10.66.1.1"

/**
 * PPSSPP's interface is an opaque native surface, so accessibility cannot drive it; its
 * memory stick can live in a user-granted SAF tree instead. Emufii writes the per-game
 * INI just before opening the ROM, and PPSSPP reads it at boot even with its menu
 * already running.
 *
 * PPSSPP accepts `VIEW` with `content://`, verified on the device, so a SAF uri needs no
 * copy and no storage permission.
 */
class PpssppLauncher(private val context: Context) {

    private val config = PpssppConfigStore(context)

    fun installedPackage(): String? = EmulatorPick.packageFor(context, Console.PSP)

    fun openApp(): LaunchResult {
        val pkg = installedPackage() ?: return LaunchResult.NotInstalled
        val intent = context.packageManager.getLaunchIntentForPackage(pkg)
            ?: return LaunchResult.NotInstalled
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching {
            context.startActivity(intent)
            LaunchResult.Success
        }.getOrElse { LaunchResult.Error(it.message ?: "Unknown launch error") }
    }

    fun launchGame(romUri: Uri): LaunchResult {
        val pkg = installedPackage() ?: return LaunchResult.NotInstalled
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(romUri, "application/octet-stream")
            setPackage(pkg)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return runCatching {
            context.startActivity(intent)
            LaunchResult.Success
        }.getOrElse { LaunchResult.Error(it.message ?: "Unknown launch error") }
    }

    fun launchPrivateGame(rom: RomRef): LaunchResult {
        if (installedPackage() == null) return LaunchResult.NotInstalled
        return when (val prepared = config.applyPrivate(
            rom.productCode,
            rom.filename,
            rom.displayName,
        )) {
            PpssppConfigResult.Success,
            // The session screen shows its instructions whenever canApply() is false, so
            // an old manual setup or an unidentifiable compressed dump stays playable.
            PpssppConfigResult.NotConfigured,
            PpssppConfigResult.PermissionMissing,
            PpssppConfigResult.InvalidRoot,
            PpssppConfigResult.UnknownDiscId -> launchGame(rom.uri)
            else -> prepared.asLaunchError() ?: launchGame(rom.uri)
        }
    }

    fun launchPublicGame(rom: Rom): LaunchResult {
        if (installedPackage() == null) return LaunchResult.NotInstalled
        restorePublic(rom)?.let { return it }
        return launchGame(rom.uri)
    }

    /**
     * The one-tap online route: the game's own INI points at the public server, so
     * nothing is set by hand. Null when it cannot be written; the caller then falls back
     * on the manual screen.
     */
    fun launchAutoPublic(rom: Rom): LaunchResult? {
        if (installedPackage() == null) return LaunchResult.NotInstalled
        return when (val prepared = config.applyPublic(
            rom.productCode, rom.filename, rom.displayName,
            PspServers.chosenHost ?: PspServers.DEFAULT_HOST,
        )) {
            PpssppConfigResult.Success -> launchGame(rom.uri)
            PpssppConfigResult.NotConfigured,
            PpssppConfigResult.PermissionMissing,
            PpssppConfigResult.InvalidRoot,
            PpssppConfigResult.UnknownDiscId -> null
            else -> prepared.asLaunchError()
        }
    }

    /** Restore before showing settings, otherwise PPSSPP displays Emufii's private values. */
    fun openPublicSettings(rom: Rom): LaunchResult {
        if (installedPackage() == null) return LaunchResult.NotInstalled
        restorePublic(rom)?.let { return it }
        return openApp()
    }

    private fun restorePublic(rom: Rom): LaunchResult.Error? =
        config.restorePublic(rom.productCode, rom.filename, rom.displayName).asLaunchError()

    private fun PpssppConfigResult.asLaunchError(): LaunchResult.Error? = when (this) {
        PpssppConfigResult.Success -> null
        PpssppConfigResult.NotConfigured -> LaunchResult.Error(
            context.getString(R.string.ppsspp_config_not_configured),
        )
        PpssppConfigResult.PermissionMissing -> LaunchResult.Error(
            context.getString(R.string.ppsspp_config_permission_missing),
        )
        PpssppConfigResult.InvalidRoot -> LaunchResult.Error(
            context.getString(R.string.ppsspp_config_invalid_root),
        )
        PpssppConfigResult.UnknownDiscId -> LaunchResult.Error(
            context.getString(R.string.ppsspp_config_unknown_game),
        )
        PpssppConfigResult.ActiveOverrides -> LaunchResult.Error(
            context.getString(R.string.ppsspp_config_active_overrides),
        )
        is PpssppConfigResult.Failure -> LaunchResult.Error(detail)
    }
}

object PpssppPackage {
    val candidates = listOf("org.ppsspp.ppsspp", "org.ppsspp.ppssppgold")
}
