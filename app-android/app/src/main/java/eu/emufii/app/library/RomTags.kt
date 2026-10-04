package eu.emufii.app.library

data class RomTags(
    val region: String? = null,
) {
    val isEmpty: Boolean get() = region == null

    fun line(): String? = region
}

object RomTagReader {

    fun read(rom: Rom): RomTags =
        read(rom.console, rom.productCode, rom.titleIdHex, rom.filename)

    /** Uri-free overload for JVM unit tests. */
    fun read(
        console: Console,
        productCode: String?,
        titleIdHex: String?,
        filename: String,
    ): RomTags = RomTags(
        region = regionFromId(console, productCode, titleIdHex) ?: regionFromName(filename),
    )

    private fun regionFromId(console: Console, productCode: String?, titleIdHex: String?): String? {
        val code = productCode?.trim()?.uppercase() ?: return null
        return when (console) {
            // `CTR-P-ARRJ`, last of the four.
            Console.THREE_DS -> code.takeLast(4).takeIf { it.length == 4 }?.last()?.let(::threeDsRegion)

            // `NDS-ADAE-01`, last of the game code.
            Console.DS -> code.split('-').getOrNull(1)?.takeIf { it.length == 4 }?.last()
                ?.let(::nintendoRegion)

            // `RMCP01`, fourth character.
            Console.GAMECUBE, Console.WII ->
                code.takeIf { it.length == 6 }?.get(3)?.let(::nintendoRegion)

            // A Sony serial encodes the region in its prefix, not at a fixed position.
            Console.PSP, Console.PS2 -> sonyRegion(code)

            // One title id worldwide: nothing to read.
            Console.SWITCH -> null
        }
    }

    private fun nintendoRegion(letter: Char): String? = when (letter) {
        'E' -> "USA"
        'P' -> "Europe"
        'J' -> "Japan"
        'K' -> "Korea"
        'U' -> "Australia"
        'F' -> "France"
        'D' -> "Germany"
        'S' -> "Spain"
        'I' -> "Italy"
        'H' -> "Netherlands"
        else -> null
    }

    private fun threeDsRegion(letter: Char): String? =
        if (letter == 'A') "World" else nintendoRegion(letter)

    /** Sony prefix read letter by letter: medium, publisher, region. */
    private fun sonyRegion(code: String): String? {
        val prefix = code.filter { it.isLetter() }.take(4)
        if (prefix.length < 3) return null
        if (prefix[0] != 'S' && prefix[0] != 'U') return null
        return when (prefix[2]) {
            'U' -> "USA"
            'E' -> "Europe"
            // `SLPS`, `ULJM`: Japan spells itself two ways depending on the medium.
            'P', 'J' -> "Japan"
            'K' -> "Korea"
            'A' -> "Asia"
            else -> null
        }
    }

    /** Exact spellings only, or "(Disney's Aladdin)" becomes a region. */
    private fun regionFromName(filename: String): String? {
        val tags = tagsIn(filename)
        for (tag in tags) {
            NAME_REGIONS[tag.uppercase()]?.let { return it }
            // `(USA, Europe)` is common: only the first fits on the one line shown.
            val first = tag.split(',').firstOrNull()?.trim()?.uppercase()
            first?.let { NAME_REGIONS[it]?.let { region -> return region } }
        }
        return null
    }


    private fun tagsIn(filename: String): List<String> =
        TAG.findAll(filename.substringBeforeLast('.')).map { it.groupValues[1].ifEmpty { it.groupValues[2] } }.toList()

    // Braces escaped on purpose: Android's ICU regex rejects what the JVM accepts.
    private val TAG = Regex("""\(([^()]*)\)|\[([^\[\]]*)\]""")

    private val NAME_REGIONS = mapOf(
        "USA" to "USA",
        "US" to "USA",
        "EUROPE" to "Europe",
        "EUR" to "Europe",
        "EU" to "Europe",
        "JAPAN" to "Japan",
        "JPN" to "Japan",
        "JP" to "Japan",
        "WORLD" to "World",
        "KOREA" to "Korea",
        "CHINA" to "China",
        "TAIWAN" to "Taiwan",
        "AUSTRALIA" to "Australia",
        "FRANCE" to "France",
        "GERMANY" to "Germany",
        "SPAIN" to "Spain",
        "ITALY" to "Italy",
        "NETHERLANDS" to "Netherlands",
        "SWEDEN" to "Sweden",
        "BRAZIL" to "Brazil",
        "CANADA" to "Canada",
        "ASIA" to "Asia",
    )
}
