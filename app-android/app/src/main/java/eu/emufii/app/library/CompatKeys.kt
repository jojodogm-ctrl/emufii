package eu.emufii.app.library

/**
 * The keys under which a ROM is looked up in the compatibility database.
 *
 * The whole difficulty of "does this game work" is not the verdict, it is
 * saying *which game* without asking anyone to name it. A rating is given once
 * and has to reach every copy of that game in the world, whatever language the
 * player's dump speaks, so a key has to survive the change of region.
 *
 * Two families of console, and they need opposite treatments:
 *
 * - **The region is a character at a known position.** The 3DS, the DS, the
 *   GameCube and the Wii all stamp their region into one letter of an otherwise
 *   identical code (`CTR-P-ARR`**`J`**, `ADA`**`E`**, `RMC`**`P`**`01`). Drop
 *   that letter and the remainder *is* the game, for free, with no table to
 *   maintain and no game left out.
 * - **The regions carry unrelated numbers.** A PSP or PS2 release has nothing
 *   in common between territories — `UCUS-98653` against `UCES-00842` — and no
 *   rule will ever bridge them. Those are matched on the exact serial, and it
 *   falls to the database to list every serial a game was released under. The
 *   tool that writes the database resolves them from the public serial indexes;
 *   this file cannot, and must not pretend to.
 *
 * Every ROM therefore yields *several* keys — a family key where one exists, and
 * always the exact identifiers — and an entry matches if any of them is listed.
 * Belt and braces on purpose: if a family rule ever meets a code it misreads,
 * the exact identifier still lands, and the failure costs one region rather
 * than the whole game.
 */
fun Rom.compatKeys(): List<String> = compatKeys(console, productCode, titleIdHex)

/**
 * The same thing, from the three fields it actually reads.
 *
 * Split out from [Rom] deliberately: this is pure string work with no Android in
 * it, and taking the whole ROM would have dragged a `Uri` into every test of a
 * rule that has nothing to do with files.
 */
fun compatKeys(
    console: Console,
    productCode: String?,
    titleIdHex: String?
): List<String> {
    val keys = LinkedHashSet<String>()
    val code = productCode?.trim()?.uppercase()
    val titleId = titleIdHex?.trim()?.uppercase()

    when (console) {
        // `CTR-P-ARRJ`, and the last of the four is the region. Read from the
        // end rather than by stripping a prefix: the prefix is `CTR-P-` on a
        // retail card, `CTR-N-` on a download, `KTR-P-` on a New 3DS exclusive,
        // and a fourth spelling would break a rule written the other way round.
        Console.THREE_DS -> {
            val four = code?.takeLast(4)?.takeIf { it.length == 4 && it.all(Char::isLetterOrDigit) }
            four?.let {
                keys += "3ds:${it.dropLast(1)}"
                keys += "3ds:$it"
            }
            titleId?.let { keys += "3ds:t:$it" }
        }

        // `NDS-ADAE-01`: game code then maker code, and only the game code is
        // used.
        //
        // The maker code was in here at first, on the reasoning that two
        // publishers might reuse a game code. It came back out on contact with
        // the data: GameTDB — the index the rating tool resolves names against,
        // and every other DS tool with it — keys on the four characters alone
        // and does not publish a maker code at all. A discriminator no source
        // can ever supply does not prevent a collision, it prevents every match,
        // and a DS badge that never appears is a worse failure than a collision
        // nobody has met. The four characters are assigned centrally and are
        // unique per title in practice.
        Console.DS -> {
            val game = code?.split('-')?.getOrNull(1)?.takeIf { it.length == 4 }
            if (game != null) {
                keys += "ds:${game.dropLast(1)}"
                keys += "ds:$game"
            }
        }

        // `RMCP01`: system, two for the game, the region, two for the publisher.
        // Same shape on both consoles, and they are kept apart by the prefix
        // because the same code can exist on each.
        Console.GAMECUBE, Console.WII -> {
            val prefix = if (console == Console.WII) "wii" else "gc"
            val id = code?.takeIf { it.length == 6 && it.all(Char::isLetterOrDigit) }
            id?.let {
                keys += "$prefix:${it.take(3)}${it.substring(4)}"
                keys += "$prefix:$it"
            }
        }

        // No rule reaches across regions here. The exact serial only, and the
        // database is expected to carry all of them for a given game.
        Console.PSP -> code?.removePrefix("PSP-")?.let { keys += "psp:$it" }
        Console.PS2 -> code?.let { keys += "ps2:$it" }

        // Region-free by design: one title id worldwide. The easy case, and the
        // only one where the exact identifier is also the family.
        Console.SWITCH -> titleId?.let { keys += "switch:$it" }

        else -> Unit
    }

    return keys.toList()
}
