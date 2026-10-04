package eu.emufii.app.ps2

import org.json.JSONObject

/** Reads the `armsx2-settings.json` mirror ARMSX2 writes beside its memory cards; absent keys mean defaults. */
object Ps2Armsx2Settings {

    data class GameOverride(
        val serial: String,
        val slot1Enabled: Boolean? = null,
        val slot1Filename: String? = null,
        val biosFilename: String? = null,
    )

    data class Parsed(
        val slot1Enabled: Boolean = true,
        val slot1Filename: String = "mcd001.ps2",
        val biosFilename: String? = null,
        val slot2Enabled: Boolean = true,
        val slot2Filename: String = "mcd002.ps2",
        val gameOverrides: List<GameOverride> = emptyList(),
    ) {
        val hasRelevantGameOverrides: Boolean get() = gameOverrides.isNotEmpty()
    }

    fun parse(mirrorJson: String): Parsed {
        val root = runCatching { JSONObject(mirrorJson) }.getOrNull() ?: return Parsed()
        val global = root.optJSONObject("global") ?: JSONObject()
        val overrides = mutableListOf<GameOverride>()
        root.optJSONObject("games")?.let { games ->
            val serials = games.keys()
            while (serials.hasNext()) {
                val serial = serials.next()
                val game = games.optJSONObject(serial) ?: continue
                if (!game.has("memoryCardSlot1Enabled") &&
                    !game.has("memoryCardSlot1Filename") && !game.has("biosFilename")
                ) continue
                overrides += GameOverride(
                    serial = serial,
                    slot1Enabled = game.optBoolean("memoryCardSlot1Enabled")
                        .takeIf { game.has("memoryCardSlot1Enabled") },
                    slot1Filename = game.optString("memoryCardSlot1Filename")
                        .takeIf { game.has("memoryCardSlot1Filename") },
                    biosFilename = game.optString("biosFilename")
                        .takeIf { game.has("biosFilename") },
                )
            }
        }
        return Parsed(
            slot1Enabled = global.optBoolean("memoryCardSlot1Enabled", true),
            slot1Filename = global.optString("memoryCardSlot1Filename", "mcd001.ps2")
                .ifEmpty { "mcd001.ps2" },
            biosFilename = global.optString("biosFilename", "").ifEmpty { null },
            slot2Enabled = global.optBoolean("memoryCardSlot2Enabled", true),
            slot2Filename = global.optString("memoryCardSlot2Filename", "mcd002.ps2")
                .ifEmpty { "mcd002.ps2" },
            gameOverrides = overrides,
        )
    }

    fun parseIni(ini: String): Parsed {
        val values = mutableMapOf<String, String>()
        var section = ""
        for (rawLine in ini.lineSequence()) {
            val line = rawLine.trim()
            if (line.startsWith('[') && line.endsWith(']')) {
                section = line.substring(1, line.length - 1)
                continue
            }
            val equals = line.indexOf('=')
            if (equals <= 0) continue
            val key = line.substring(0, equals).trim()
            val value = line.substring(equals + 1).trim()
            values["$section/$key"] = value
        }
        return Parsed(
            slot1Enabled = values["MemoryCards/Slot1_Enable"]?.lowercase() != "false",
            slot1Filename = values["MemoryCards/Slot1_Filename"]?.ifEmpty { null } ?: "mcd001.ps2",
            biosFilename = values["Filenames/BIOS"]?.ifEmpty { null },
            slot2Enabled = values["MemoryCards/Slot2_Enable"]?.lowercase() != "false",
            slot2Filename = values["MemoryCards/Slot2_Filename"]?.ifEmpty { null } ?: "mcd002.ps2",
        )
    }
}
