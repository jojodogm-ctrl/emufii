package eu.emufii.dsnemul

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicBoolean

/** The unmodified engine talks only to loopback. Every external datagram is authenticated. */
internal class SilverUdpProxy(private val request: NemulRoundRequest, private val failed: () -> Unit) {
    private val live = AtomicBoolean(true)
    private val loopback = InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1))
    private val local = DatagramSocket(InetSocketAddress(loopback, 0)).apply { soTimeout = 250 }
    private val remote = DatagramSocket().apply {
        connect(InetAddress.getByAddress(request.relayAddress.split('.').map { it.toInt().toByte() }.toByteArray()),
            request.relayPort)
        soTimeout = 250
        receiveBufferSize = 4 * 1024 * 1024
        sendBufferSize = 4 * 1024 * 1024
    }
    private val codec = SilverWire(request.capability, request.sessionId, request.nativeSeat)
    private val incoming = NativePacketGuard()
    @Volatile private var engine: InetSocketAddress? = null
    val port: Int get() = local.localPort

    fun start() {
        pump("silver-c2s") {
            val packet = DatagramPacket(ByteArray(SilverWire.MAX_NATIVE + 1), SilverWire.MAX_NATIVE + 1)
            local.receive(packet)
            if (packet.address != loopback) return@pump
            val bytes = packet.data.copyOf(packet.length)
            if (!SilverWire.ownNativePacket(bytes, request.nativeSeat, request.roomCode)) return@pump
            val source = InetSocketAddress(packet.address, packet.port)
            if (engine == null) {
                if (bytes.size != 22 || String(bytes, 0, 4, Charsets.US_ASCII) != "MRJN") return@pump
                engine = source
            }
            if (source != engine) return@pump
            val wrapped = codec.encode(bytes)
            remote.send(DatagramPacket(wrapped, wrapped.size))
        }
        pump("silver-s2c") {
            val packet = DatagramPacket(ByteArray(SilverWire.HEADER + 16 + SilverWire.MAX_NATIVE + 1),
                SilverWire.HEADER + 16 + SilverWire.MAX_NATIVE + 1)
            remote.receive(packet)
            val bytes = codec.decode(packet.data.copyOf(packet.length)) ?: return@pump
            val sourceSeat = bytes.getOrNull(1)?.toInt() ?: return@pump
            if (sourceSeat !in 1..request.players) return@pump
            if (!incoming.accepts(bytes, sourceSeat, request.roomCode)) return@pump
            val destination = engine ?: return@pump
            local.send(DatagramPacket(bytes, bytes.size, destination))
        }
    }

    private fun pump(name: String, step: () -> Unit) {
        Thread({
            while (live.get()) try { step() }
            catch (_: SocketTimeoutException) { }
            catch (_: Exception) { if (live.getAndSet(false)) failed(); break }
        }, name).start()
    }

    fun close() {
        live.set(false)
        local.close()
        remote.close()
        codec.destroy()
    }
}
