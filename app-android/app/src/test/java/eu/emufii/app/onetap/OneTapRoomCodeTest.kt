package eu.emufii.app.onetap

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OneTapRoomCodeTest {
    @Test
    fun `codes and trusted production invites select the same room`() {
        for (value in listOf(" ab2d ", "AB2D", "https://party.lepast.fr/join/ab2d",
            "https://party.lepast.fr/play/#room/ab2d", "wantapparty://join/ab2d",
            "wantap-nifi2://join/AB2D", "wantapparty://ab2d")) {
            assertEquals(value, "AB2D", OneTapRoomCode.parse(value))
        }
    }

    @Test
    fun `foreign origins beta and credentials cannot become room codes`() {
        for (value in listOf("ABC234", "ABC-234", "ABC", "ABCDE", "ÉBCD", "abß", "ıBCD", "AB/D", "AB D", "",
            "https://beta.party.lepast.fr/join/AB2D", "nemul://join/AB2D",
            "http://party.lepast.fr/join/AB2D", "https://party.lepast.fr.evil.invalid/join/AB2D",
            "https://user@party.lepast.fr/join/AB2D", "https://party.lepast.fr:443/join/AB2D",
            "https://party.lepast.fr/join/AB2D?token=secret", "https://party.lepast.fr/join/AB2D#secret",
            "https://party.lepast.fr/join/%4142D", "https://party.lepast.fr/nifi2/join/#secret",
            "https://party.lepast.fr/play/?code=AB2D#room/AB2D",
            "https://party.lepast.fr/play/#room/AB2D/other", "wantapparty://join/AB2D?name=x")) {
            assertNull(value, OneTapRoomCode.parse(value))
        }
    }

    @Test
    fun `entry URLs are fixed production application URLs`() {
        assertEquals("https://party.lepast.fr/play/#room/AB2D", OneTapEntry.browserRoom(" ab2d "))
        assertEquals("wantapparty://join/AB2D", OneTapEntry.nativeRoom("ab2d"))
        assertEquals("https://party.lepast.fr/join/AB2D", OneTapEntry.publicInvite("ab2d"))
        assertEquals("com.mpds.online.singleowner.dev", OneTapEntry.NATIVE_PACKAGE)
    }
}
