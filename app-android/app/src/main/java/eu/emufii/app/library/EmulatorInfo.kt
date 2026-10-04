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

data class EmulatorInfo(
    val console: Console,
    val name: String,
    val installedPackage: String?,
    val version: String?,
    val icon: ImageBitmap?,
    val variants: List<EmulatorVariant> = emptyList(),
    val chosenExplicitly: Boolean = false
) {
    val installed: Boolean get() = installedPackage != null

    val variant: EmulatorVariant? get() = variants.firstOrNull { it.packageName == installedPackage }
}

val Console.emulatorPackages: List<String>
    get() = when (this) {
        Console.THREE_DS -> NetplayTarget.AZAHAR.packages
        Console.SWITCH -> NetplayTarget.EDEN.packages
        Console.PSP -> PpssppPackage.candidates
        Console.DS -> MelonDsPackage.candidates
        Console.GAMECUBE, Console.WII -> DolphinTarget.packages
        Console.PS2 -> Ps2Target.packages
    }

fun emulatorInfo(context: Context, console: Console): EmulatorInfo {
    val pm = context.packageManager
    val variants = EmulatorPick.variants(context, console)
    val pkg = EmulatorPick.packageFor(context, console)
    val version = pkg?.let { packageVersion(context, it) }
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
        icon = icon,
        variants = variants,
        chosenExplicitly = EmulatorPick.chosen(context, console) != null
    )
}

fun emulatorVersion(context: Context, console: Console): String? =
    EmulatorPick.packageFor(context, console)?.let { packageVersion(context, it) }

private fun packageVersion(context: Context, pkg: String): String? =
    runCatching { context.packageManager.getPackageInfo(pkg, 0).versionName }.getOrNull()

fun allEmulators(context: Context): List<EmulatorInfo> =
    Console.entries.map { emulatorInfo(context, it) }

private const val ICON_PX = 144
