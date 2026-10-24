package eu.emufii.app.onetap

import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.coroutines.cancellation.CancellationException

class OneTapClientTest {
    private val token = "ab".repeat(32)
    private fun session(linked: Boolean = false): JSONObject = JSONObject()
        .put("code", "ABC-234").put("game", "mkds").put("players", 4)
        .put("phase", if (linked) "linked" else "awaiting-one-tap-host")
        .put("expiresAt", 1_800_000_000_000L).put("hostReady", false)
        .put("oneTap", if (linked) JSONObject().put("code", "AB2D")
            .put("browserUrl", OneTapEntry.browserRoom("AB2D"))
            .put("nativeUrl", OneTapEntry.publicInvite("AB2D"))
            .put("nativeUri", OneTapEntry.nativeRoom("AB2D")) else JSONObject.NULL)

    private fun created(value: JSONObject = session(), ownerToken: String = token): String =
        JSONObject().put("ownerToken", ownerToken).put("session", value).toString()

    @Test
    fun `create registers an Emufii room before any native room or ready game`() = runBlocking {
        lateinit var sent: OneTapHttpRequest
        val client = OneTapClient("https://adapter.example", OneTapTransport {
            sent = it
            OneTapHttpResponse(201, created())
        })
        val result = client.createSession(" abc-234 ", OneTapGame.MARIO_KART_DS, "LePast",
            romTitleId = "AMCE", romTitle = "Mario Kart DS").getOrThrow()
        assertEquals("https://adapter.example/v1/one-tap/sessions", sent.url)
        assertEquals("POST", sent.method)
        assertNull(sent.bearer)
        val body = JSONObject(sent.body!!)
        assertEquals(setOf("code", "game", "hostName", "romTitleId", "romTitle"), body.keys().asSequence().toSet())
        assertEquals("ABC-234", body.getString("code"))
        assertEquals("LePast", body.getString("hostName"))
        assertEquals(OneTapPhase.AWAITING_ONE_TAP_HOST, result.session.phase)
        assertNull(result.session.oneTap)
        assertFalse(result.session.hostReady)
        assertFalse(result.toString().contains(token))
    }

    @Test
    fun `linked room remains pending and uses canonical production URLs`() = runBlocking {
        val client = OneTapClient("https://adapter.example", OneTapTransport {
            assertEquals("GET", it.method)
            assertNull(it.bearer)
            OneTapHttpResponse(200, session(true).toString())
        })
        val result = client.getSession("ABC-234").getOrThrow()
        assertEquals(OneTapPhase.LINKED, result.phase)
        assertEquals("AB2D", result.oneTap!!.code)
        assertEquals("https://party.lepast.fr/play/#room/AB2D", result.oneTap.browserUrl)
        assertFalse(result.hostReady)
    }

    @Test
    fun `delete sends the room bound owner capability and never logs it`() = runBlocking {
        val requests = mutableListOf<OneTapHttpRequest>()
        val client = OneTapClient("https://adapter.example", OneTapTransport {
            requests.add(it)
            if (it.method == "POST") OneTapHttpResponse(201, created()) else OneTapHttpResponse(204, "")
        })
        val owner = client.createSession("ABC-234", OneTapGame.MARIO_KART_DS, "LePast").getOrThrow().owner
        client.deleteSession(owner).getOrThrow()
        val sent = requests.last()
        assertEquals("DELETE", sent.method)
        assertEquals("https://adapter.example/v1/one-tap/sessions/ABC-234", sent.url)
        assertEquals(token, sent.bearer)
        assertNull(sent.body)
        assertFalse(owner.toString().contains(token))
        assertFalse(sent.toString().contains(token))
        assertFalse(OneTapHttpResponse(200, created()).toString().contains(token))
    }

    @Test
    fun `foreign links forged readiness and inconsistent rooms reject before launching`() = runBlocking {
        val malformed = listOf(
            session(true).apply { getJSONObject("oneTap").put("browserUrl", "https://evil.invalid/play/#room/AB2D") },
            session(true).apply { getJSONObject("oneTap").put("nativeUri", "nemul://join/AB2D") },
            session(true).put("hostReady", true), session(true).put("players", 3),
            session(true).put("players", "4"), session(true).put("players", 4.5),
            session(true).put("expiresAt", "1800000000000"), session(true).put("expiresAt", -1),
            session(true).put("phase", "running"), session(true).put("phase", "awaiting-one-tap-host"),
            session(true).put("oneTap", JSONObject.NULL), session(true).put("code", "ABD-234"),
            session(true).put("ownerToken", token), session(true).put("game", "experimental"),
        )
        for (value in malformed) {
            val client = OneTapClient("https://adapter.example", OneTapTransport {
                OneTapHttpResponse(200, value.toString())
            })
            assertTrue(value.toString(), client.getSession("ABC-234").exceptionOrNull() is OneTapClientError.InvalidResponse)
        }
    }

    @Test
    fun `creation cannot adopt another game or an already linked host`() = runBlocking {
        for (value in listOf(session().put("game", "mpds"), session().put("code", "ABD-234"), session(true))) {
            val client = OneTapClient("https://adapter.example", OneTapTransport { OneTapHttpResponse(201, created(value)) })
            assertTrue(client.createSession("ABC-234", OneTapGame.MARIO_KART_DS, "LePast")
                .exceptionOrNull() is OneTapClientError.InvalidResponse)
        }
        for (invalidToken in listOf("", "bad", "ab".repeat(31), "AB".repeat(32), token + "\n")) {
            val client = OneTapClient("https://adapter.example", OneTapTransport {
                OneTapHttpResponse(201, created(ownerToken = invalidToken))
            })
            assertTrue(client.createSession("ABC-234", OneTapGame.MARIO_KART_DS, "LePast")
                .exceptionOrNull() is OneTapClientError.InvalidResponse)
        }
    }

    @Test
    fun `invalid codes and profile names never send requests`() = runBlocking {
        var count = 0
        val client = OneTapClient("https://adapter.example", OneTapTransport { count++; error("unexpected") })
        for (code in listOf("AB2D", "ABC234", "ABI-234", "aß-234", "ABC-014", "ABC-234/leave", "ABC-234?token=x")) {
            assertTrue(client.getSession(code).isFailure)
        }
        for (name in listOf("", " LePast", "LePast ", "12345678901", "Le\nPast", "Le\u202ePast")) {
            assertTrue(client.createSession("ABC-234", OneTapGame.MARIO_KART_DS, name).isFailure)
        }
        assertEquals(0, count)
    }

    @Test
    fun `HTTP errors redact remote bodies and oversized responses reject`() = runBlocking {
        val denied = OneTapClient("https://adapter.example", OneTapTransport { OneTapHttpResponse(403, token) })
            .getSession("ABC-234").exceptionOrNull()!!
        assertTrue(denied is OneTapClientError.Http)
        assertFalse(denied.toString().contains(token))
        val oversized = OneTapClient("https://adapter.example", OneTapTransport {
            OneTapHttpResponse(200, " ".repeat(OneTapClient.MAX_RESPONSE_BYTES + 1))
        }).getSession("ABC-234").exceptionOrNull()
        assertTrue(oversized is OneTapClientError.InvalidResponse)
    }

    @Test
    fun `adapter origin requires explicit HTTPS without credentials or override paths`() {
        for (origin in listOf("http://adapter.example", "https://user:secret@adapter.example",
            "https://adapter.example/", "https://adapter.example:443", "https://adapter.example?secret=x",
            "https://adapter.example#secret", "https://adapter.example/v1", "")) {
            assertTrue(origin, runCatching { OneTapClient(origin) }.exceptionOrNull() is IllegalArgumentException)
        }
    }

    @Test
    fun `coroutine cancellation is not converted to a recoverable HTTP error`() = runBlocking {
        val client = OneTapClient("https://adapter.example", OneTapTransport { throw CancellationException("cancelled") })
        assertTrue(runCatching { client.getSession("ABC-234") }.exceptionOrNull() is CancellationException)
    }
}
