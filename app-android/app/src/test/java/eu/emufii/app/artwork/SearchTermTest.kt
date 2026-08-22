package eu.emufii.app.artwork

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * What we send the catalogue to search for.
 *
 * A dump's name is not a game title: it drags its region and its revision along.
 * Searching for "Mario Kart 7 (USA) (Rev 1)" brings nothing back, and a game
 * that cannot be found is the cache's most expensive case, the one that produces
 * no image to remember.
 */
class SearchTermTest {

    @Test
    fun `retire la region et la revision`() {
        assertEquals("Mario Kart 7", SteamGridDb.searchTerm("Mario Kart 7 (USA) (Rev 1)"))
    }

    @Test
    fun `retire les marqueurs entre crochets`() {
        assertEquals("Luigi's Mansion", SteamGridDb.searchTerm("Luigi's Mansion [!]"))
    }

    @Test
    fun `remplace les separateurs de nom de fichier par des espaces`() {
        assertEquals("Kirby Planet Robobot", SteamGridDb.searchTerm("Kirby.Planet_Robobot"))
    }

    /** The suffix is part of the real title: removing it would break the search. */
    @Test
    fun `garde le suffixe de console`() {
        assertEquals(
            "The Legend of Zelda Ocarina of Time 3D",
            SteamGridDb.searchTerm("The Legend of Zelda Ocarina of Time 3D (Europe)")
        )
    }

    @Test
    fun `un titre deja propre ne change pas`() {
        assertEquals("Majora's Mask 3D", SteamGridDb.searchTerm("Majora's Mask 3D"))
    }
}
