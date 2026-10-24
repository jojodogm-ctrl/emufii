package eu.emufii.app.ds

import eu.emufii.app.profile.Profile
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.Base64

class DsHleGuestAccessTest {
    private val identity="11".repeat(32)
    private val member="22".repeat(32)
    private val native=Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32){3})
    private fun admission()="""{"protocol":"emufii-ds-session-v1","code":"ABCD","seat":2}""".toByteArray()

    @Test fun nativeDirectoryTokenNeverReplacesPrivateMemberAuthorization() {
        val calls=mutableListOf<Pair<String,JSONObject>>()
        val access=DsHleGuestAccess(null,"PRIVATE",member,Profile("111111111112","Guest"),identity) {path,method,body,bearer,limit->
            assertEquals("POST",method);assertEquals(member,bearer)
            assertTrue(limit in 4096..65536);calls.add(path to body!!)
            Result.success(if(path.endsWith("/admission"))admission() else "{}".toByteArray())
        }
        assertEquals(DsHleGuestAdmission("ABCD",2),access.admission())
        assertEquals(access.admission(),access.admission())
        access.post("ABCD","join",null,"""{"name":"Guest"}""".toByteArray())
        access.post("ABCD","poll",native,"""{"name":"Guest"}""".toByteArray())
        assertEquals(3,calls.size)
        assertEquals("/sessions/PRIVATE/ds/gold/admission",calls[0].first)
        assertEquals("/sessions/PRIVATE/ds/gold/guest/poll",calls[2].first)
        assertTrue(calls.all {it.second.getString("id")=="111111111112"&&it.second.getString("key")==identity})
        assertFalse(calls[1].second.has("nativeToken"))
        assertEquals(native,calls[2].second.getString("nativeToken"))
        assertEquals("ABCD",calls[2].second.getString("roomCode"))
        assertEquals("Guest",calls[2].second.getJSONObject("request").getString("name"))
    }

    @Test fun unrelatedCodeUnapprovedOperationAndRetiredMembershipAreRejectedBeforeTransport() {
        var calls=0
        val access=DsHleGuestAccess(null,"PRIVATE",member,Profile("111111111112","Guest"),identity) {_,_,_,_,_->
            calls++;Result.success(admission())
        }
        assertThrows(IllegalArgumentException::class.java){access.post("ABCD","join",null,"{}".toByteArray())}
        access.admission()
        assertThrows(IllegalArgumentException::class.java){access.post("WXYZ","join",null,"{}".toByteArray())}
        assertThrows(IllegalArgumentException::class.java){access.post("ABCD","../gateway-ticket",native,"{}".toByteArray())}
        assertThrows(IllegalArgumentException::class.java){access.post("ABCD","join",native,"{}".toByteArray())}
        access.close()
        assertThrows(IllegalStateException::class.java){access.post("ABCD","poll",native,"{}".toByteArray())}
        assertEquals(1,calls)
    }
}
