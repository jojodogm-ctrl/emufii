package eu.emufii.app.library.switchfs

/**
 * The console keys a Switch dump needs before it will say anything about itself.
 *
 * Unlike a 3DS SMDH or a DS banner, nothing in an NSP is readable in the clear:
 * the icon lives inside an encrypted NCA. So Emufii can only show a real icon if
 * the player supplies their own `prod.keys`, the same file every Switch
 * emulator already asks for. Emufii never ships one, never fetches one, and works
 * without one: no keys simply means the tile keeps its initials.
 *
 * The file is the familiar `name = hex` format, one key per line, `#` comments
 * allowed. Only the three families below are read; everything else is ignored
 * rather than parsed, because everything else is somebody's console secret and
 * we have no business holding it in memory.
 */
class SwitchKeys private constructor(private val keys: Map<String, ByteArray>) {

    /** AES-XTS key (32 bytes) that unwraps every NCA header. */
    val headerKey: ByteArray? get() = keys["header_key"]?.takeIf { it.size == 32 }

    /** Unwraps an NCA's key area, for content encrypted without a ticket. */
    fun keyAreaKeyApplication(generation: Int): ByteArray? =
        keys["key_area_key_application_%02x".format(generation)]

    /** Unwraps a ticket's title key, for content that carries a rights id. */
    fun titleKek(generation: Int): ByteArray? = keys["titlekek_%02x".format(generation)]

    val isUsable: Boolean get() = headerKey != null

    /**
     * The kept keys, back in the `name = hex` form they came in.
     *
     * Written so [eu.emufii.app.library.ConsoleKeysStore] can store the usable
     * subset instead of a copy of the player's whole key file.
     */
    fun toKeyFile(): String = keys.entries
        .sortedBy { it.key }
        .joinToString("\n") { (name, value) ->
            "$name = " + value.joinToString("") { "%02x".format(it) }
        }

    companion object {
        /** Longest line we will look at, a key line is ~80 characters. */
        private const val MAX_LINE = 512

        private val WANTED = listOf("header_key", "key_area_key_application_", "titlekek_")

        fun parse(text: String): SwitchKeys {
            val out = HashMap<String, ByteArray>()
            text.lineSequence().forEach { raw ->
                if (raw.length > MAX_LINE) return@forEach
                val line = raw.substringBefore('#').trim()
                val eq = line.indexOf('=')
                if (eq <= 0) return@forEach
                val name = line.substring(0, eq).trim().lowercase()
                if (WANTED.none { name == it || name.startsWith(it) }) return@forEach
                val hex = line.substring(eq + 1).trim()
                hexOrNull(hex)?.let { out[name] = it }
            }
            return SwitchKeys(out)
        }

        private fun hexOrNull(s: String): ByteArray? {
            if (s.length % 2 != 0 || s.isEmpty()) return null
            val out = ByteArray(s.length / 2)
            for (i in out.indices) {
                val hi = Character.digit(s[i * 2], 16)
                val lo = Character.digit(s[i * 2 + 1], 16)
                if (hi < 0 || lo < 0) return null
                out[i] = ((hi shl 4) or lo).toByte()
            }
            return out
        }
    }
}
