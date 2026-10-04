package eu.emufii.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class TitleKeyTest {
    @Test
    fun `trademark marks and punctuation do not split one game`() {
        assertEquals(titleKey("Animal Crossing™: New Horizons"), titleKey("Animal Crossing: New Horizons"))
    }

    @Test
    fun `accents and case do not either`() {
        assertEquals(titleKey("Pokémon Épée"), titleKey("POKEMON epee"))
    }

    @Test
    fun `two games stay two games`() {
        assertNotEquals(titleKey("Mario Kart 8"), titleKey("Mario Kart 7"))
    }
}
