package eu.emufii.app.onetap

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OneTapGamesTest {
    @Test
    fun `supported library regions choose game identity without choosing transferred assets`() {
        for (code in listOf("AMCE", "AMCP", "AMCJ", "NDS-AMCP-01")) {
            assertEquals(code, OneTapGame.MARIO_KART_DS, OneTapGames.fromProductCode(code))
        }
        for (code in listOf("A8TE", "A8TP", "A8TJ", "NDS-A8TP-01")) {
            assertEquals(code, OneTapGame.MARIO_PARTY_DS, OneTapGames.fromProductCode(code))
        }
    }

    @Test
    fun `unrelated games and display names cannot enable the integration`() {
        for (code in listOf(null, "", "NDS-AMCE", "NDS-AMCE-XYZ", "SM64", "Mario Kart DS", "AA8TE")) {
            assertNull(code, OneTapGames.fromProductCode(code))
        }
    }
}
