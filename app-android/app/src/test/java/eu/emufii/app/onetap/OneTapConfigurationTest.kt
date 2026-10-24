package eu.emufii.app.onetap

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OneTapConfigurationTest {
    @Test
    fun `default deployment stays disabled`() {
        val config = OneTapConfiguration("", "")
        assertNull(config.adapterUrl)
        assertNull(config.frontendUrl)
        assertNull(config.createHostUrl(OneTapGame.MARIO_KART_DS, "LePast"))
    }

    @Test
    fun `creation uses the integrated frontend and encodes only public selection`() {
        val config = OneTapConfiguration("https://adapter.example", "https://party.lepast.fr/emufii/play/")
        assertEquals("https://adapter.example", config.adapterUrl)
        assertEquals("https://party.lepast.fr/emufii/play/?game=mkds&name=LePast",
            config.createHostUrl(OneTapGame.MARIO_KART_DS, "LePast"))
        assertEquals("https://party.lepast.fr/emufii/play/?game=mpds&name=L%C3%A9+Past%26x%3D1",
            config.createHostUrl(OneTapGame.MARIO_PARTY_DS, "Lé Past&x=1"))
    }

    @Test
    fun `production play page and credential bearing URLs never become integrated creation`() {
        for (frontend in listOf("https://party.lepast.fr/play/", "https://beta.party.lepast.fr/play/",
            "https://party.lepast.fr/emufii/play/?token=x", "https://party.lepast.fr/emufii/play/#secret",
            "http://party.lepast.fr/emufii/play/", "https://user@party.lepast.fr/emufii/play/",
            "https://party.lepast.fr:443/emufii/play/")) {
            assertNull(frontend, OneTapConfiguration("", frontend).frontendUrl)
        }
        for (adapter in listOf("https://adapter.example/", "https://adapter.example/v1",
            "http://adapter.example", "https://adapter.example?token=x", "https://user@adapter.example")) {
            assertNull(adapter, OneTapConfiguration(adapter, "").adapterUrl)
        }
    }
}
