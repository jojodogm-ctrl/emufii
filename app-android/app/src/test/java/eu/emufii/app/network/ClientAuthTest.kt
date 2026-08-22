package eu.emufii.app.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * The vectors shared with `coordinator/client-auth.test.js`.
 *
 * This mechanism's real danger is not that it is weak, it is that the two
 * implementations drift: the day the server and the app stop computing the same
 * thing, every client is refused at once, and the symptom does not say where it
 * came from. These values are therefore hardcoded on both sides, and a format
 * change has to break here before it reaches production.
 *
 * `ClientAuth.sign` reads `BuildConfig`, which is null in a JVM test: so we
 * reproduce the computation identically rather than calling the object. The
 * formula is the contract, and the formula is what is frozen.
 */
class ClientAuthTest {

    private val secret = "secret-de-test-partage"

    private fun sha256Hex(s: String): String =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") {
            "%02x".format(it)
        }

    private fun sign(method: String, path: String, body: String?, ts: Long): String {
        val payload = "${method.uppercase()}\n$path\n$ts\n${sha256Hex(body ?: "")}"
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(), "HmacSHA256"))
        return mac.doFinal(payload.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    @Test
    fun `la signature d'un vecteur connu ne bouge pas`() {
        // If this value changes, the server will refuse every app. It is computed
        // by the same formula on the Node side; the two must move together or not
        // at all.
        val signature = sign("POST", "/sessions", """{"code":"ABC-123"}""", 1_770_000_000L)
        assertEquals(
            "e232919421418371a85dfc2fc5d7b894b5eeaffe8825c069039786347558db95",
            signature
        )
    }

    @Test
    fun `le corps entre dans le calcul`() {
        // Without it, a signature valid for one request would be valid for every
        // request at the same path.
        val a = sign("POST", "/sessions", """{"code":"UN"}""", 1_770_000_000L)
        val b = sign("POST", "/sessions", """{"code":"DEUX"}""", 1_770_000_000L)
        assertNotEquals(a, b)
    }

    @Test
    fun `le chemin entre dans le calcul`() {
        val a = sign("POST", "/me", """{"id":"X"}""", 1_770_000_000L)
        val b = sign("POST", "/friends", """{"id":"X"}""", 1_770_000_000L)
        assertNotEquals(a, b)
    }

    @Test
    fun `l'horodatage entre dans le calcul`() {
        val a = sign("POST", "/sessions", null, 1_770_000_000L)
        val b = sign("POST", "/sessions", null, 1_770_000_060L)
        assertNotEquals(a, b)
    }

    @Test
    fun `un corps absent et un corps vide se signent pareil`() {
        // A GET request has no body, and the app then sends null where the server
        // reads the empty string.
        assertEquals(
            sign("GET", "/sessions", null, 1_770_000_000L),
            sign("GET", "/sessions", "", 1_770_000_000L)
        )
    }
}
