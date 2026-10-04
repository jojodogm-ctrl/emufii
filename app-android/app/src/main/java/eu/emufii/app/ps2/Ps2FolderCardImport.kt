package eu.emufii.app.ps2

object Ps2FolderCardImport {

    const val SUPERBLOCK = "_pcsx2_superblock"
    const val INDEX = "_pcsx2_index"

    data class Save(val directory: String, val files: List<Pair<String, ByteArray>>)

    /** Both braces escaped: Android's ICU regex rejects a bare `}` that the JVM accepts. */
    private val ENTRY = Regex("""([^,{}\s][^,{}:]*)\s*:\s*\{([^}]*)\}""")
    private val ORDER = Regex("""\border\s*:\s*(\d+)""")

    /** A null or unreadable index falls back to a stable order by name. */
    fun order(indexText: String?, files: Map<String, ByteArray>): List<Pair<String, ByteArray>> {
        val payload = files.filterKeys { it != INDEX }
        val ranks = mutableMapOf<String, Int>()
        if (indexText != null) {
            for (match in ENTRY.findAll(indexText)) {
                val name = match.groupValues[1].trim()
                if (name == "\$ROOT") continue
                ORDER.find(match.groupValues[2])?.groupValues?.get(1)?.toIntOrNull()
                    ?.let { ranks[name] = it }
            }
        }
        // Then whatever the index forgot, by name: two runs give the same card.
        val ranked = payload.keys.filter { it in ranks }.sortedBy { ranks.getValue(it) }
        val rest = payload.keys.filter { it !in ranks }.sorted()
        return (ranked + rest).map { it to payload.getValue(it) }
    }
}
