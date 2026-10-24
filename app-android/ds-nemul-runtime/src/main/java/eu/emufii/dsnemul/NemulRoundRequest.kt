package eu.emufii.dsnemul

import android.net.Uri
import java.util.concurrent.atomic.AtomicBoolean

/** A fresh authenticated admission. Never construct this from invitation query parameters. */
class NemulRoundRequest(
    val romUri: Uri,
    val expectedRomSha256: String,
    val roomCode: String,
    val nativeSeat: Int,
    val players: Int,
    val inputDelay: Int,
    val relayAddress: String,
    val relayPort: Int,
    sessionId: ByteArray,
    capability: ByteArray,
    val playerName: String,
) {
    internal val sessionId = sessionId.copyOf()
    internal val capability = capability.copyOf()
    private val consumed = AtomicBoolean(false)
    internal fun consume(): Boolean = consumed.compareAndSet(false, true)

    internal fun valid(): Boolean =
        romUri.scheme in setOf("content", "file") &&
        expectedRomSha256.matches(Regex("[0-9a-f]{64}")) &&
        roomCode.matches(Regex("[A-Z0-9]{1,15}")) &&
        players in 2..4 && nativeSeat in 1..players && inputDelay in 1..30 &&
        relayPort in 1..65535 && numericIpv4(relayAddress) &&
        sessionId.size == 16 && capability.size == 32 &&
        playerName.isNotBlank() && playerName.length <= 10 &&
        playerName.all { it.code in 32..126 }

    internal fun destroySecrets() { capability.fill(0); sessionId.fill(0) }

    companion object {
        const val RUNTIME_VERSION = "android-nemul-ca43f8efdeeccd19"
        const val ENGINE_REVISION = "41f251bffae81e65fcb86a9f2f21d761ea9067b3"
        const val ENGINE_SHA256 = "ca43f8efdeeccd19e0a17e4e801feb046b232ea277b20f5066d4ac8ae70a0fdf"

        internal fun numericIpv4(value: String): Boolean {
            val parts = value.split('.')
            return parts.size == 4 && parts.all {
                it.matches(Regex("0|[1-9][0-9]{0,2}")) && (it.toIntOrNull() ?: -1) in 0..255
            } && value != "0.0.0.0" && value != "255.255.255.255"
        }
    }
}
