package eu.emufii.app.update

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class LatestVersionTest {

    private fun version(fr: String?, en: String?) =
        LatestVersion(versionCode = 38, versionName = "1.11.7", url = null, notes = fr, notesEn = en)

    @Test
    fun `each language gets its own note`() {
        val v = version("Correctif Dolphin", "Dolphin fix")
        assertEquals("Correctif Dolphin", v.notesFor(Locale.FRENCH))
        assertEquals("Dolphin fix", v.notesFor(Locale.ENGLISH))
    }

    @Test
    fun `a locale we do not publish reads English, not French`() {
        val v = version("Correctif Dolphin", "Dolphin fix")
        assertEquals("Dolphin fix", v.notesFor(Locale.JAPANESE))
    }

    @Test
    fun `an old latest_json carries French only, and it is still shown`() {
        val v = version("Correctif Dolphin", null)
        assertEquals("Correctif Dolphin", v.notesFor(Locale.ENGLISH))
    }

    @Test
    fun `no note at all stays no note`() {
        assertEquals(null, version(null, null).notesFor(Locale.ENGLISH))
    }
}
