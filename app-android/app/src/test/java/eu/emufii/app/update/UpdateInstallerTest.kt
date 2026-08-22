package eu.emufii.app.update

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The first of the three locks: where the app agrees to download from.
 *
 * The rest, signature, version, system dialog, needs a real device and a real
 * APK. This one does not, and it is precisely the one that decides whether a
 * compromised `latest.json` can send the app to fetch a binary elsewhere.
 */
class UpdateInstallerTest {

    private val base = "https://coordinator.example"

    @Test
    fun `sans url publiee, on tire du coordinator`() {
        assertEquals("$base/download", UpdateInstaller.downloadUrl(null, base))
        assertEquals("$base/download", UpdateInstaller.downloadUrl("", base))
    }

    @Test
    fun `une url du meme hote est suivie`() {
        val published = "$base/releases/emufii-1.9.3.apk"

        assertEquals(published, UpdateInstaller.downloadUrl(published, base))
    }

    @Test
    fun `une url d'ailleurs n'est pas suivie`() {
        // The case that matters: someone controls the published JSON but not the
        // server. The app falls back on its own coordinator rather than going to
        // fetch whatever it is being pointed at.
        assertEquals(
            "$base/download",
            UpdateInstaller.downloadUrl("https://ailleurs.example/emufii.apk", base)
        )
    }

    @Test
    fun `le meme hote en clair n'est pas suivi`() {
        assertEquals(
            "$base/download",
            UpdateInstaller.downloadUrl("http://coordinator.example/emufii.apk", base)
        )
    }

    @Test
    fun `une url illisible retombe sur le coordinator`() {
        assertEquals("$base/download", UpdateInstaller.downloadUrl("pas une url", base))
    }
}
