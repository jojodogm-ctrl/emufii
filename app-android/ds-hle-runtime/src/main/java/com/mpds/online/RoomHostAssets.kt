package com.mpds.online

import android.content.Context
import android.net.Uri
import eu.emufii.dshle.DsHleRuntime
import java.io.Closeable
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/** Private immutable copies; supplied content URIs/preparation streams never
 * become emulator paths. Every seat's manifest is signed and hash checked. */
internal class RoomHostAssets(private val root:File,private val current:()->Unit) : Closeable {
    private val owned=mutableListOf<RoomAssetReceiver.Assets>()
    fun stage(context:Context,rom:Uri,admission:RoomInvitation.Admission,
        access:DsHleRuntime.HostAccess):RoomAssetReceiver.Assets {
        current();val entries=RoomAssetManifest.entries(admission,host=true)
        val directory=Files.createTempDirectory(root.toPath(),"host-assets-").toFile()
        val files=entries.associate {it.kind to File(directory,it.kind+".bin")}
        val owner=RoomAssetReceiver.Assets(directory,files,entries) {
            try {current();admission.isCurrent()} catch(_:Exception){false}
        }
        try {
            for(entry in entries) {
                current()
                val input=if(entry.kind=="rom")context.contentResolver.openInputStream(rom)
                    else access.openPreparation(RoomInvitation.number(admission.room["seat"]).toInt(),
                        entry.kind,entry.bytes,entry.sha256)
                requireNotNull(input){"host-asset-source"}.use {copyVerified(it,files.getValue(entry.kind),entry)}
            }
            owned.add(owner);return owner
        } catch(e:Throwable){owner.close();throw e}
    }
    private fun copyVerified(input:InputStream,target:File,entry:RoomAssetManifest.Entry) {
        val digest=MessageDigest.getInstance("SHA-256");var count=0L
        target.outputStream().use {out->
            val buffer=ByteArray(65536)
            while(count<entry.bytes) {
                current();val n=input.read(buffer,0,minOf(buffer.size.toLong(),entry.bytes-count).toInt())
                check(n>0){"host-asset-length"};digest.update(buffer,0,n);out.write(buffer,0,n);count+=n
            }
            check(input.read()==-1){"host-asset-length"}
        }
        current();check(count==entry.bytes&&RoomInvitation.hex(digest.digest())==entry.sha256){"host-asset-hash"}
    }
    fun send(round:RoomGuestSession.Round,admission:RoomInvitation.Admission,
        assets:RoomAssetReceiver.Assets,onProgress:(Long,Long)->Unit) {
        val entries=RoomAssetManifest.entries(admission)
        require(assets.manifest==entries&&assets.isCurrent())
        var channel:RoomGatewayChannel?=null
        val deadline=RoomAssetWire.deadlineAfter(300000)
        fun ensure(){current();round.checkCurrent();check(admission.isCurrent()&&assets.isCurrent());
            check(System.nanoTime()<deadline&&channel?.isClosed!=true)}
        val wire=RoomAssetWire(send={bytes->ensure();channel?.send(bytes)?.get(10,TimeUnit.SECONDS)==true},ensureCurrent=::ensure)
        val timer=java.util.concurrent.Executors.newSingleThreadScheduledExecutor {Thread(it,"hle-host-assets").apply {isDaemon=true}}
        try {
            val opened=RoomGatewayChannel.openHost(round,admission,admission.assets,true,wire::push,{wire.close()})
            channel=opened
            timer.scheduleWithFixedDelay({
                try{ensure();wire.writable()}catch(_:Exception){wire.close("host-asset-retired");opened.close()}
            },20,20,TimeUnit.MILLISECONDS)
            opened.ready.get(30,TimeUnit.SECONDS);ensure();wire.authenticateSender(admission.assets.capability,deadlineNanos=deadline)
            var total=0L;val expected=entries.sumOf {it.bytes}
            for(entry in entries) {
                val request=wire.frame(RoomAssetWire.REQUEST,deadlineNanos=deadline)
                val reuse=request.contentEquals(entry.metadata(true))
                check(reuse||request.contentEquals(entry.metadata())){"host-asset-request"}
                check(admission.room["sendROM"]!=false||entry.kind!="rom"||reuse){"host-asset-rom-required"}
                if(!reuse)assets.files.getValue(entry.kind).inputStream().use {input->
                    var count=0L
                    while(count<entry.bytes){ensure();val length=minOf(65536L,entry.bytes-count).toInt()
                        val bytes=ByteArray(length);var at=0
                        while(at<length){val n=input.read(bytes,at,length-at);check(n>0);at+=n}
                        wire.send(RoomAssetWire.DATA,bytes,deadline);bytes.fill(0);count+=length;total+=length;onProgress(total,expected)}
                    check(input.read()==-1)
                } else {total+=entry.bytes;onProgress(total,expected)}
                wire.send(RoomAssetWire.END,entry.metadata(),deadline)
            }
            check(wire.read(1,deadline).contentEquals(byteArrayOf(RoomAssetWire.COMMITTED.toByte())))
            check(wire.pendingBytes==0){"host-asset-trailing"}
            val grace=System.nanoTime()+TimeUnit.SECONDS.toNanos(1)
            while(System.nanoTime()<grace||!opened.drained().get(10,TimeUnit.SECONDS)) {ensure();Thread.sleep(20)}
            ensure();check(wire.pendingBytes==0)
        } finally {timer.shutdownNow();wire.close();channel?.close()}
    }
    override fun close(){owned.asReversed().forEach {it.close()};owned.clear()}
}
