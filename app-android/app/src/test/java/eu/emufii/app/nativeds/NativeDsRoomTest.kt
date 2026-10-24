package eu.emufii.app.nativeds

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class NativeDsRoomTest {
    private fun session() = JSONObject("""{"code":"ABC-234","transport":"ds-homebrew","players":2,"ready":false,
      "native":{"protocol":"emufii-nsmb-native-v1","gameId":"nsmb-mvlo-dsi","engineVersion":"nsmb-native-2",
      "contentSha256":"${"a".repeat(64)}","maxPlayers":10,"roomCode":"BCDF"},
      "native_state":{"phase":"lobby","generation":0,"players":2}}""")
    @Test fun physicalCompanionHasSeparateTypeAndNoPhoneRuntimeOrTunnel() {
        val room = NativeDsRoom.fromSession(session())!!
        assertEquals(NativeDsRoute.PHYSICAL_CONSOLE_COMPANION, room.route)
        assertFalse(room.launchesPhoneRuntime); assertFalse(room.requiresWireGuard)
        assertEquals("BCDF", room.roomCode); assertEquals(2, room.players); assertEquals(10, room.maxPlayers)
    }
    @Test fun allStatesAndUnsignedGenerationArePreserved() {
        for (phase in NativeDsPhase.entries) {
            val input = session(); input.getJSONObject("native_state").put("phase", phase.wire).put("generation", 0xffff_ffffL)
            val room = NativeDsRoom.fromSession(input)!!; assertEquals(phase, room.phase); assertEquals(0xffff_ffffL, room.generation)
        }
    }
    @Test fun nonNativeRoomsRemainNonNativeAndUnknownOrMixedDescriptorsFail() {
        for (transport in listOf("wireguard", "ds-hle", "ds-local"))
            assertNull(NativeDsRoom.fromSession(JSONObject().put("transport", transport)))
        assertNull(NativeDsRoom.fromSession(JSONObject()))
        for (input in listOf(session().put("transport", "wireguard"), session().put("transport", "ds-hle"),
            session().put("transport", "future-unknown"), session().put("ds", JSONObject()),
            session().put("host_ip", "10.67.1.2"), session().put("host_ready", true)))
            assertThrows(IllegalArgumentException::class.java) { NativeDsRoom.fromSession(input) }
    }
    @Test fun missingMalformedAndCoercedNativeFieldsCannotFallBackToLegacy() {
        val invalid = listOf<(JSONObject) -> Unit>(
            { it.remove("native") }, { it.remove("native_state") },
            { it.getJSONObject("native").put("protocol", "other") },
            { it.getJSONObject("native").put("gameId", "nsmb") },
            { it.getJSONObject("native").put("maxPlayers", "10") },
            { it.getJSONObject("native").put("maxPlayers", 10.0) },
            { it.getJSONObject("native").put("maxPlayers", 11) },
            { it.getJSONObject("native").put("roomCode", "ABCD") },
            { it.getJSONObject("native").put("contentSha256", "not-a-hash") },
            { it.getJSONObject("native").put("endpoint", "https://unexpected.invalid") },
            { it.getJSONObject("native_state").put("generation", -1) },
            { it.getJSONObject("native_state").put("generation", 0x1_0000_0000L) },
            { it.getJSONObject("native_state").put("players", 11) },
            { it.getJSONObject("native_state").put("phase", "fake-ready") },
            { it.put("players", 1) }
        )
        for (change in invalid) {
            val input = session(); change(input)
            assertThrows(RuntimeException::class.java) { NativeDsRoom.fromSession(input) }
        }
    }
    @Test fun selectionSharesOnlyHumanRoomCodesAndImmutableDescriptorDetectsReplacement() {
        val room = NativeDsRoom.fromSession(session())!!
        val text = NativeDsSelection("ABC-234", "Mario", room).shareText()
        assertTrue(text.contains("BCDF")); assertTrue(text.contains("ABC-234")); assertFalse(text.contains(room.contentSha256))
        assertThrows(IllegalArgumentException::class.java) { NativeDsSelection("http://unexpected.invalid", "H", room) }
        val changed = session(); changed.getJSONObject("native").put("roomCode", "CGHJ")
        assertFalse(room.sameDescriptor(NativeDsRoom.fromSession(changed)!!))
    }
}
