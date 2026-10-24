package com.mpds.online

import android.content.Context
import eu.emufii.dshle.DsHleRuntime
import java.io.Closeable
import java.io.File
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Port of host-lobby/browser-host: signed outer/inner admissions, true SDK
 * server carriers, committed per-seat assets, then the collective start barrier.
 * This owner never calls frame methods, creates a second NDS or uploads a ROM. */
internal class RoomHostSession(
    private val context:Context,
    private val registration:DsHleRuntime.HostRegistration,
    private val root:File,
    private val onStatus:(String,Boolean)->Unit,
    private val onReady:(Int,()->Unit)->Closeable
) : Closeable {
    private val request=registration.request
    private val access=registration.access
    private val closed=AtomicBoolean(false)
    private val worker=Executors.newSingleThreadScheduledExecutor {Thread(it,"hle-host-owner").apply {isDaemon=true}}
    private val gameWorker=Executors.newSingleThreadExecutor {Thread(it,"hle-host-game").apply {isDaemon=true}}
    // Every admitted guest opens its receiver immediately. A queued seat would
    // consume its handshake/start deadline before its server carrier existed.
    private val transfers=Executors.newFixedThreadPool(request.players-1) {Thread(it,"hle-host-transfer").apply {isDaemon=true}}
    private val termination=CompletableFuture<Unit>()
    private val pendingInput=java.util.concurrent.atomic.AtomicInteger()
    private val pendingInputBytes=java.util.concurrent.atomic.AtomicInteger()
    private val outerOwners=mutableListOf<RoomInvitation>()
    private val roundOwners=mutableListOf<RoomRoundAdmission>()
    private val outer=mutableListOf<RoomInvitation.Admission>()
    private val seats=mutableListOf<RoomInvitation.Admission>()
    private val channels=java.util.concurrent.ConcurrentHashMap<Int,RoomLobbyChannel>()
    private val names=MutableList(request.players) {if(it==0)request.playerName else ""}
    private val emblems=MutableList<String?>(request.players) {null}
    private val connected=mutableSetOf(1)
    private val hello=mutableMapOf<Int,Map<String,Any?>>()
    private val assets=RoomHostAssets(root,::ensure)
    private val staged=mutableListOf<RoomAssetReceiver.Assets>()
    private var plan:DsHleRuntime.HostPlan?=null
    private var round:RoomGuestSession.Round?=null
    private var gameRequest:RoomGuestLobby.GameRequest?=null
    @Volatile private var game:RoomNativeGame?=null
    private var started=false
    private var phase="waiting"
    private var generation=0L
    private val deliveriesOwned=java.util.concurrent.CopyOnWriteArrayList<CompletableFuture<*>>()
    private fun ensure(){check(!closed.get()){"host-retired"}}
    private fun status(text:String,ready:Boolean=false){try{onStatus(text,ready)}catch(_:Exception){}}
    /** Every close path, including an internal failure, reaches this same
     * result only after the native lease and local owners have retired. */
    fun onTermination(observer:(Throwable?)->Unit) {
        termination.whenComplete {_,error->observer(error)}
    }
    fun open() {worker.execute {try{prepare()}catch(error:Throwable){
        if(BuildConfig.DEBUG) {
            // Only fixed protocol error codes enter logcat, never capabilities,
            // paths, request bodies or exception stack traces.
            val code=error.message?.takeIf {it.length<=80&&it.matches(Regex("[a-z]+(?:-[a-z0-9]+)+"))}
            android.util.Log.e("EMUFII-DS-HLE","host-prepare: ${error.javaClass.simpleName}${code?.let {":$it"}.orEmpty()}")
        }
        fail("Préparation du salon impossible.")
    }}}
    private fun prepare() {
        ensure();status("Vérification du jeu local…")
        val digest=MessageDigest.getInstance("SHA-256");var bytes=0L
        requireNotNull(context.contentResolver.openInputStream(request.romUri)).use {input->
            val chunk=ByteArray(65536)
            while(true){ensure();val n=input.read(chunk);if(n<0)break;bytes+=n
                check(bytes<=67108864){"host-rom-size"};digest.update(chunk,0,n)}
        }
        require(bytes>=512)
        val romHash=RoomInvitation.hex(digest.digest())
        val prepared=access.prepare(romHash,bytes);ensure()
        require(prepared.generation in 1..1000000&&prepared.lobbyInvitations.size==request.players&&
            prepared.gameInvitations.size==request.players)
        plan=prepared
        val settings=RoomDirectory.Settings(RoomDirectory.Game.valueOf(request.game.uppercase(java.util.Locale.ROOT)),
            request.players,false,true,if(request.game=="experimental")romHash else null,
            if(request.game=="experimental")bytes else null,
            if(request.game=="experimental")RoomDirectory.GuestBoot.CARTRIDGE else null)
        for(index in 0 until request.players) {
            val snapshot=RoomDirectory.Snapshot(request.roomCode,index+1,1,settings,emptyList(),
                RoomDirectory.Phase.RUNNING,prepared.generation)
            val owner=RoomInvitation({if(!closed.get())snapshot else null})
            outerOwners.add(owner)
            val lobby=owner.admit(prepared.lobbyInvitations[index],snapshot)
            require(lobby.room["lobbyProtocol"] in listOf(1.0,2.0)&&lobby.room["shareAssets"]==true)
            outer.add(lobby)
            val roundOwner=RoomRoundAdmission(lobby,snapshot);roundOwners.add(roundOwner)
            val inner=roundOwner.admit(prepared.generation,files(prepared.gameInvitations[index]))
            seats.add(inner)
            require(RoomAssetManifest.entries(inner,host=true).first().let {it.bytes==bytes&&it.sha256==romHash})
            // stage() verifies actual content against every signed manifest.
            status("Préparation de P${index+1}…")
            staged.add(assets.stage(context,request.romUri,inner,access));ensure()
        }
        val hostSnapshot=RoomDirectory.Snapshot(request.roomCode,1,1,settings,emptyList(),
            RoomDirectory.Phase.RUNNING,prepared.generation)
        val activeRound=RoomGuestSession.Round(hostSnapshot,RoomDirectory.Launch(prepared.generation,
            prepared.lobbyInvitations[0],1,1),null,hostCurrent={!closed.get()},hostTicket={route,seat,role,expiry->
                ensure();access.gatewayTicket(prepared.generation,route,seat,role,expiry).also {ensure()}
            })
        round=activeRound
        for(index in 1 until request.players) {
            val seat=index+1
            channels[seat]=RoomLobbyChannel.openHost(activeRound,outer[index],{message->
                val size=RoomJson.encode(message).toByteArray(Charsets.UTF_8).size
                if(!closed.get()) {
                    val count=pendingInput.incrementAndGet();val retained=pendingInputBytes.addAndGet(size)
                    if(count>64||retained>262144){pendingInput.decrementAndGet();pendingInputBytes.addAndGet(-size);fail("Salon surchargé.")}
                    else try {worker.execute {
                        try{if(!closed.get())receive(seat,message)}catch(_:Exception){fail("Protocole du salon interrompu.")}
                        finally{pendingInput.decrementAndGet();pendingInputBytes.addAndGet(-size)}
                    }}catch(_:Exception){pendingInput.decrementAndGet();pendingInputBytes.addAndGet(-size)}
                }
            },{if(!closed.get())fail("P$seat s’est déconnecté.")})
        }
        ensure();access.publish(prepared.generation);ensure()
        status("Salon ${request.roomCode} — attente des joueurs")
        worker.scheduleWithFixedDelay({if(!closed.get()&&outer.any {!it.isCurrent()})fail("Le salon a expiré.")},1,1,TimeUnit.SECONDS)
    }
    private fun receive(seat:Int,message:Map<String,Any?>) {
        ensure()
        when(message["type"]) {
            "hello"->{
                require(phase=="waiting"&&message.keys.all {it in HELLO_FIELDS})
                val name=message["name"] as? String ?: error("host-name");RoomDirectory.name(name)
                require(request.game!="mpds"||name.length<=8)
                require(message["startBarrierProtocol"]==1.0&&message["profileProtocol"]==1.0)
                for(key in HELLO_FIELDS-setOf("type","name","emblem"))
                    require(!message.containsKey(key)||message[key]==1.0)
                val emblem=message["emblem"]
                require(emblem==null||emblem is String&&emblem.length==684&&
                    Base64.getDecoder().decode(emblem).size==512&&Base64.getEncoder().encodeToString(Base64.getDecoder().decode(emblem))==emblem)
                hello[seat]?.let {old->require(old.filterKeys {it!="name"}==message.filterKeys {it!="name"})}
                hello[seat]=message.toMap();names[seat-1]=name;emblems[seat-1]=emblem as? String;connected.add(seat)
                publishRoster();status("Salon ${request.roomCode} — ${connected.size}/${request.players} joueurs",connected.size==request.players)
            }
            "diagnostic"->Unit
            "failed","ended"->fail("La partie a été interrompue par P$seat.")
            "stopped","stop-ready"->Unit
            else->error("host-lobby-message")
        }
    }
    private fun publishRoster() {
        val value=mapOf("type" to "roster","generation" to generation,"phase" to phase,
            "roster" to names.mapIndexed {index,name->mapOf("seat" to index+1,"name" to name,"connected" to (index+1 in connected))},
            "askPlayerSaves" to false)
        channels.filterKeys {it in connected}.values.forEach {it.send(value).get(10,TimeUnit.SECONDS)}
    }
    fun start() {worker.execute {try{launch()}catch(_:Throwable){fail("Démarrage de la partie impossible.")}}}
    private fun launch() {
        ensure();require(!started&&connected.size==request.players&&plan!=null)
        require(request.game!="mpds"||request.playerName.length<=8)
        started=true;phase="starting";generation=plan!!.generation;status("Distribution du jeu…");publishRoster()
        val activeRound=round!!
        val barrier=CompletableFuture<Unit>()
        val nativeRequest=RoomGuestLobby.GameRequest(generation,seats[0],names.toList(),emblems.toList(),null,
            barrier,AtomicBoolean(false),false)
        gameRequest=nativeRequest
        for(index in 1 until request.players) {
            val message=mapOf("type" to "prepare","generation" to generation,
                "files" to files(plan!!.gameInvitations[index]),"playerNames" to names.toList(),
                "playerEmblems" to emblems.toList(),"saveBinding" to null,"startBarrier" to 1)
            channels.getValue(index+1).send(message).get(10,TimeUnit.SECONDS)
        }
        val deliveries=(1 until request.players).map {index->CompletableFuture.runAsync({
            assets.send(activeRound,seats[index],staged[index]) {received,total->
                status("Envoi à P${index+1} : ${if(total>0)received*100/total else 0}%")
            }
        },transfers)}
        deliveriesOwned.addAll(deliveries)
        CompletableFuture.allOf(*deliveries.toTypedArray()).whenComplete {_,error->
            if(error!=null){barrier.completeExceptionally(error);fail("Le transfert du jeu a échoué.")}
            else if(!closed.get())worker.execute {
                try {ensure();channels.values.forEach {it.send(mapOf("type" to "game-go","generation" to generation)).get(10,TimeUnit.SECONDS)}
                    barrier.complete(Unit);status("Démarrage collectif…")
                    startNative(activeRound,nativeRequest)}
                catch(e:Exception){barrier.completeExceptionally(e);fail("Départ collectif interrompu.")}
            }
        }
    }
    private fun startNative(activeRound:RoomGuestSession.Round,nativeRequest:RoomGuestLobby.GameRequest) {
        // Match the production browser host: P1's boot/start-barrier budget
        // begins after all guests committed their assets and received game-go.
        gameWorker.execute {
            try {
                ensure()
                val opened=RoomNativeGame.start(context,activeRound,nativeRequest,root,onReady=onReady,
                    hostAssets={ensure();staged[0]},hostNetwork={ticket,onFailure->
                        RoomHostGameNetwork(activeRound,seats.toList(),ticket,onFailure).start()
                    })
                game=opened
                if(closed.get()){opened.close();game=null}
                else worker.execute {if(!closed.get()){phase="running";publishRoster();status("Partie en cours")}}
                opened.done.whenComplete {_,error->if(!closed.get())fail(if(error==null)"Partie terminée." else "Partie interrompue.")}
            } catch(_:Throwable){if(!closed.get())fail("Le moteur n’a pas démarré.")}
        }
    }
    private fun fail(text:String){if(!closed.get()){status(text);closeAsync()}}
    fun closeAsync():CompletableFuture<Unit> {
        if(!closed.compareAndSet(false,true))return termination
        round?.cancel();gameRequest?.cancel();channels.values.toList().forEach {it.close()}
        transfers.shutdownNow()
        // CompletableFuture.runAsync tasks discarded by shutdownNow otherwise
        // never complete. Cancellation wakes the start barrier, while awaiting
        // the executor below still waits for physically running transfers.
        deliveriesOwned.forEach {it.cancel(true)}
        gameWorker.execute {
            var failure:Throwable?=null
            try{game?.close();game=null}catch(e:Throwable){failure=e}
            if(RoomEngine.hasReservedLease()&&failure==null)failure=IllegalStateException("host-native-lease-held")
            worker.execute {
                try{check(transfers.awaitTermination(20,TimeUnit.SECONDS)){"host-transfers-held"}}
                catch(e:Throwable){if(failure==null)failure=e}
                // Preserve private launch files if native teardown is uncertain.
                if(failure==null)try{assets.close()}catch(e:Throwable){failure=e}
                try{roundOwners.forEach {it.close()};outerOwners.forEach {it.close()}}
                catch(e:Throwable){if(failure==null)failure=e}
                // Native retirement and private file cleanup are local facts.
                // A failed remote revoke expires remotely; it must not retain a
                // successfully released console, just as directory.leave does.
                runCatching {access.close()}
                worker.shutdownNow()
                if(failure==null)termination.complete(Unit) else termination.completeExceptionally(IllegalStateException("host-retirement-failed"))
            }
        }
        gameWorker.shutdown();return termination
    }
    override fun close(){closeAsync()}
    companion object {
        private val HELLO_FIELDS=setOf("type","name","emblem","profileProtocol","saveProtocol",
            "checkpointProtocol","authorityCheckpointProtocol","experimentalTrialProtocol",
            "startBarrierProtocol","stopIntentProtocol","assistProtocol")
        private fun files(invitation:String):Map<String,Any?> {
            require(invitation.startsWith(RoomInvitation.PREFIX)&&invitation.length<=30000)
            val text=Base64.getUrlDecoder().decode(invitation.substring(RoomInvitation.PREFIX.length)).toString(Charsets.UTF_8)
            require(text.startsWith("NIFI2-INVITE-1\n"))
            val envelope=RoomDirectory.parseStrictJson(text.substring(15)) as? Map<*,*> ?: error("host-invitation")
            require(envelope.keys==setOf("version","files")&&envelope["version"]==1.0)
            val values=envelope["files"] as? Map<*,*> ?: error("host-files")
            require(values.keys==setOf("room.json","client.json","capability.bin"))
            @Suppress("UNCHECKED_CAST")return values as Map<String,Any?>
        }
    }
}
