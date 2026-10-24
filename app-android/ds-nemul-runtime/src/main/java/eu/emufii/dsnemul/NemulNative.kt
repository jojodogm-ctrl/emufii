package eu.emufii.dsnemul

import java.nio.ByteBuffer

/** Unique JNI namespace; the upstream engine remains byte-for-byte unchanged. */
internal object NemulNative {
    init { System.loadLibrary("emufii_nemul_bridge") }
    external fun start(rom: String, stateDir: String, seat: Int, players: Int, proxyPort: Int,
                       delay: Int, room: String, name: String): Int
    external fun input(keys: Int, x: Int, y: Int, down: Boolean, mic: Int)
    external fun running(): Int
    external fun frames(seatIndex: Int): Long
    external fun stop()
    external fun audio(samples: ShortArray, maxPairs: Int): Int
    external fun copyFrame(seatIndex: Int, memory: ByteBuffer): Boolean
}
