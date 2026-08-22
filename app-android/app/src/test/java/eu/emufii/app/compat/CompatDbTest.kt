package eu.emufii.app.compat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CompatDbTest {

    private val sample = """
        {
          "version": 1,
          "games": [
            {
              "name": "TimeSplitters 2",
              "rating": "perfect",
              "keys": ["ps2:SLES-50877", "ps2:SLUS-20314"]
            },
            {
              "name": "Resident Evil 4",
              "rating": "broken",
              "note": "Plante au chargement",
              "keys": ["ps2:SLES-53702"]
            }
          ]
        }
    """.trimIndent()

    @Test
    fun `every region of one game answers with the same verdict`() {
        // The whole feature in one assertion: two unrelated serials, one entry,
        // one badge.
        val db = CompatDb.parse(sample)
        assertEquals(CompatRating.PERFECT, db.ratingFor(listOf("ps2:SLES-50877"))?.rating)
        assertEquals(CompatRating.PERFECT, db.ratingFor(listOf("ps2:SLUS-20314"))?.rating)
    }

    @Test
    fun `a game nobody has rated has no verdict, rather than a good one`() {
        assertNull(CompatDb.parse(sample).ratingFor(listOf("ps2:SLES-99999")))
    }

    @Test
    fun `the most specific key wins`() {
        // Keys arrive exact-first, so a rating aimed at one region beats the
        // family's. That is what lets one Japanese dump be marked broken without
        // splitting the game in two.
        val db = CompatDb.parse(
            """
            {"games": [
              {"name": "Jeu", "rating": "perfect", "keys": ["3ds:ARR"]},
              {"name": "Jeu (JP)", "rating": "broken", "keys": ["3ds:ARRJ"]}
            ]}
            """.trimIndent()
        )
        assertEquals(CompatRating.BROKEN, db.ratingFor(listOf("3ds:ARRJ", "3ds:ARR"))?.rating)
        assertEquals(CompatRating.PERFECT, db.ratingFor(listOf("3ds:ARRP", "3ds:ARR"))?.rating)
    }

    @Test
    fun `a malformed entry costs one game, not the database`() {
        // This file is edited by hand as well as by the tool. One bad line must
        // not blank every badge in the app.
        val db = CompatDb.parse(
            """
            {"games": [
              {"name": "Cassé", "rating": "excellent", "keys": ["ps2:A"]},
              {"name": "Sans clé", "rating": "perfect", "keys": []},
              {"name": "Bon", "rating": "partial", "keys": ["ps2:B"]}
            ]}
            """.trimIndent()
        )
        assertEquals(1, db.size)
        assertEquals(CompatRating.PARTIAL, db.ratingFor(listOf("ps2:B"))?.rating)
        // An unknown rating is skipped and never defaulted: defaulting would
        // invent a verdict, which is the one thing a compatibility list must not
        // do.
        assertNull(db.ratingFor(listOf("ps2:A")))
    }

    @Test
    fun `junk parses to an empty database rather than throwing`() {
        assertEquals(0, CompatDb.parse("<html>captive portal</html>").size)
        assertEquals(0, CompatDb.parse("").size)
    }
}
