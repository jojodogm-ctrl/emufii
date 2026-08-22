package eu.emufii.app.library

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The title we display, built from an SMDH's two descriptions.
 *
 * The short description truncates: A Link Between Worlds is called "The Legend
 * of Zelda" there, so two Zeldas in the library carried the same name and the
 * icon search could only bring back whichever Zelda came first.
 *
 * Taking the long one systematically would be worse, it is sometimes cover-art
 * copy. Hence the rule tested here.
 */
class SmdhSubtitleTest {

    /**
     * The real case, taken from the European dump: the separator is a line break,
     * not punctuation. The rule's first version missed it because it normalised
     * whitespace before looking for the separator.
     */
    @Test
    fun `le saut de ligne du SMDH separe le titre de son sous-titre`() {
        assertEquals(
            "The Legend of Zelda: A Link Between Worlds",
            fullTitle("The Legend of Zelda", "The Legend of Zelda\nA Link Between Worlds")
        )
    }

    @Test
    fun `un sous-titre apres deux points est repris`() {
        assertEquals(
            "Bravely Default: Flying Fairy",
            fullTitle("Bravely Default", "Bravely Default: Flying Fairy")
        )
    }

    @Test
    fun `un sous-titre apres un tiret cadratin est repris`() {
        assertEquals(
            "Bravely Default: Flying Fairy",
            fullTitle("Bravely Default", "Bravely Default — Flying Fairy")
        )
    }

    /** The case that forbids taking the long one systematically. */
    @Test
    fun `une accroche sur la seconde ligne est ecartee`() {
        assertEquals(
            "Mario Kart 7",
            fullTitle("Mario Kart 7", "Mario Kart 7\nRace your friends!")
        )
    }

    @Test
    fun `une accroche collee au titre est ecartee`() {
        assertEquals(
            "Mario Kart 7",
            fullTitle("Mario Kart 7", "Mario Kart 7 is back and faster")
        )
    }

    @Test
    fun `une longue sans rapport est ecartee`() {
        assertEquals(
            "Luigi's Mansion",
            fullTitle("Luigi's Mansion", "Chase ghosts through a haunted manor")
        )
    }

    @Test
    fun `une longue identique a la courte ne change rien`() {
        assertEquals("Majora's Mask 3D", fullTitle("Majora's Mask 3D", "Majora's Mask 3D"))
    }

    @Test
    fun `une longue vide ne change rien`() {
        assertEquals("Persona Q", fullTitle("Persona Q", ""))
    }
}
