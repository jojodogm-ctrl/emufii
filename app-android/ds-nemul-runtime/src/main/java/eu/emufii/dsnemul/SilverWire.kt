package eu.emufii.dsnemul

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.math.BigInteger
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Authenticated data-plane contract shared with integrations/ds-private. */
internal class SilverWire(capability: ByteArray, sessionId: ByteArray, private val seat: Int) {
    private val session = sessionId.copyOf()
    private val outgoing = derive(capability, session, "c2s")
    private val incoming = derive(capability, session, "s2c")
    private var sent = 0L
    private var received = 0L
    private var replayBits = BigInteger.ZERO
    private var destroyed = false

    // The admission is single-use. Counters are never reset with the same capability.
    @Synchronized fun encode(payload: ByteArray): ByteArray {
        require(!destroyed && payload.size in 2..MAX_NATIVE && sent < Long.MAX_VALUE)
        val header = header(0, ++sent)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(outgoing, "AES"),
            GCMParameterSpec(128, nonce(header)))
        cipher.updateAAD(header)
        return header + cipher.doFinal(payload)
    }

    @Synchronized fun decode(packet: ByteArray): ByteArray? {
        if (destroyed || packet.size !in (HEADER + 16 + 2)..(HEADER + 16 + MAX_NATIVE)) return null
        val header = packet.copyOfRange(0, HEADER)
        if (!header.copyOfRange(0, 4).contentEquals(MAGIC) || header[4] != 1.toByte() ||
            header[5] != 1.toByte() || header[6].toInt() != seat || header[7] != 0.toByte() ||
            !header.copyOfRange(8, 24).contentEquals(session)) return null
        val counter = ByteBuffer.wrap(header, 24, 8).order(ByteOrder.BIG_ENDIAN).long
        if (counter <= 0 || counter <= received &&
            (received - counter >= 128 || replayBits.testBit((received - counter).toInt()))) return null
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(incoming, "AES"),
                GCMParameterSpec(128, nonce(header)))
            cipher.updateAAD(header)
            val result = cipher.doFinal(packet, HEADER, packet.size - HEADER)
            // UDP can reorder snapshots and input windows. Admit each of the last
            // 128 counters once; only authentication may advance/shift the window.
            if (counter > received) {
                val distance = counter - received
                replayBits = if (distance >= 128) BigInteger.ONE else
                    replayBits.shiftLeft(distance.toInt()).or(BigInteger.ONE).and(REPLAY_MASK)
                received = counter
            } else replayBits = replayBits.setBit((received - counter).toInt())
            result
        } catch (_: Exception) { null }
    }

    @Synchronized fun destroy() { destroyed = true; outgoing.fill(0); incoming.fill(0); session.fill(0) }

    private fun header(direction: Int, counter: Long) = ByteBuffer.allocate(HEADER)
        .order(ByteOrder.BIG_ENDIAN).put(MAGIC).put(1).put(direction.toByte())
        .put(seat.toByte()).put(0).put(session).putLong(counter).array()

    companion object {
        const val MAX_NATIVE = 2048
        const val HEADER = 32
        private val MAGIC = byteArrayOf(69, 68, 83, 87) // EDSW
        private val REPLAY_MASK = BigInteger.ONE.shiftLeft(128).subtract(BigInteger.ONE)
        internal fun derive(capability: ByteArray, session: ByteArray, direction: String): ByteArray {
            require(capability.size == 32 && session.size == 16 && direction in setOf("c2s", "s2c"))
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(session, "HmacSHA256"))
            val prk = mac.doFinal(capability)
            mac.init(SecretKeySpec(prk, "HmacSHA256"))
            val result = mac.doFinal("emufii-ds-silver-v1/$direction".toByteArray(Charsets.UTF_8) + byteArrayOf(1))
            prk.fill(0)
            return result
        }
        private fun nonce(header: ByteArray) = ByteArray(4) + header.copyOfRange(24, 32)

        internal fun ownNativePacket(data: ByteArray, seat: Int, room: String): Boolean {
            if (data.size !in 2..MAX_NATIVE) return false
            if (data.size >= 4 && data.copyOfRange(0, 4).contentEquals("MRJN".toByteArray())) {
                if (data.size != 22 || data[4] != 0.toByte() || data[5].toInt() != seat) return false
                val expected = ByteArray(16)
                room.toByteArray(Charsets.US_ASCII).copyInto(expected)
                return data.copyOfRange(6, 22).contentEquals(expected)
            }
            if (data[1].toInt() != seat) return false
            return when (data[0].toInt().toChar()) {
                'R', 'G', 'Q', 'z', 'J', 'F', 'P', 'D', 'L', 'A', 'U', 'X' -> data.size == 7
                'W' -> data.size >= 7 && (data[6].toInt() and 255) in 1..24 &&
                    data.size == 7 + (data[6].toInt() and 255) * 8
                'M' -> data.size == 18
                'Y' -> data.size == 23 || data.size == 27
                'T', 'K' -> data.size == 8
                'C' -> data.size == 14
                'V' -> data.size == 518
                'E' -> data.size == 38
                'S' -> snapshot(data) != null
                'N' -> data.size >= 8 && u16(data, 6) in 1..600 && data.size == 8 + 2 * u16(data, 6)
                // The original source pops pack(1) before its ROM structs. These
                // layouts are the verified ARM64 ABI, unlike packed RTT/snapshots.
                'i' -> data.size == 16
                'q' -> data.size == 7 || data.size == 8
                'r' -> data.size >= 12 && u16(data, 10) in 1..1400 && data.size == 12 + u16(data, 10)
                'm' -> data.size >= 10 && u16(data, 8) in 1..600 && data.size == 10 + 2 * u16(data, 8)
                'k' -> data.size == 8
                else -> false
            }
        }

        internal data class Snapshot(val transfer: Long, val total: Int, val stride: Int)
        internal fun snapshot(data: ByteArray): Snapshot? {
            if (data.size < 14 || data[0].toInt().toChar() != 'S') return null
            val total = u16(data, 2); val index = u16(data, 4)
            val length = u16(data, 6); val stride = u16(data, 8)
            if (total < 1 || index >= total || stride !in 1..1400 || length !in 1..stride ||
                data.size != 14 + length) return null
            val transfer = ByteBuffer.wrap(data, 10, 4).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xffffffffL
            return Snapshot(transfer, total, stride)
        }
        private fun u16(data: ByteArray, offset: Int) = (data[offset].toInt() and 255) or
            ((data[offset + 1].toInt() and 255) shl 8)
    }
}

/** The pinned core keeps the stride from the first chunk in each transfer. A
 * later conflicting stride must never reach its native reassembly buffer. */
internal class NativePacketGuard {
    private var snapshot: SilverWire.Companion.Snapshot? = null
    fun accepts(data: ByteArray, seat: Int, room: String): Boolean {
        if (!SilverWire.ownNativePacket(data, seat, room)) return false
        val next = SilverWire.snapshot(data) ?: return true
        val previous = snapshot
        if (previous != null && (next.transfer < previous.transfer ||
                next.transfer == previous.transfer && next.total == previous.total && next.stride != previous.stride)) return false
        snapshot = next
        return true
    }
}
