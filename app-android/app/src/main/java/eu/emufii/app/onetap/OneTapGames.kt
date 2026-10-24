package eu.emufii.app.onetap

import java.util.Locale

/** Library identity selects the supported game UI, never the ROM transferred by its host. */
object OneTapGames {
    fun fromProductCode(productCode: String?): OneTapGame? {
        val code = productCode?.trim()?.uppercase(Locale.ROOT) ?: return null
        val match = Regex("(?:NDS-([A-Z0-9]{4})-[A-Z0-9]{2}|([A-Z0-9]{4}))").matchEntire(code) ?: return null
        val gameCode = match.groupValues[1].ifEmpty { match.groupValues[2] }
        return when (gameCode.take(3)) {
            "A8T" -> OneTapGame.MARIO_PARTY_DS
            "AMC" -> OneTapGame.MARIO_KART_DS
            else -> null
        }
    }
}
