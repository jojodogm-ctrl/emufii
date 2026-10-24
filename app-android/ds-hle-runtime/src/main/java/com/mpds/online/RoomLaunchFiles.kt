package com.mpds.online

import java.io.Closeable
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import java.security.MessageDigest
import java.util.Comparator
import java.util.concurrent.atomic.AtomicBoolean

/** Fresh private inputs for one round. Originals received from the host and
 * player saves are never modified. Lifetime must exceed the native process. */
internal class RoomLaunchFiles private constructor(val directory:File) : Closeable {
    private val closed=AtomicBoolean(false)
    override fun close() {
        if(!closed.compareAndSet(false,true))return
        // Files.walk does not follow symlinks. Only our freshly allocated tree.
        Files.walk(directory.toPath()).use { paths ->
            paths.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
        }
    }
    companion object {
        fun stage(admission:RoomInvitation.Admission,assets:RoomAssetReceiver.Assets,
                  names:List<String>,emblems:List<String?>?,personalSave:ByteArray?,
                  privateRoot:File,checkCurrent:()->Unit):RoomLaunchFiles {
            fun active() { checkCurrent();check(admission.isCurrent() && assets.isCurrent()) }
            active();require(privateRoot.isDirectory)
            val result=RoomLaunchFiles(Files.createTempDirectory(privateRoot.toPath(),"room-launch-").toFile())
            try {
                val room=admission.room;val game=room["game"] as? String ?: "mpds"
                val gameId=listOf("nsmb","mkds","mpds","dkr","experimental").indexOf(game)
                require(gameId>=0)
                val players=RoomInvitation.number(room["players"]).toInt()
                val seat=RoomInvitation.number(room["seat"]).toInt()
                val expected=RoomAssetManifest.entries(admission,host=seat==1)
                require(assets.manifest==expected && assets.files.keys==expected.map { it.kind }.toSet())
                for(entry in expected) {
                    active()
                    val source=assets.files.getValue(entry.kind).toPath()
                    require(Files.isRegularFile(source,LinkOption.NOFOLLOW_LINKS))
                    val name=when(entry.kind) { "rom"->"game.nds";"seat"->"seat.bin";else->"boot.bin" }
                    val target=File(result.directory,name)
                    val digest=MessageDigest.getInstance("SHA-256");var total=0L
                    Files.newInputStream(source).use { input -> target.outputStream().use { output ->
                        val buffer=ByteArray(65536)
                        while(true) {
                            active();val n=input.read(buffer);if(n<0)break
                            total+=n;check(total<=entry.bytes)
                            digest.update(buffer,0,n);output.write(buffer,0,n)
                        }
                    } }
                    check(total==entry.bytes && RoomInvitation.hex(digest.digest())==entry.sha256)
                }
                RoomPlayerProfile.files(room,names,emblems).forEach { (name,bytes) ->
                    try { active();File(result.directory,name).writeBytes(bytes) } finally { bytes.fill(0) }
                }
                personalSave?.let { active();File(result.directory,"player.sav").writeBytes(it) }
                val preparation=expected.singleOrNull { it.kind!="rom" }
                if(preparation!=null) {
                    val generated=when(room["profile"]) {
                        "nsmb-eu-generated-cartridge-v1","mkds-menu-amce-generated-v1" -> room["setSha256"] as String
                        else -> ""
                    }
                    active()
                    val input=File(result.directory,if(preparation.kind=="seat")"seat.bin" else "boot.bin")
                    val imported=NativeBridge.nativeStageRoomPreparation(gameId,players,seat,input.absolutePath,
                        result.directory.absolutePath,generated,preparation.sha256)
                    if(imported!=1) {
                        val known=setOf("MKDS generated preparation rejected","MKDS generated manifest rejected",
                            "MKDS preparation short read","preparation staging requires a fresh process",
                            "invalid preparation staging selection","preparation transfer identity rejected",
                            "preparation staging requires private directory","private preparation write failed",
                            "room input size or access rejected","MKDS pinned bundle size rejected",
                            "MKDS preparation metadata rejected","MKDS preparation file bound",
                            "MKDS preparation regular file required","MKDS preparation image identity",
                            "MKDS preparation seat identity","room input changed during read",
                            "room input must be a private regular file","preparation destination already exists",
                            "DKR room rejects linked paths")
                        val reason=NativeBridge.nativeSingleOwnerError()
                        android.util.Log.w("Wantap-room","Preparation importer: " +
                            if(reason in known)reason else "unclassified-native-rejection")
                    }
                    check(imported==1) { "room-preparation-rejected" }
                    active();Files.delete(input.toPath())
                }
                active();return result
            } catch(error:Throwable) {
                try { result.close() } catch(cleanup:Throwable) { error.addSuppressed(cleanup) }
                throw error
            }
        }
    }
}
