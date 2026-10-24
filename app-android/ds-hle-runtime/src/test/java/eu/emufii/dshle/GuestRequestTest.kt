package eu.emufii.dshle

import org.junit.Assert.*
import org.junit.Test

class GuestRequestTest {
    @Test fun acceptsOnlyPublicRoomSelection() {
        assertEquals(GuestRequest("AB2D", "Lé Past"), GuestRequest.parse(" ab2d ", " Lé Past "))
        assertEquals(GuestRequest("AB2D", "LePast"), GuestRequest.parse("AB2D", "\ufeff\u00a0LePast\u00a0\ufeff"))
        for (input in listOf("ABC-234", "AB2D?relay=evil", "https://party.lepast.fr/join/AB2D",
            "wantapparty://join/AB2D", "ＡB2D", "AB\nD", "AB2DE")) {
            assertNull(input, GuestRequest.parse(input, "LePast"))
        }
    }

    @Test fun rejectsNamesTheNativeProtocolCannotRepresent() {
        for (name in listOf("", "   ", "12345678901", "ab\u202Ecd", "a\u0000b", "a\uD83D\uDE00")) {
            assertNull(GuestRequest.parse("AB2D", name))
        }
        assertNotNull(GuestRequest.parse("AB2D", "1234567890"))
    }

    @Test fun unsupportedAbiNeverSelectsThisNativeRuntime() {
        assertFalse(GuestRequest.supports(listOf("x86_64", "x86")))
        assertFalse(GuestRequest.supports(listOf("armeabi-v7a")))
        assertTrue(GuestRequest.supports(listOf("arm64-v8a", "armeabi-v7a")))
    }
}
