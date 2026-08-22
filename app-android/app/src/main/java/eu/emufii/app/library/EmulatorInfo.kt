package eu.emufii.app.library

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import eu.emufii.app.dolphin.DolphinTarget
import eu.emufii.app.netplay.NetplayTarget
import eu.emufii.app.ps2.Ps2Target
import eu.emufii.app.psp.PpssppPackage
import eu.emufii.app.wfc.MelonDsPackage

/**
 * Which emulator plays a console, and whether it is actually on the device.
 *
 * Read from the system rather than written down here. Two things follow from
 * that, and both are the point:
 *
 * - The version is the one installed, so the screen showing it cannot go stale
 *   the way a table in this repo would. Emufii already learned that lesson the
 *   other way round, with an Eden variant it did not know about being reported
 *   as "not installed" while it sat on the home screen.
 * - The icon is the emulator's own, loaded from its package. Nothing is shipped
 *   here: these are other people's marks, and an app that copies them into its
 *   own resources is redistributing them. Asking the system means the player
 *   sees exactly the icon they tap on their own launcher.
 *
 * A console with no emulator installed is not an error and never blocks
 * anything: it is simply the answer to "what do I need in order to play this".
 */
data class EmulatorInfo(
    val console: Console,
    /** The emulator's own name, never translated: it is a product. */
    val name: String,
    /** The installed package, or null when none of its variants is there. */
    val installedPackage: String?,
    /** What the installed build calls itself, when it says. */
    val version: String?,
    val icon: ImageBitmap?
) {
    val installed: Boolean get() = installedPackage != null
}

/**
 * Every package a console's emulator may install itself under.
 *
 * Each of these lists is already the authority for its own backend, so this only
 * gathers them. Duplicating them here is how the accessibility service and
 * `<queries>` drifted apart once already, and a test now pins those two together
 * for the same reason.
 */
val Console.emulatorPackages: List<String>
    get() = when (this) {
        Console.THREE_DS -> NetplayTarget.AZAHAR.packages
        Console.SWITCH -> NetplayTarget.EDEN.packages
        Console.PSP -> PpssppPackage.candidates
        Console.DS -> MelonDsPackage.candidates
        Console.GAMECUBE, Console.WII -> DolphinTarget.packages
        Console.PS2 -> Ps2Target.packages
    }

/**
 * What the device actually holds for [console].
 *
 * The first installed variant wins, which matches how the launchers pick when a
 * player has several. Everything is wrapped: a package can be uninstalled
 * between the query and the read, and an icon can fail to load on a build with
 * an odd density. None of that is worth taking a screen down for, so an
 * unreadable emulator reads as an absent one.
 */
fun emulatorInfo(context: Context, console: Console): EmulatorInfo {
    val pm = context.packageManager
    val pkg = console.emulatorPackages.firstOrNull { candidate ->
        runCatching { pm.getPackageInfo(candidate, 0) }.isSuccess
    }
    val version = pkg?.let {
        runCatching { pm.getPackageInfo(it, 0).versionName }.getOrNull()
    }
    val icon = pkg?.let {
        runCatching {
            pm.getApplicationIcon(it).toBitmap(ICON_PX, ICON_PX).asImageBitmap()
        }.getOrNull()
    }
    return EmulatorInfo(
        console = console,
        name = console.backend.emulatorName,
        installedPackage = pkg,
        version = version,
        icon = icon
    )
}

/**
 * One entry per console, in the order the enum declares them.
 *
 * The GameCube and the Wii both answer "Dolphin", and they are deliberately left
 * as two lines rather than merged: the list is read to find out what plays *my*
 * games, and someone holding only Wii dumps should not have to infer that the
 * GameCube row covers them too.
 */
fun allEmulators(context: Context): List<EmulatorInfo> =
    Console.entries.map { emulatorInfo(context, it) }

/**
 * Big enough for the largest place it is drawn, which is the emulator list.
 *
 * Asked once, at load, rather than per composition: a launcher icon is often an
 * adaptive drawable, and rasterising one is not free.
 */
private const val ICON_PX = 144
