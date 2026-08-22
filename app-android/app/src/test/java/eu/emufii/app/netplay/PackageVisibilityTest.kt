package eu.emufii.app.netplay

import eu.emufii.app.dolphin.DolphinTarget
import eu.emufii.app.ps2.Ps2Target
import eu.emufii.app.wfc.MelonDsPackage
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Every emulator Emufii talks to has to be declared in `<queries>`.
 *
 * Since Android 11, a package left out of that block is simply invisible:
 * `getPackageInfo` throws, and a launcher written against it concludes the
 * emulator isn't installed. That is exactly how Eden support failed its first
 * run on a device, the button did nothing at all, and the only trace was one
 * `AppsFilter … BLOCKED` line buried in logcat.
 *
 * The mistake is silent, cheap to make, and the manifest is the one place a
 * compiler will never look. So the test looks instead.
 */
class PackageVisibilityTest {

    private val manifest: String by lazy {
        // Unit tests run with the module directory as working directory.
        val file = File("src/main/AndroidManifest.xml")
        assertTrue("manifest not found at ${file.absolutePath}", file.exists())
        file.readText()
    }

    @Test
    fun `every netplay target is visible to the package manager`() {
        for (target in NetplayTarget.all) {
            for (pkg in target.packages) {
                assertTrue(
                    "$pkg is driven by the netplay service but missing from <queries>: " +
                        "it will read as \"not installed\" on any device running Android 11+",
                    manifest.contains("""android:name="$pkg"""")
                )
            }
        }
    }

    /**
     * The accessibility service holds its own package list, and it gets forgotten
     * just as surely as `<queries>`.
     *
     * An emulator missing from `packageNames` produces no events at all: the
     * service never wakes, the form never fills, and nothing says so, no error and
     * no log. The press simply looks like it does nothing. Seen for real on
     * 2026-08-10 on Eden's "Optimized" variants, which were nonetheless detected
     * correctly: the two lists were saying different things.
     */
    @Test
    fun `every netplay target reaches the accessibility service`() {
        val config = File("src/main/res/xml/azahar_netplay_service.xml")
        assertTrue("service config not found at ${config.absolutePath}", config.exists())
        val declared = config.readText()
            .substringAfter("android:packageNames=\"")
            .substringBefore('"')
            .split(',')
            .map { it.trim() }
            .toSet()

        for (target in NetplayTarget.all) {
            for (pkg in target.packages) {
                assertTrue(
                    "$pkg is driven by the netplay service but missing from its " +
                        "packageNames: the service will never receive an event from it, " +
                        "and the autofill will silently do nothing",
                    pkg in declared
                )
            }
        }
    }

    /**
     * Dolphin rides the same service through a driver of its own.
     *
     * It is deliberately absent from [NetplayTarget.all], its Compose screen
     * has no view ids, so it shares none of that walk, which means the two
     * tests above cannot see it. Left unchecked, the one mistake they exist to
     * catch would simply move to the backend that is easiest to forget.
     */
    @Test
    fun `dolphin is visible and reaches the accessibility service too`() {
        val declared = File("src/main/res/xml/azahar_netplay_service.xml").readText()
            .substringAfter("android:packageNames=\"")
            .substringBefore('"')
            .split(',')
            .map { it.trim() }
            .toSet()

        for (pkg in DolphinTarget.packages) {
            assertTrue(
                "$pkg is missing from <queries>: it will read as \"not installed\"",
                manifest.contains("""android:name="$pkg"""")
            )
            assertTrue(
                "$pkg is missing from the service's packageNames: the Dolphin " +
                    "driver will never receive an event, with no error and no log line",
                pkg in declared
            )
        }
    }

    /**
     * And ARMSX2, for the same reason, with one more trap on top.
     *
     * The PS2 is in neither [NetplayTarget.all] nor `DolphinTarget.packages`,
     * being a third shape of screen and a third driver, so none of the three
     * tests above sees it. It is precisely the "easiest backend to forget" the
     * previous comment warned about.
     */
    @Test
    fun `armsx2 is visible and reaches the accessibility service too`() {
        val declared = File("src/main/res/xml/azahar_netplay_service.xml").readText()
            .substringAfter("android:packageNames=\"")
            .substringBefore('"')
            .split(',')
            .map { it.trim() }
            .toSet()

        for (pkg in Ps2Target.packages) {
            assertTrue(
                "$pkg is missing from <queries>: it will read as \"not installed\"",
                manifest.contains("""android:name="$pkg"""")
            )
            assertTrue(
                "$pkg is missing from the service's packageNames: the PS2 driver " +
                    "will never receive an event, with no error and no log line",
                pkg in declared
            )
        }
    }

    /**
     * The original AetherSX2 must never enter these lists: it has no network
     * layer, and it is installed next to ARMSX2 on the Thor.
     */
    @Test
    fun `the original AetherSX2 is not driven`() {
        assertFalse(Ps2Target.owns("xyz.aethersx2.android"))
    }

    @Test
    fun `the other emulators Emufii launches are declared too`() {
        // Not covered by NetplayTarget, melonDS has no dialog to drive, but
        // it is launched, so it needs the same visibility.
        for (pkg in MelonDsPackage.candidates) {
            assertTrue(pkg, manifest.contains("""android:name="$pkg""""))
        }
    }
}
