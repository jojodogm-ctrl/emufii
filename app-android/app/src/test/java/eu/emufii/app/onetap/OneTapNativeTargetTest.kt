package eu.emufii.app.onetap

import eu.emufii.app.BuildConfig

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class OneTapNativeTargetTest {
    @Test
    fun `attested HLE target is visible to Android package lookup`() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        val publicPackage = "com.mpds.online.singleowner.dev"
        val diagnosticPackage = "com.mpds.online.singleowner.emufii.metrics"
        assertTrue(OneTapEntry.NATIVE_PACKAGE in setOf(publicPackage, diagnosticPackage))
        if (BuildConfig.BUILD_TYPE != "preview") assertEquals(publicPackage, OneTapEntry.NATIVE_PACKAGE)
        assertEquals("com.mpds.online.MainActivity", OneTapEntry.NATIVE_ACTIVITY)
        assertTrue(manifest.contains("android:name=\"$publicPackage\""))
        assertTrue(manifest.contains("android:name=\"$diagnosticPackage\""))
    }
}
