package com.mpds.online

import org.junit.Assert.*
import org.junit.Test
import java.util.ArrayDeque
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/** Exercises the new host protocol against the retained upstream guest half. */
class HostProtocolTest {
    @Test fun authenticatedServerCarriesBothStreamDirectionsAndKeepsIdentity() {
        var now=1000.0;val wall=1700000000000L
        val config=RoomInvitation.Config("192.0.2.1",443,"room",1,"0123456789abcdef0123456789abcdef",600,wall+600000,0)
        val toServer=ArrayDeque<ByteArray>();val toGuest=ArrayDeque<ByteArray>()
        val atServer=mutableListOf<ByteArray>();val atGuest=mutableListOf<ByteArray>()
        val key=ByteArray(32){(it+1).toByte()}
        val server=RoomRelayPeer(config.server(),key,true,{toGuest.add(it.copyOf());true},
            {atServer.add(it.copyOf());true},{},now={now},wall={wall+now.toLong()})
        val guest=RoomRelayPeer(config,key,true,{toServer.add(it.copyOf());true},
            {atGuest.add(it.copyOf());true},{},now={now},wall={wall+now.toLong()})
        fun pump(steps:Int=50) {repeat(steps){
            server.tick();guest.tick()
            repeat(16){toServer.pollFirst()?.let(server::receive);toGuest.pollFirst()?.let(guest::receive)}
            now+=20
        }}
        server.start();guest.start();pump()
        assertTrue(server.authenticated&&guest.authenticated&&server.ready&&guest.ready)
        val a=ByteArray(4000){(it%251).toByte()};val b=ByteArray(5000){((it+31)%251).toByte()}
        assertTrue(guest.send(a));assertTrue(server.send(b));pump()
        assertArrayEquals(a,atServer.fold(ByteArray(0)){acc,bytes->acc+bytes})
        assertArrayEquals(b,atGuest.fold(ByteArray(0)){acc,bytes->acc+bytes})
        assertEquals(0,server.outgoingRecords);assertEquals(0,guest.outgoingRecords)
        // Another possession-of-capability claim cannot replace the pinned peer.
        val intruder=RoomRelayPeer(config,key,true,{server.receive(it);true},{false},{},now={now},wall={wall+now.toLong()})
        intruder.start();pump(10)
        assertFalse(intruder.authenticated);assertTrue(server.ready)
        assertTrue(guest.send(byteArrayOf(17,18)));pump()
        assertArrayEquals(byteArrayOf(17,18),atServer.last())
        intruder.close();guest.close();server.close()
    }

    @Test fun waitingServerDoesNotExpireBeforeAClientHandshake() {
        var now=1000.0;val wall=1700000000000L
        val config=RoomInvitation.Config("192.0.2.1",443,"room",1,"0123456789abcdef0123456789abcdef",600,wall+600000,0,"server")
        val server=RoomRelayPeer(config,ByteArray(32){1},false,{true},{true},{},now={now},wall={wall+now.toLong()})
        server.start();now+=40000;server.tick()
        assertFalse(server.closed);assertFalse(server.authenticated);assertFalse(server.ready)
        now+=600000;server.tick();assertTrue(server.closed);assertEquals("ttl-expired",server.reason)
    }

    @Test fun hostAssetStreamSurvivesConsumerPressureLossAndDuplicates() {
        var now=1000.0;val wall=1700000000000L
        val config=RoomInvitation.Config("192.0.2.1",443,"room",1,"0123456789abcdef0123456789abcdef",600,wall+600000,0)
        val toServer=ArrayDeque<ByteArray>();val toGuest=ArrayDeque<ByteArray>()
        val received=java.io.ByteArrayOutputStream()
        val key=ByteArray(32){(it+1).toByte()}
        var holdConsumer=false;var dropped=false;var records=0
        val server=RoomRelayPeer(config.server(),key,true,packet@{bytes->
            if(bytes.size>=4&&bytes[0]==78.toByte()&&bytes[1]==50.toByte()&&bytes[2]==68.toByte()&&bytes[3]==49.toByte()) {
                records++
                // Lose one sealed record and duplicate later records. The stream
                // must recover using receipts, without duplicating consumer bytes.
                if(holdConsumer&&!dropped){dropped=true;return@packet true}
                toGuest.add(bytes.copyOf())
                if(records%13==0)toGuest.add(bytes.copyOf())
            }else toGuest.add(bytes.copyOf())
            true
        },{true},{},now={now},wall={wall+now.toLong()})
        val guest=RoomRelayPeer(config,key,true,{toServer.add(it.copyOf());true},{bytes->
            if(holdConsumer)false else {received.write(bytes);true}
        },{},now={now},wall={wall+now.toLong()})
        fun step() {
            server.tick();guest.tick()
            repeat(256){toServer.pollFirst()?.let(server::receive);toGuest.pollFirst()?.let(guest::receive)}
            now+=20
        }
        try {
            server.start();guest.start();repeat(50){step()}
            assertTrue(server.ready&&guest.ready)
            val expected=ByteArray(1024*1024+17){((it*31+19)%251).toByte()}
            holdConsumer=true;var sent=0
            repeat(1500){iteration->
                if(iteration==100)holdConsumer=false
                if(sent<expected.size){val end=minOf(sent+65536,expected.size)
                    if(server.send(expected.copyOfRange(sent,end)))sent=end}
                step()
            }
            assertTrue(dropped);assertEquals(expected.size,sent)
            assertFalse(server.closed);assertFalse(guest.closed)
            assertEquals(0,server.outgoingRecords)
            assertArrayEquals(expected,received.toByteArray())
        } finally {guest.close();server.close()}
    }

    @Test fun hostAndGuestMutuallyAuthenticateAssetWireAndNegotiateReuse() {
        lateinit var host:RoomAssetWire;lateinit var guest:RoomAssetWire
        host=RoomAssetWire({guest.push(it)})
        guest=RoomAssetWire({host.push(it)})
        val key=ByteArray(32){(it+1).toByte()};val original=key.copyOf()
        val deadline=RoomAssetWire.deadlineAfter(3000)
        val sent=CompletableFuture.supplyAsync {
            host.authenticateSender(key,reuse=true,deadlineNanos=deadline)
            host.send(RoomAssetWire.END,byteArrayOf(22,23),deadline)
        }
        val auth=guest.authenticateReceiver(key,deadlineNanos=deadline)
        assertTrue(auth.reuse);assertFalse(auth.diagnostics)
        assertArrayEquals(byteArrayOf(22,23),guest.frame(RoomAssetWire.END,deadlineNanos=deadline))
        sent.get(3,TimeUnit.SECONDS);assertArrayEquals(original,key)
        assertEquals(0,host.pendingBytes);assertEquals(0,guest.pendingBytes)
        host.close();guest.close()
    }
}
