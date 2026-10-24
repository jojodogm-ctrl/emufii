package eu.emufii.dsnemul

import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class SilverWireTest {
    private val capability = ByteArray(32) { it.toByte() }
    private val session = ByteArray(16) { it.toByte() }
    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }

    @Test fun hkdfMatchesIndependentHmacVectors() {
        assertEquals("622693448f5fb04cb76103dcebe2f30025463fd4a7f1376b21837909bd484d29",
            hex(SilverWire.derive(capability, session, "c2s")))
        assertEquals("d9e0f65fc32c244755754bc3408b283c7f389445eac083fe79a9b091d10435ee",
            hex(SilverWire.derive(capability, session, "s2c")))
    }

    @Test fun authenticationBeforeReplayAdvanceAndDestinationBinding() {
        val codec = SilverWire(capability, session, 2)
        val raw = byteArrayOf('R'.code.toByte(), 1, 0, 0, 0, 0, 0)
        val packet = incoming(2, 1, raw)
        val modified = packet.copyOf().apply { this[lastIndex] = (this[lastIndex].toInt() xor 1).toByte() }
        assertNull(codec.decode(modified))
        assertArrayEquals(raw, codec.decode(packet))
        assertNull(codec.decode(packet))
        assertNull(codec.decode(incoming(3, 2, raw)))
        assertArrayEquals(raw, codec.decode(incoming(2, 2, raw)))
    }

    @Test fun outboundContainsCanonicalHeaderAndUniqueCounter() {
        val codec = SilverWire(capability, session, 2)
        val raw = byteArrayOf('R'.code.toByte(), 2, 0, 0, 0, 0, 0)
        val a = codec.encode(raw)
        val b = codec.encode(raw)
        assertEquals("EDSW", String(a, 0, 4, Charsets.US_ASCII))
        assertEquals(1, a[4].toInt()); assertEquals(0, a[5].toInt()); assertEquals(2, a[6].toInt())
        assertArrayEquals(session, a.copyOfRange(8, 24))
        assertEquals(1L, ByteBuffer.wrap(a, 24, 8).long)
        assertEquals(2L, ByteBuffer.wrap(b, 24, 8).long)
        assertEquals(raw.size + 48, a.size)
    }

    @Test fun authenticatedUdpReorderingIsAcceptedWithinReplayWindow() {
        val codec = SilverWire(capability, session, 2)
        val raw = byteArrayOf('R'.code.toByte(), 1, 0, 0, 0, 0, 0)
        assertArrayEquals(raw, codec.decode(incoming(2, 129, raw)))
        assertArrayEquals(raw, codec.decode(incoming(2, 2, raw)))
        assertNull(codec.decode(incoming(2, 2, raw)))
        assertNull(codec.decode(incoming(2, 1, raw)))
        val forged = incoming(2, 1000, raw).apply { this[lastIndex] = (this[lastIndex].toInt() xor 1).toByte() }
        assertNull(codec.decode(forged))
        assertArrayEquals(raw, codec.decode(incoming(2, 128, raw)))
        assertArrayEquals(raw, codec.decode(incoming(2, 130, raw)))
        assertNull(codec.decode(incoming(2, 2, raw)))
    }

    @Test fun retiredCodecCannotEmitOrAcceptPackets() {
        val codec = SilverWire(capability, session, 2)
        val raw = byteArrayOf('R'.code.toByte(), 1, 0, 0, 0, 0, 0)
        codec.destroy()
        assertNull(codec.decode(incoming(2, 1, raw)))
        assertThrows(IllegalArgumentException::class.java) { codec.encode(raw) }
    }

    @Test fun rejectsNativeRoomSeatSpoofAndMalformedWindow() {
        val join = ByteArray(22).apply {
            "MRJN".toByteArray().copyInto(this); this[5] = 2
            "ABCD1234".toByteArray().copyInto(this, 6)
        }
        assertTrue(SilverWire.ownNativePacket(join, 2, "ABCD1234"))
        assertFalse(SilverWire.ownNativePacket(join, 1, "ABCD1234"))
        assertFalse(SilverWire.ownNativePacket(join, 2, "OTHER"))
        val window = ByteArray(15).apply { this[0] = 'W'.code.toByte(); this[1] = 2; this[6] = 1 }
        assertTrue(SilverWire.ownNativePacket(window, 2, "ABCD1234"))
        assertFalse(SilverWire.ownNativePacket(window.copyOf(14), 2, "ABCD1234"))
        assertFalse(SilverWire.ownNativePacket(window, 3, "ABCD1234"))
        assertFalse(SilverWire.ownNativePacket(byteArrayOf('?'.code.toByte(), 2), 2, "ABCD1234"))
    }

    @Test fun malformedSnapshotCannotReachPinnedNativeBuffer() {
        val valid = snapshot(100, 2, 1, 100, 50)
        assertTrue(SilverWire.ownNativePacket(valid, 1, "ABCD1234"))
        assertFalse(SilverWire.ownNativePacket(valid.copyOf(valid.size - 1), 1, "ABCD1234"))
        assertFalse(SilverWire.ownNativePacket(snapshot(100, 2, 1, 10, 50), 1, "ABCD1234"))
        assertFalse(SilverWire.ownNativePacket(snapshot(100, 2, 2, 100, 50), 1, "ABCD1234"))
        assertFalse(SilverWire.ownNativePacket(snapshot(100, 0, 0, 100, 50), 1, "ABCD1234"))
        val guard = NativePacketGuard()
        assertTrue(guard.accepts(valid, 1, "ABCD1234"))
        // The core would keep its first stride (100) for this same transfer and
        // total; the second packet's 150 bytes must not reach that 100-byte slot.
        assertFalse(guard.accepts(snapshot(100, 2, 1, 200, 150), 1, "ABCD1234"))
        assertFalse(guard.accepts(snapshot(99, 2, 1, 100, 50), 1, "ABCD1234"))
        assertTrue(guard.accepts(snapshot(101, 2, 1, 200, 150), 1, "ABCD1234"))
    }

    @Test fun legacyRomPacketsUseOriginalArm64Padding() {
        fun packet(tag: Char, size: Int) = ByteArray(size).apply { this[0] = tag.code.toByte(); this[1] = 1 }
        assertTrue(SilverWire.ownNativePacket(packet('i', 16), 1, "ABCD1234"))
        assertFalse(SilverWire.ownNativePacket(packet('i', 12), 1, "ABCD1234"))
        assertTrue(SilverWire.ownNativePacket(packet('q', 7), 1, "ABCD1234"))
        assertTrue(SilverWire.ownNativePacket(packet('q', 8), 1, "ABCD1234"))
        assertFalse(SilverWire.ownNativePacket(packet('q', 6), 1, "ABCD1234"))
        assertTrue(SilverWire.ownNativePacket(packet('k', 8), 1, "ABCD1234"))
        val chunk = packet('r', 13).apply { this[10] = 1 }
        assertTrue(SilverWire.ownNativePacket(chunk, 1, "ABCD1234"))
        assertFalse(SilverWire.ownNativePacket(packet('r', 11).apply { this[8] = 1 }, 1, "ABCD1234"))
        val nack = packet('m', 12).apply { this[8] = 1 }
        assertTrue(SilverWire.ownNativePacket(nack, 1, "ABCD1234"))
        assertFalse(SilverWire.ownNativePacket(packet('m', 10).apply { this[6] = 1 }, 1, "ABCD1234"))
    }

    private fun snapshot(transfer: Int, total: Int, index: Int, stride: Int, length: Int): ByteArray =
        ByteBuffer.allocate(14 + length).order(ByteOrder.LITTLE_ENDIAN)
            .put('S'.code.toByte()).put(1).putShort(total.toShort()).putShort(index.toShort())
            .putShort(length.toShort()).putShort(stride.toShort()).putInt(transfer).array()

    private fun incoming(seat: Int, counter: Long, payload: ByteArray): ByteArray {
        val header = ByteBuffer.allocate(32).put("EDSW".toByteArray()).put(1).put(1)
            .put(seat.toByte()).put(0).put(session).putLong(counter).array()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(SilverWire.derive(capability, session, "s2c"), "AES"),
            GCMParameterSpec(128, ByteArray(4) + header.copyOfRange(24, 32)))
        cipher.updateAAD(header)
        return header + cipher.doFinal(payload)
    }
}
