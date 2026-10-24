package eu.emufii.app.ds

import eu.emufii.app.profile.Profile
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.security.MessageDigest

class DsHleHostAccessTest {
    private val key="11".repeat(32)
    private val token="22".repeat(32)
    private val rom="33".repeat(32)
    private val invitation="https://party.lepast.fr/nifi2/join/#synthetic-fixture"
    private fun prepared()=JSONObject().put("version",1).put("generation",1)
        .put("lobbyInvitations",listOf(invitation,invitation)).put("gameInvitations",listOf(invitation,invitation)).toString().toByteArray()

    @Test fun hostCallsRemainBoundToSessionOwnerAndContainOnlyRomMetadata() {
        val calls=mutableListOf<Triple<String,JSONObject?,String?>>()
        val access=DsHleHostAccess(null,"DS-GOLD",token,Profile("111111111112","Host"),key) {path,method,body,bearer,limit->
            assertEquals("POST",method);assertTrue(limit in 1..262144)
            calls.add(Triple(path,body,bearer))
            Result.success(if(path.endsWith("/prepare"))prepared() else if(path.endsWith("/gateway-ticket"))
                JSONObject().put("ticket","A".repeat(43)).toString().toByteArray() else "{\"ok\":true}".toByteArray())
        }
        val plan=access.prepare(rom,33554432)
        assertEquals(1L,plan.generation);assertEquals(2,plan.gameInvitations.size)
        assertEquals("A".repeat(43),access.gatewayTicket(1,"Sroute",1,"server",System.currentTimeMillis()+60000))
        access.publish(1);access.close();access.close()
        assertEquals(4,calls.size)
        assertTrue(calls.all {it.first.startsWith("/sessions/DS-GOLD/ds/gold/")&&it.third==token})
        assertTrue(calls.all {it.second!!.getString("id")=="111111111112"&&it.second!!.getString("key")==key})
        assertEquals(setOf("id","key","romSha256","romBytes"),calls.first().second!!.keys().asSequence().toSet())
        assertThrows(IllegalStateException::class.java){access.publish(1)}
    }

    @Test fun malformedPlanAndUnprivilegedGatewayRoleCannotReachNativeRuntime() {
        var count=0
        val access=DsHleHostAccess(null,"DS-GOLD",token,Profile("111111111112","Host"),key) {_,_,_,_,_->
            count++;Result.success(prepared())
        }
        access.prepare(rom,33554432)
        assertThrows(IllegalArgumentException::class.java){access.gatewayTicket(1,"Sroute",1,"client",System.currentTimeMillis()+60000)}
        assertEquals(1,count)
        val malformed=DsHleHostAccess(null,"DS-GOLD",token,Profile("111111111112","Host"),key) {_,_,_,_,_->
            Result.success(JSONObject(String(prepared())).put("generation",1.5).toString().toByteArray())
        }
        assertThrows(IllegalArgumentException::class.java){malformed.prepare(rom,33554432)}
    }

    @Test fun preparationIsHashBoundAndItsResponseMemoryIsErasedOnClose() {
        val bytes=ByteArray(100){it.toByte()}
        val sha=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(""){"%02x".format(it.toInt() and 255)}
        val expected=bytes.copyOf()
        val access=DsHleHostAccess(null,"DS-GOLD",token,Profile("111111111112","Host"),key) {path,_,_,_,limit->
            if(path.endsWith("/prepare"))Result.success(prepared()) else {assertEquals(100,limit);Result.success(bytes)}
        }
        access.prepare(rom,33554432)
        access.openPreparation(2,"seat",100,sha).use {assertArrayEquals(expected,it.readBytes())}
        assertTrue(bytes.all {it==0.toByte()})
    }
}
