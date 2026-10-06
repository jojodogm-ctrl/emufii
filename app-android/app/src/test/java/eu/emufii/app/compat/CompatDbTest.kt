package eu.emufii.app.compat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
        val db = CompatDb.parse(
            """
            {"games": [
              {"name": "Broken", "rating": "excellent", "keys": ["ps2:A"]},
              {"name": "No key", "rating": "perfect", "keys": []},
              {"name": "Bon", "rating": "partial", "keys": ["ps2:B"]}
            ]}
            """.trimIndent()
        )
        assertEquals(1, db.size)
        assertEquals(CompatRating.PARTIAL, db.ratingFor(listOf("ps2:B"))?.rating)
        assertNull(db.ratingFor(listOf("ps2:A")))
    }

    @Test
    fun `junk parses to an empty database rather than throwing`() {
        assertEquals(0, CompatDb.parse("<html>captive portal</html>").size)
        assertEquals(0, CompatDb.parse("").size)
    }

    @Test
    fun `a PS2 entry carries its servers and live count sources`() {
        val db = CompatDb.parse(
            """{"games":[{"name":"SOCOM II","rating":"broken","online":"perfect","keys":["ps2:SCUS-97275"],
               "servers":["psrewired","ps2online"],"live":{"psrewired":[10472,10481],"ps2online":["SOCOM II"]}}]}"""
        )
        val entry = db.ratingFor(listOf("ps2:SCUS-97275"))!!
        assertEquals(listOf("psrewired", "ps2online"), entry.servers)
        assertEquals(listOf(10472, 10481), entry.psrewiredApps)
        assertEquals(listOf("SOCOM II"), entry.ps2onlineRows)
    }

    @Test
    fun `an entry without live sources parses as before`() {
        val entry = CompatDb.parse("""{"games":[{"name":"X","rating":"perfect","keys":["ps2:SLUS-00001"]}]}""")
            .ratingFor(listOf("ps2:SLUS-00001"))!!
        assertTrue(entry.servers.isEmpty() && entry.psrewiredApps.isEmpty() && entry.ps2onlineRows.isEmpty())
    }
}
