package eu.emufii.app.ds

import androidx.test.ext.junit.runners.AndroidJUnit4
import eu.emufii.app.BuildConfig
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DsSdk500VpsDeviceTest {
    @Test
    fun testVpsUsesPublicHttpsTrustedByAndroid() {
        // Refuse any production or caller-selected endpoint before opening a socket.
        assertEquals("https://vps-28c0ab53.vps.ovh.net:8443", BuildConfig.COORDINATOR_BASE_URL)
        val connection = URL(BuildConfig.COORDINATOR_BASE_URL + "/health").openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            assertEquals(200, connection.responseCode)
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            assertTrue(JSONObject(body).getBoolean("ok"))
        } finally {
            connection.disconnect()
        }
    }
}
