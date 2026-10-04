package eu.emufii.app.secondscreen

import eu.emufii.app.library.Console
import eu.emufii.app.session.Session
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SecondScreenTest {

    @After fun reset() = SecondScreen.clear()

    @Test fun `the panel follows the app when there is no session`() {
        assertTrue(secondScreenWanted(enabled = true, foreground = true, model = SecondScreenModel.Idle))
        assertFalse(secondScreenWanted(enabled = true, foreground = false, model = SecondScreenModel.Idle))
    }

    @Test fun `a running session keeps the panel lit behind the emulator`() {
        assertTrue(secondScreenWanted(enabled = true, foreground = false, model = inSession()))
    }

    @Test fun `the setting wins over everything`() {
        assertFalse(secondScreenWanted(enabled = false, foreground = true, model = inSession()))
    }

    @Test fun `starts idle`() {
        assertEquals(SecondScreenModel.Idle, SecondScreen.model.value)
    }

    @Test fun `publishes a session and reads it back`() {
        SecondScreen.publish(
            SecondScreenModel.InSession(
                code = "HMM-295",
                role = Session.Role.HOST,
                console = Console.PS2,
                gameTitle = "Midnight Club 3",
            )
        )
        val model = SecondScreen.model.value
        assertTrue(model is SecondScreenModel.InSession)
        assertEquals("HMM-295", (model as SecondScreenModel.InSession).code)
    }

    @Test fun `clearing returns to idle`() {
        SecondScreen.publish(
            SecondScreenModel.InSession(
                code = "ABC-234",
                role = Session.Role.GUEST,
                console = null,
                gameTitle = null,
            )
        )
        SecondScreen.clear()
        assertEquals(SecondScreenModel.Idle, SecondScreen.model.value)
    }

    @Test fun `a folder claims only the keys that do something`() {
        val legend = SecondScreenModel.ConsoleFolder(Console.PS2).legend
        assertEquals(listOf(PadHint.BACK), legend.left)
        assertEquals(listOf(PadHint.CONFIRM), legend.right)
    }

    private fun inSession() = SecondScreenModel.InSession(
        code = "HMM-295",
        role = Session.Role.HOST,
        console = Console.PS2,
        gameTitle = "Midnight Club 3",
    )
}
