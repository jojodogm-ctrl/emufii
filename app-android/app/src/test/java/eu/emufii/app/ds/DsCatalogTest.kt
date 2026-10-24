package eu.emufii.app.ds

import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeNotNull
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.attribute.FileTime
import kotlin.coroutines.cancellation.CancellationException

class DsCatalogTest {
    private fun json() = JSONObject(File("src/main/assets/ds-runtime-profiles.json").readText())
    private fun registry() = DsCatalog.parseRegistry(json())

    @Test fun reviewedRawAndPreparedMpdsHaveGoldIdentity() {
        val registry = registry()
        val mpds = registry.profiles.values.filter { it.gameId == "mpds" }
        assertEquals(2, mpds.size)
        assertEquals(1, mpds.map { it.canonicalRomSha256 }.distinct().size)
        assertEquals(1, mpds.map { it.profileFingerprint }.distinct().size)
        mpds.forEach { profile ->
            val identity = DsCatalog.identity(profile.romSha256, registry)
            assertEquals(DsTier.GOLD, identity.tier)
            assertEquals(listOf(2, 3, 4), identity.allowedPlayers)
            val spec = DsCatalog.choose(identity, 4)
            assertEquals("ds-hle", spec.transport)
            assertEquals(1, spec.instanceCount)
            assertEquals(profile.romSha256, spec.romSha256)
            assertTrue(DsCatalog.valid(spec, registry))
        }
    }

    @Test fun unreviewedCartridgeIsSilverWithNConsolesAndAtMostFourPlayers() {
        val identity = DsCatalog.identity("ab".repeat(32), registry())
        assertNull(identity.profile)
        assertEquals(DsTier.SILVER, identity.tier)
        assertEquals(listOf(2, 3, 4), identity.allowedPlayers)
        for (players in 2..4) {
            val spec = DsCatalog.choose(identity, players)
            assertEquals("ds-local", spec.transport)
            assertEquals(players, spec.instanceCount)
            assertTrue(DsCatalog.valid(spec, registry()))
            assertFalse(spec.toJson().has("profileId"))
        }
        assertThrows(IllegalArgumentException::class.java) { DsCatalog.choose(identity, 5) }
    }

    @Test fun reviewedSdk500CartridgeUsesOneConsoleAndOnlyItsAdmittedSeats() {
        val registry=registry()
        val identity=DsCatalog.identity("ca4242d7f88a4b8c4c84e28adcebb3ff558e11863c31d3e83c848a4a230715dc",registry)
        assertEquals(DsTier.GOLD,identity.tier)
        assertEquals(listOf(2),identity.allowedPlayers)
        val spec=DsCatalog.choose(identity,2)
        assertEquals("experimental",spec.gameId)
        assertEquals(1,spec.instanceCount)
        assertTrue(DsCatalog.valid(spec,registry))
        assertThrows(IllegalArgumentException::class.java) { DsCatalog.choose(identity,3) }
        assertFalse(DsCatalog.valid(spec.copy(runtimeVersion="other"),registry))
    }

    @Test fun reviewedProfilesKeepTheirOwnSeatRestrictions() {
        val registry = registry()
        val dkr = registry.profiles.values.single { it.gameId == "dkr" }
        val identity = DsCatalog.identity(dkr.romSha256, registry)
        assertEquals(listOf(2), identity.allowedPlayers)
        assertThrows(IllegalArgumentException::class.java) { DsCatalog.choose(identity, 3) }
        val mkds = registry.profiles.values.single { it.gameId == "mkds" }
        assertEquals(8, DsCatalog.choose(DsCatalog.identity(mkds.romSha256, registry), 8).players)
    }

    @Test fun sessionRoundTripAndGoldPinsRejectSpoofedDescriptor() {
        val registry = registry()
        val profile = registry.profiles.values.first()
        val original = DsCatalog.choose(DsCatalog.identity(profile.romSha256, registry), 2)
        assertEquals(original, DsSessionSpec.parse(original.toRequestJson()))
        assertNull(DsSessionSpec.parse("ds-local", original.toJson()))
        for ((field, value) in listOf("romSha256" to "00".repeat(32),
            "profileFingerprint" to "00".repeat(32), "runtimeVersion" to "other", "profileId" to "other", "gameId" to "other")) {
            val modified = JSONObject(original.toJson().toString()).put(field, value)
            val parsed = DsSessionSpec.parse(original.transport, modified)
            assertNotNull(field, parsed) // Structure alone does not imply a reviewed local profile.
            assertFalse(field, DsCatalog.valid(parsed!!, registry))
        }
        assertNull(DsSessionSpec.parse(original.transport, original.toJson().put("instanceCount", 2)))
        assertNull(DsSessionSpec.parse(original.transport, original.toJson().put("relayAddress", "1.2.3.4")))
    }

    @Test fun silverRejectsProfileFieldsWrongInstanceCountsAndForeignRuntime() {
        val registry = registry()
        val original = DsCatalog.choose(DsCatalog.identity("ab".repeat(32), registry), 3)
        assertEquals(original, DsSessionSpec.parse(original.toRequestJson()))
        assertNull(DsSessionSpec.parse(original.transport, original.toJson().put("profileId", JSONObject.NULL)))
        assertNull(DsSessionSpec.parse(original.transport, original.toJson().put("players", "3")))
        assertNull(DsSessionSpec.parse(original.transport, original.toJson().put("players", 3.5)))
        assertNull(DsSessionSpec.parse(original.transport, original.toJson().put("protocol", "other")))
        assertNull(DsSessionSpec.parse(original.transport, original.toJson().put("instanceCount", 1)))
        val foreign = DsSessionSpec.parse(original.transport, original.toJson().put("runtimeVersion", "other"))!!
        assertFalse(DsCatalog.valid(foreign, registry))
    }

    @Test fun catalogRejectsUnknownBinaryPinAndAmbiguousRomHash() {
        val altered = json()
        altered.getJSONArray("profiles").getJSONObject(0).put("runtimeSha256", "00".repeat(32))
        assertThrows(IllegalArgumentException::class.java) { DsCatalog.parseRegistry(altered) }
        val duplicate = json()
        duplicate.getJSONArray("profiles").put(duplicate.getJSONArray("profiles").getJSONObject(0))
        assertThrows(IllegalArgumentException::class.java) { DsCatalog.parseRegistry(duplicate) }
        val silver = json()
        silver.getJSONObject("silver").put("maxPlayers", 8)
        assertThrows(IllegalArgumentException::class.java) { DsCatalog.parseRegistry(silver) }
    }

    @Test fun disabledSilverDoesNotProduceSession() {
        val json = json().also { it.getJSONObject("silver").put("available", false) }
        val identity = DsCatalog.identity("ab".repeat(32), DsCatalog.parseRegistry(json))
        assertEquals(DsTier.SILVER, identity.tier)
        assertEquals(emptyList<Int>(), identity.allowedPlayers)
        assertThrows(IllegalArgumentException::class.java) { DsCatalog.choose(identity, 2) }
    }

    @Test fun digestUsesWholeContentAndPropagatesCancellationWhileClosingStream() = runBlocking {
        val hash = ByteArrayInputStream("abc".toByteArray()).use { DsCatalog.hashStream(it) {} }
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", hash)
        var reads = 0
        var closed = false
        val input = object : InputStream() {
            override fun read(): Int = 0
            override fun read(bytes: ByteArray, offset: Int, length: Int): Int { reads++; bytes[offset] = 1; return 1 }
            override fun close() { closed = true }
        }
        var checks = 0
        val error = runCatching { input.use { DsCatalog.hashStream(it) {
            if (++checks == 2) throw CancellationException("cancel")
        } } }.exceptionOrNull()
        assertTrue(error is CancellationException)
        assertEquals(1, reads)
        assertTrue(closed)
    }

    @Test fun filesystemCacheStampChangesWhenContentMetadataChanges() {
        val path = Files.createTempFile("emufii-ds-hash", ".nds")
        try {
            Files.write(path, byteArrayOf(1, 2, 3))
            val before = DsCatalog.localFileStamp(path.toFile())
            assumeNotNull(before)
            Files.write(path, byteArrayOf(4, 5, 6))
            Files.setLastModifiedTime(path, FileTime.fromMillis(System.currentTimeMillis() + 5000))
            val after = DsCatalog.localFileStamp(path.toFile())
            assertNotEquals(before, after)
        } finally { Files.deleteIfExists(path) }
    }
}
