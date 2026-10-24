package com.mpds.online

import org.junit.Assert.*
import org.junit.Test
import java.util.Base64

/** Private transport injection retains upstream seat/token/snapshot checks. */
class PrivateGuestDirectoryTest {
    private val token=Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32){1})
    private fun snapshot(code:String="ABCD",revision:Int=1)=
        """{"code":"$code","revision":$revision,"settings":{"game":"mpds","players":2,"askPlayerSaves":false,"sendROM":true},"roster":[{"seat":1,"name":"Hôte","connected":true},{"seat":2,"name":"Émile","connected":true}],"phase":"waiting","generation":0,"launch":null}"""

    @Test fun applicationTransportRetainsNativeBearerAndClearsItOnLeave() {
        val calls=mutableListOf<Triple<String,String?,String>>()
        val directory=RoomDirectory(RoomDirectory.Transport {code,operation,bearer,body->
            assertEquals("ABCD",code)
            calls.add(Triple(operation,bearer,body.toString(Charsets.UTF_8)))
            val value=when(operation) {
                "join"->"""{"code":"ABCD","token":"$token","seat":2,"snapshot":${snapshot()}}"""
                "poll"->snapshot(revision=2)
                "leave"->"""{"ok":true}"""
                else->error("unexpected operation")
            }
            RoomDirectory.Response(200,"application/json",null,value.toByteArray(Charsets.UTF_8))
        })
        assertEquals(2,directory.join("ABCD","Émile").seat)
        assertEquals(2L,directory.poll("Émile").revision)
        directory.leave()
        assertNull(directory.snapshot)
        assertEquals(listOf("join","poll","leave"),calls.map {it.first})
        assertNull(calls[0].second)
        assertEquals(token,calls[1].second);assertEquals(token,calls[2].second)
        assertEquals("""{"name":"Émile"}""",calls[0].third)
    }

    @Test fun signedApplicationTransportCannotChangeTheAdmittedNativeCode() {
        val directory=RoomDirectory(RoomDirectory.Transport {_,operation,_,_->
            val value=if(operation=="join")
                """{"code":"ABCD","token":"$token","seat":2,"snapshot":${snapshot()}}"""
                else snapshot("WXYZ",2)
            RoomDirectory.Response(200,"application/json",null,value.toByteArray(Charsets.UTF_8))
        })
        directory.join("ABCD","Émile")
        assertThrows(RoomDirectory.DirectoryError::class.java){directory.poll("Émile")}
    }
}
