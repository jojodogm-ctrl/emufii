package eu.emufii.app.library

// Compatibility-database lookup keys for a ROM. Must stay in step with scripts/compat.mjs.
fun Rom.compatKeys(): List<String> = compatKeys(console, productCode, titleIdHex)

fun compatKeys(
    console: Console,
    productCode: String?,
    titleIdHex: String?
): List<String> {
    val keys = LinkedHashSet<String>()
    val code = productCode?.trim()?.uppercase()
    val titleId = titleIdHex?.trim()?.uppercase()

    when (console) {
        // CTR-P-ARRJ: region is the last char of the four; read from the end (CTR-P, CTR-N, KTR-P prefixes).
        Console.THREE_DS -> {
            val four = code?.takeLast(4)?.takeIf { it.length == 4 && it.all(Char::isLetterOrDigit) }
            four?.let {
                keys += "3ds:${it.dropLast(1)}"
                keys += "3ds:$it"
            }
            titleId?.let { keys += "3ds:t:$it" }
        }

        // NDS-ADAE-01: maker code dropped, GameTDB keys on the four characters only.
        Console.DS -> {
            val game = code?.split('-')?.getOrNull(1)?.takeIf { it.length == 4 }
            if (game != null) {
                keys += "ds:${game.dropLast(1)}"
                keys += "ds:$game"
            }
        }

        // RMCP01: system, game(2), region, publisher(2); the same code exists on both consoles.
        Console.GAMECUBE, Console.WII -> {
            val prefix = if (console == Console.WII) "wii" else "gc"
            val id = code?.takeIf { it.length == 6 && it.all(Char::isLetterOrDigit) }
            id?.let {
                keys += "$prefix:${it.take(3)}${it.substring(4)}"
                keys += "$prefix:$it"
            }
        }

        // The exact serial only: the database is expected to carry every region.
        Console.PSP -> code?.removePrefix("PSP-")?.let { keys += "psp:$it" }
        Console.PS2 -> code?.let { keys += "ps2:$it" }

        Console.SWITCH -> titleId?.let { keys += "switch:$it" }

        else -> Unit
    }

    return keys.toList()
}
