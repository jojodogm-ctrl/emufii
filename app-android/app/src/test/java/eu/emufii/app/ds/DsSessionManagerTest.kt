package eu.emufii.app.ds

import eu.emufii.app.network.CoordinatorError
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class DsSessionManagerTest {
    private fun silver(players: Int = 3) = DsSessionSpec(DsTier.SILVER, DsSessionSpec.PROTOCOL,
        "nds-${"ab".repeat(8)}", "android-nemul-ca43f8efdeeccd19", "ab".repeat(32), players, players)

    private fun gold() = DsSessionSpec(DsTier.GOLD, DsSessionSpec.PROTOCOL, "mpds",
        "android-hle-c4d70cff0916e3a4", "ab".repeat(32), 4, 1, "reviewed", "cd".repeat(32))

    private fun admission() = JSONObject().put("protocol", "emufii-silver-udp-v1")
        .put("sessionId", "12".repeat(16)).put("room", "A0123456789BCDE")
        .put("seat", 2).put("capability", "34".repeat(32))
        .put("relayAddress", "51.77.202.144").put("relayPort", 6080).put("inputDelay", 4)

    @Test fun aJoinedRoundCannotChangeModeRomPlayerCountOrCodeBeforeAnotherLaunch() {
        val expected = silver()
        DsSessionPolicy.requireDescriptor("ABC-234", expected, "ABC-234", expected)
        val changes = listOf(null, gold(), expected.copy(romSha256 = "cd".repeat(32)),
            expected.copy(players = 2, instanceCount = 2), expected.copy(runtimeVersion = "foreign"),
            expected.copy(protocol = "foreign"))
        changes.forEach { changed ->
            assertThrows(IllegalArgumentException::class.java) {
                DsSessionPolicy.requireDescriptor("ABC-234", expected, "ABC-234", changed)
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            DsSessionPolicy.requireDescriptor("ABC-234", expected, "ABC-235", expected)
        }
        assertThrows(IllegalArgumentException::class.java) {
            DsSessionPolicy.requireDescriptor("../other", expected, "../other", expected)
        }
        val invalidLayout = expected.copy(instanceCount = 1)
        assertThrows(IllegalArgumentException::class.java) {
            DsSessionPolicy.requireDescriptor("ABC-234", invalidLayout, "ABC-234", invalidLayout)
        }
    }

    @Test fun privateIdentityAndBearerMustExistBeforeTheRuntimeReceivesAdmission() {
        assertEquals("ab".repeat(32), DsSessionPolicy.identityKey("ab".repeat(32)))
        assertEquals("a".repeat(32), DsSessionPolicy.bearer("a".repeat(32)))
        for (identity in listOf(null, "", "AB".repeat(32), "ab".repeat(31), "secret\n")) {
            assertThrows(IllegalArgumentException::class.java) { DsSessionPolicy.identityKey(identity) }
        }
        for (token in listOf(null, "", "a".repeat(31), "a".repeat(129), "a".repeat(32) + "\r\nInjected: x")) {
            assertThrows(IllegalArgumentException::class.java) { DsSessionPolicy.bearer(token) }
        }
    }

    @Test fun malformedAdmissionAndRevokedMembershipStopTheRoundButTemporaryNetworkErrorsDoNot() {
        assertNotNull(DsSessionPolicy.terminalError(CoordinatorError.NotFound()))
        assertNotNull(DsSessionPolicy.terminalError(IllegalArgumentException("unexpected DS descriptor")))
        for (status in listOf(400, 401, 403)) assertNotNull(DsSessionPolicy.terminalError(CoordinatorError.Http(status)))
        for (status in listOf(429, 500, 502, 503)) assertNull(DsSessionPolicy.terminalError(CoordinatorError.Http(status)))
        assertNull(DsSessionPolicy.terminalError(CoordinatorError.Unreachable(IOException("connection reset"))))
        assertNull(DsSessionPolicy.terminalError(null))
    }

    @Test fun silverAdmissionRequiresTheExactSeatAndWireContract() {
        val parsed = DsSilverAdmission.parse(admission(), 3)
        assertEquals("A0123456789BCDE", parsed.room)
        assertEquals(2, parsed.seat)
        assertEquals(16, parsed.sessionId.size)
        assertEquals(32, parsed.capability.size)
        assertFalse(parsed.toString().contains("34".repeat(32)))
        parsed.erase()
        assertTrue(parsed.sessionId.all { it == 0.toByte() })
        assertTrue(parsed.capability.all { it == 0.toByte() })
        val mutations = listOf("protocol" to "wireguard", "seat" to 0, "seat" to 4,
            "seat" to "2", "seat" to 2.5, "inputDelay" to 3, "relayPort" to 0,
            "relayPort" to 65536, "relayAddress" to "relay.example.org", "relayAddress" to "1.2.3.04",
            "relayAddress" to "224.0.0.1", "room" to "a0123456789bcde",
            "capability" to "ab".repeat(31), "capability" to "AB".repeat(32),
            "sessionId" to "12".repeat(15), "ownerToken" to "must-not-be-forwarded")
        mutations.forEach { (field, value) ->
            assertThrows("$field=$value", IllegalArgumentException::class.java) {
                DsSilverAdmission.parse(admission().put(field, value), 3)
            }
        }
        for (players in listOf(1, 5, 8)) assertThrows(IllegalArgumentException::class.java) {
            DsSilverAdmission.parse(admission(), players)
        }
        assertThrows(IllegalArgumentException::class.java) {
            DsSilverAdmission.parse(admission().apply { remove("capability") }, 3)
        }
    }

    @Test fun nativeAliasesKeepGoldUnicodeAndRespectTheActualEngineLimits() {
        assertEquals("Éléonore", DsSessionManager.nativeName(" Éléonore ", gold()))
        assertEquals("Alice", DsSessionManager.nativeName("\uFEFF\u200fAlice\u202e\n", gold()))
        assertEquals("ABCDEFGH", DsSessionManager.nativeName("ABCDEFGHIJKLM", gold()))
        assertEquals("LongPlayer", DsSessionManager.nativeName("LongPlayerName", silver()))
        assertEquals("Joueur", DsSessionManager.nativeName("😃é 中文", silver()))
        assertEquals("Joueur", DsSessionManager.nativeName("\uFEFF\u200f😃\n", gold()))
    }
}
