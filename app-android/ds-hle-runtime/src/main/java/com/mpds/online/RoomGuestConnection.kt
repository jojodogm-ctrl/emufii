package com.mpds.online

import java.io.Closeable
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/** Owns the real directory invitation -> authenticated gateway -> persistent
 * lobby chain. Call open/close on RoomGuestSession's round worker, not UI.
 * The UI may use closeAsync; the session worker must await cleanup before rejoin.
 */
internal class RoomGuestConnection private constructor(
    private val admissionOwner: RoomInvitation,
    val lobby: RoomGuestLobby
) : Closeable {
    fun closeAsync():CompletableFuture<Unit> {
        admissionOwner.close()
        return lobby.closeAsync()
    }
    override fun close() { closeAsync().get(30,TimeUnit.SECONDS) }

    companion object {
        fun open(round:RoomGuestSession.Round, name:String,
                 startGame:(RoomGuestLobby.GameRequest)->RoomGuestLobby.GameHandle,
                 validateSave:(String,ByteArray)->Unit,
                 onChange:(RoomGuestLobby.Status)->Unit,
                 onDisconnected:(RoomDirectory.Player)->Unit,
                 onError:(String)->Unit,authorityCheckpoints:Boolean=false):RoomGuestConnection {
            round.checkCurrent()
            val owner=RoomInvitation({ if(round.isCancelled)null else round.snapshot })
            var channel:RoomLobbyChannel?=null
            var lobby:RoomGuestLobby?=null
            try {
                val admitted=owner.admit(round.launch.invitation,round.snapshot)
                // Preparation confers no game authority. Only now may its ROM
                // become a candidate for the signed manifest's normal receiver.
                round.checkCurrent()
                round.prefetch?.let { if (!it.acceptsManifest(admitted)) it.close() }
                // Attach both sides before starting any network callbacks.
                val lane=RoomLobbyChannel.open(round,admitted,
                    onMessage={ lobby!!.receive(it) },
                    onClosed={ reason -> owner.close();lobby?.transportClosed(reason) },startImmediately=false)
                channel=lane
                val session=RoomGuestLobby(admitted,round.snapshot,lane,name,startGame,onChange,onError,
                    onDisconnected,validateSave,
                    authorityCheckpoints=round.launch.canOfferAuthorityCheckpoints(authorityCheckpoints,round.snapshot.settings.game),
                    checkpointFactory={binding,checkpointOwner,current,onClosed->
                        RoomCheckpointLane.open(round,admitted,binding,checkpointOwner,current,onClosed)
                    })
                lobby=session
                session.open();round.checkCurrent();lane.start()
                return RoomGuestConnection(owner,session)
            } catch(e:Exception) {
                owner.close();lobby?.close();channel?.close();throw e
            }
        }
    }
}
