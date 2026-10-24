package eu.emufii.app.nativeds

import com.sun.net.httpserver.HttpServer
import eu.emufii.app.network.CoordinatorClient
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList

/** Actual modified CoordinatorClient and HTTP parsing; no DS/Android runtime is launched. */
class NativeDsDiscoveryTest {
    private fun native() = """{"code":"ABC-234","transport":"ds-homebrew","players":2,"ready":false,"host_ready":false,
      "host_name":"Mario","subnet":null,"host_ip":null,"port":null,"room":null,
      "native":{"protocol":"emufii-nsmb-native-v1","gameId":"nsmb-mvlo-dsi","engineVersion":"nsmb-native-2",
      "contentSha256":"${"a".repeat(64)}","maxPlayers":10,"roomCode":"BCDF"},
      "native_state":{"phase":"playing","generation":7,"players":2}}"""
    private fun serve(body: String, action: (CoordinatorClient, List<String>) -> Unit) {
        val seen = CopyOnWriteArrayList<String>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { request ->
            seen.add("${request.requestMethod} ${request.requestURI.path} ${request.requestHeaders.getFirst(NativeDsRoom.DISCOVERY_HEADER)}")
            val bytes = body.toByteArray(Charsets.UTF_8)
            request.responseHeaders.add("Content-Type", "application/json")
            request.sendResponseHeaders(200, bytes.size.toLong()); request.responseBody.use { it.write(bytes) }
        }
        server.start()
        try { action(CoordinatorClient("http://127.0.0.1:${server.address.port}"), seen) }
        finally { server.stop(0) }
    }
    @Test fun directoryOptInParsesNativeWithoutBreakingLegacyRows() = runBlocking {
        serve("""{"sessions":[${native()}, {"code":"BCD-345","transport":"wireguard","players":1,"ready":true}]}""") { client, seen ->
            val result = runBlocking { client.listSessions() }.getOrThrow()
            assertEquals(2, result.size); assertNotNull(result[0].native); assertNull(result[0].ds)
            assertNull(result[1].native); assertNull(result[1].ds)
            assertEquals(listOf("GET /sessions 1"), seen)
        }
    }
    @Test fun nativeCodeLookupIsReadOnlyTypedCompanionWithNoLegacyAddress() = runBlocking {
        serve(native()) { client, seen ->
            val result = runBlocking { client.getSession("ABC-234") }.getOrThrow()
            assertEquals("BCDF", result.native!!.roomCode); assertEquals(NativeDsPhase.PLAYING, result.native!!.phase)
            assertNull(result.ds); assertNull(result.hostIp); assertNull(result.room); assertEquals("", result.subnet)
            assertEquals(listOf("GET /sessions/ABC-234 1"), seen)
        }
    }
    @Test fun malformedNativeReplyFailsInsteadOfProducingWireGuardOrGoldSpec() = runBlocking {
        val broken = JSONObject(native()).put("ds", JSONObject()).toString()
        serve(broken) { client, _ -> assertTrue(runBlocking { client.getSession("ABC-234") }.isFailure) }
        val unknown = JSONObject(native()).put("transport", "unsupported-new-runtime").toString()
        serve(unknown) { client, _ -> assertTrue(runBlocking { client.getSession("ABC-234") }.isFailure) }
    }
}
