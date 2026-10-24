package com.mpds.online

import java.io.Closeable
import java.util.Base64
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Persistent inner lobby. All protocol decisions run on events; downloads and
 * native ownership run on gameWorker. Stop can invalidate a blocked startup.
 * startGame must observe request.checkCurrent() and close everything it owns on
 * exceptions. Its returned handle is closed before another game can start.
 */
internal class RoomGuestLobby(
    private val outer: RoomInvitation.Admission,
    snapshot: RoomDirectory.Snapshot,
    private val port: RoomLobbyPort,
    private val playerName: String,
    private val startGame: (GameRequest) -> GameHandle,
    private val onChange: (Status) -> Unit = {},
    private val onError: (String) -> Unit = {},
    private val onDisconnected: (RoomDirectory.Player) -> Unit = {},
    private val validateSave: (String, ByteArray) -> Unit,
    now: () -> Long = System::currentTimeMillis,
    private val authorityCheckpoints:Boolean=false,
    private val checkpointFactory:((String,RoomCheckpointOwner,()->Unit,(String)->Unit)->RoomCheckpointPort)?=null
) : Closeable {
    data class Status(val phase: String, val generation: Long, val roster: List<RoomDirectory.Player>,
                      val saveRequested: Boolean,val playerDiagnostic:RoomGameNotices.Wait?=null)
    interface GameHandle : Closeable {
        val done: CompletableFuture<Unit>
        val checkpointOwner:RoomCheckpointOwner? get()=null
    }
    class GameRequest internal constructor(
        val generation: Long, val admitted: RoomInvitation.Admission,
        val names: List<String>, val emblems: List<String?>?, val personalSave: ByteArray?,
        val startBarrier: CompletableFuture<Unit>, private val cancelled: AtomicBoolean,
        val authorityCheckpoints:Boolean=false
    ) {
        @Volatile var stopping = false; internal set
        fun checkCurrent() { check(!cancelled.get() && admitted.isCurrent()) { "game-retired" } }
        internal fun cancel() {
            cancelled.set(true)
            startBarrier.completeExceptionally(IllegalStateException("game-retired"))
            admitted.close()
        }
    }
    private class Active(val request: GameRequest) {
        var handle: GameHandle? = null
        var checkpointRequested=false
        @Volatile var checkpoint:RoomCheckpointPort?=null
    }
    private class Save(val binding: RoomLobbySavePolicy.Binding) {
        var selected=false;var bytes:ByteArray?=null;var ready:Set<Int> = emptySet()
        fun clear() { bytes?.fill(0);bytes=null }
    }
    private val closed=AtomicBoolean(false)
    private val opened=AtomicBoolean(false)
    private val cleanupFailed=AtomicBoolean(false)
    private val termination=CompletableFuture<Unit>()
    private val events=ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,ArrayBlockingQueue(64),
        java.util.concurrent.ThreadFactory { Thread(it,"wantap-lobby-events").apply { isDaemon=true } })
    private val gameWorker=Executors.newSingleThreadExecutor { Thread(it,"wantap-game-owner").apply { isDaemon=true } }
    private val admissions=RoomRoundAdmission(outer,snapshot,now)
    private val players=snapshot.settings.players
    private val seat=snapshot.seat
    private var generation=0L
    private var retired=0L
    private var phase="waiting"
    private var roster:List<RoomDirectory.Player> = emptyList()
    private var playerDiagnostic:RoomGameNotices.Wait?=null
    @Volatile private var active:Active?=null
    private var save:Save?=null
    private var retiring=false
    private var checkpointProtocol:Boolean?=null
    private var authorityProtocol:Boolean?=null

    init { name(playerName,false);require(!authorityCheckpoints||checkpointFactory!=null) }
    fun open() {
        check(opened.compareAndSet(false,true))
        port.ready.whenComplete { _,error ->
            if(error!=null) fail("lobby-connection") else post {
                send(mapOf("type" to "hello","name" to playerName,"saveProtocol" to 1,
                    "profileProtocol" to 1,"emblem" to null,"startBarrierProtocol" to 1,"stopIntentProtocol" to 1)+
                    if(authorityCheckpoints)mapOf("checkpointProtocol" to 1,"authorityCheckpointProtocol" to 1)else emptyMap())
            }
        }
    }
    /** Called by the channel reader; never waits for game/download work. */
    fun receive(message:Map<String,Any?>) {
        // Freeze the caller's mutable collections and cap retained input bytes.
        try {
            val text=RoomJson.encode(message);require(text.toByteArray(Charsets.UTF_8).size<=32768)
            @Suppress("UNCHECKED_CAST")
            val frozen=RoomDirectory.parseStrictJson(text) as Map<String,Any?>
            post { process(frozen) }
        } catch (_:Exception) { fail("lobby-protocol") }
    }
    private fun post(action:()->Unit) {
        if(closed.get())return
        try { events.execute {
            if(!closed.get())try { check(outer.isCurrent());action() } catch (e:Exception) {
                // Only source locations, never remote messages, invitations or credentials.
                runCatching { android.util.Log.w("Wantap-room", "Lobby validation: " + e.javaClass.simpleName + " " +
                    e.stackTrace.filter { it.className.startsWith("com.mpds.online.") }.take(4).joinToString(" ")) }
                fail("lobby-protocol")
            }
        } } catch (_:Exception) { fail("lobby-overloaded") }
    }
    private fun send(value:Map<String,Any?>) {
        port.send(value).whenComplete { _,error -> if(error!=null) fail("lobby-send-failed") }
    }
    private fun emit() { observe { onChange(Status(phase,generation,roster.toList(),save?.selected==false,
        playerDiagnostic.takeIf{phase in listOf("starting","running")})) } }
    private fun clearSave() { save?.clear();save=null }
    private fun process(m:Map<String,Any?>) {
        when(m["type"]) {
            "roster" -> {
                fields(m,setOf("type","generation","phase","roster"),
                    setOf("askPlayerSaves","playerDiagnostic","experimentalTrialProtocol","assistProtocol","checkpointProtocol","authorityCheckpointProtocol"))
                val g=number(m["generation"],generation)
                val p=m["phase"] as? String
                require(p in listOf("waiting","saves-pending","starting","running","stopping"))
                require(!m.containsKey("askPlayerSaves") || m["askPlayerSaves"] is Boolean)
                require(!m.containsKey("experimentalTrialProtocol"))
                require(!m.containsKey("checkpointProtocol")||authorityCheckpoints&&m["checkpointProtocol"]==1.0)
                val cp=m["checkpointProtocol"]==1.0
                require(!m.containsKey("authorityCheckpointProtocol")||cp&&authorityCheckpoints&&m["authorityCheckpointProtocol"]==1.0)
                val ap=m["authorityCheckpointProtocol"]==1.0
                require(checkpointProtocol==null||checkpointProtocol==cp)
                require(authorityProtocol==null||authorityProtocol==ap)
                checkpointProtocol=cp;authorityProtocol=ap
                require(!m.containsKey("assistProtocol") || m["assistProtocol"]==1.0)
                val entries=m["roster"] as? List<*> ?: error("roster")
                require(entries.size==players)
                val next=entries.mapIndexed { i,v ->
                    val r=obj(v);fields(r,setOf("seat","name","connected"))
                    require(number(r["seat"],1)==i+1L && r["connected"] is Boolean)
                    val n=r["name"] as? String ?: error("name");name(n,r["connected"]==false)
                    RoomDirectory.Player(i+1,n,r["connected"] as Boolean)
                }
                require(active==null || active!!.request.generation==g)
                require(p!="waiting" || active==null && !retiring)
                for(old in roster)if(old.seat!=seat && old.connected && !next[old.seat-1].connected)
                    observe { onDisconnected(old) }
                if(g!=generation || p in listOf("waiting","stopping"))clearSave()
                if(g!=generation||p !in listOf("starting","running"))playerDiagnostic=null
                if(p in listOf("starting","running")&&port.diagnostics)try {
                    playerDiagnostic=RoomGameNotices.parseWait(m["playerDiagnostic"],players)
                }catch(_:Exception){ /* Invalid optional diagnostics cannot retire gameplay. */ }
                generation=g;phase=p!!;roster=next;emit()
            }
            "save-request" -> {
                fields(m,setOf("type","generation","game","romSha256"))
                val binding=RoomLobbySavePolicy.binding(m.filterKeys { it!="type" },outer.room)
                require(phase=="saves-pending" && binding.generation==generation && generation>retired && save==null && active==null)
                save=Save(binding);emit()
            }
            "save-readiness" -> {
                fields(m,setOf("type","generation","game","romSha256","readiness"))
                val s=save ?: error("save-binding")
                require(phase=="saves-pending" && RoomLobbySavePolicy.binding(m.filterKeys { it!="type" && it!="readiness" },outer.room)==s.binding)
                val rows=m["readiness"] as? List<*> ?: error("save-binding");require(rows.size==players)
                val ready=mutableSetOf<Int>()
                rows.forEachIndexed { i,v ->
                    val r=obj(v);fields(r,setOf("seat","ready"));require(number(r["seat"],1)==i+1L && r["ready"] is Boolean)
                    val yes=r["ready"]==true
                    require((i+1 !in s.ready || yes) && (i+1!=seat || !yes || s.selected))
                    if(yes)ready.add(i+1)
                };s.ready=ready;emit()
            }
            "prepare" -> prepare(m)
            "checkpoint-bind" -> bindCheckpoint(m)
            "game-go" -> {
                fields(m,setOf("type","generation"));val g=number(m["generation"],1)
                if(g<=retired)return
                val r=active?.request ?: error("start-barrier")
                require(g==r.generation && phase=="starting" && !r.startBarrier.isDone)
                r.startBarrier.complete(Unit)
            }
            "stop-intent" -> {
                fields(m,setOf("type","generation"));val g=number(m["generation"],1)
                if(g<generation || g<=retired)return
                val r=active?.request ?: error("stop-intent")
                require(r.generation==g && phase in listOf("starting","running"))
                r.stopping=true;send(mapOf("type" to "stop-ready","generation" to g))
            }
            "stop" -> {
                fields(m,setOf("type","generation"));val g=number(m["generation"],maxOf(1,generation))
                require(!retiring)
                generation=g;retired=g;phase="stopping";clearSave();retiring=true
                val previous=active;active=null;previous?.request?.cancel();previous?.checkpoint?.close();admissions.retire(g);emit()
                gameWorker.execute {
                    try {
                        previous?.handle?.close();previous?.request?.personalSave?.fill(0)
                        post { retiring=false;phase="waiting";emit();send(mapOf("type" to "stopped","generation" to g)) }
                    } catch (_:Exception) { cleanupFailed.set(true);fail("game-retirement-failed") }
                }
            }
            else -> error("lobby-message")
        }
    }
    private fun prepare(m:Map<String,Any?>) {
        fields(m,setOf("type","generation","files","playerNames"),setOf("saveBinding","playerEmblems","startBarrier","authorityCheckpoints"))
        require(!m.containsKey("authorityCheckpoints")||m["authorityCheckpoints"]==true&&authorityProtocol==true)
        val g=number(m["generation"],maxOf(1,generation))
        require(g>retired && active==null && !retiring)
        val s=save
        if(s!=null || m["saveBinding"]!=null) {
            require(s!=null && RoomLobbySavePolicy.binding(obj(m["saveBinding"]),outer.room)==s.binding &&
                s.selected && s.ready.size==players && s.binding.generation==g)
        } else require(phase!="saves-pending")
        val names=(m["playerNames"] as? List<*>)?.map { (it as? String ?: error("name")).also { n->name(n,false) } }
            ?: error("profiles")
        require(names.size==players && names[seat-1]==playerName && roster.size==players &&
            roster.indices.all { roster[it].connected && roster[it].name==names[it] })
        val emblems=if(m.containsKey("playerEmblems")) {
            val list=m["playerEmblems"] as? List<*> ?: error("emblems");require(list.size==players)
            list.map { value -> if(value==null)null else {
                val text=value as? String ?: error("emblem");require(text.length==684)
                val bytes=Base64.getDecoder().decode(text)
                require(bytes.size==512 && Base64.getEncoder().encodeToString(bytes)==text);text
            } }.also { require(it[seat-1]==null) }
        } else null
        require(!m.containsKey("startBarrier") || m["startBarrier"]==1.0)
        val admitted=admissions.admit(g,obj(m["files"]))
        try {
            val game=admitted.room["game"] ?: "mpds"
            require(m["authorityCheckpoints"]!=true||game in listOf("mkds","nsmb"))
            require(game!="mpds" || names.all { it.length<=8 })
            require(admitted.room["mkdsMenu"]!=true || emblems!=null && s?.bytes==null)
            if(s!=null)require(s.binding.game==game && s.binding.romSha256==(admitted.room["transferRomSha256"] ?: admitted.room["romSha256"]))
            val barrier=CompletableFuture<Unit>();if(!m.containsKey("startBarrier"))barrier.complete(Unit)
            val request=GameRequest(g,admitted,names,emblems,s?.bytes?.copyOf(),barrier,AtomicBoolean(false),m["authorityCheckpoints"]==true)
            val a=Active(request);active=a;generation=g;phase="starting";clearSave();emit()
            gameWorker.execute {
                try {
                    check(!closed.get());request.checkCurrent();val handle=startGame(request);a.handle=handle
                    if(closed.get() || active!==a) {
                        try { handle.close() } catch(e:Exception) { cleanupFailed.set(true);fail("game-retirement-failed");throw e }
                        finally { a.handle=null }
                    }
                    else post {
                        if(active===a) {
                            phase="running";emit()
                            handle.done.whenComplete { _,error -> post {
                                if(active===a && !request.stopping)send(mapOf("type" to if(error==null)"ended" else "failed","generation" to g))
                            } }
                        }
                    }
                } catch (error:Exception) { post {
                    if(active===a && !request.stopping) {
                        observe { onError(RoomStartFailure.code(error)) };send(mapOf("type" to "failed","generation" to g))
                    }
                } }
            }
        } catch(e:Exception) { admitted.close();throw e }
    }
    private fun bindCheckpoint(m:Map<String,Any?>){
        val g=number(m["generation"],1);if(g<generation||g<=retired)return
        fields(m,setOf("type","generation","roundEpoch","bindingSha256","authority"))
        val a=checkNotNull(active);val request=a.request
        require(authorityCheckpoints&&authorityProtocol==true&&checkpointProtocol==true&&request.authorityCheckpoints&&
            m["authority"]==true&&g==generation&&g==request.generation&&phase in listOf("starting","running")&&
            m["roundEpoch"]==request.admitted.room["epoch"]&&!a.checkpointRequested)
        val binding=m["bindingSha256"] as? String ?: error("checkpoint-binding")
        require(Regex("[a-f0-9]{64}").matches(binding));a.checkpointRequested=true
        // Queued after startGame, outside events: Stop must remain responsive.
        gameWorker.execute {
            try{
                fun live(){check(!closed.get()&&active===a);request.checkCurrent()}
                live();val owner=checkNotNull(a.handle?.checkpointOwner)
                val lane=checkpointFactory!!(binding,owner,::live,{reason->
                    post { if(active===a&&!request.stopping)fail(reason) }
                })
                a.checkpoint=lane
                if(closed.get()||active!==a){lane.close();return@execute}
                lane.ready.get(35,TimeUnit.SECONDS);live()
            }catch(_:Exception){post { if(active===a&&!request.stopping)fail("game-checkpoint-failed") }}
        }
    }
    fun submitSave(bytes:ByteArray?):CompletableFuture<Unit> {
        val result=CompletableFuture<Unit>()
        if(closed.get()) { result.completeExceptionally(IllegalStateException("lobby-closed"));return result }
        val copy=bytes?.copyOf()
        try { events.execute {
            try {
                check(!closed.get() && outer.isCurrent());val s=save ?: error("save-not-requested")
                check(!s.selected && phase=="saves-pending")
                if(copy!=null) { RoomLobbySavePolicy.validateSize(copy.size,s.binding.game);validateSave(s.binding.game,copy) }
                s.bytes=copy?.copyOf();s.selected=true
                send(mapOf("type" to "save-ready","generation" to s.binding.generation,"game" to s.binding.game,"romSha256" to s.binding.romSha256))
                emit();result.complete(Unit)
            } catch(e:Exception) { result.completeExceptionally(e) } finally { copy?.fill(0) }
        } } catch(e:Exception) { copy?.fill(0);result.completeExceptionally(e) }
        return result
    }
    private fun fail(code:String) { if(!closed.get()) { observe { onError(code) };close() } }
    internal fun transportClosed(reason:String = "lobby-connection") { fail(reason) }
    fun closeAsync():CompletableFuture<Unit> {
        if(!closed.compareAndSet(false,true))return termination
        // Immediate cancellation wakes a game waiting for game-go or network I/O.
        active?.request?.cancel();active?.checkpoint?.close();port.close()
        // Never enqueue teardown into the bounded event queue: overload itself
        // must still be closable. Drain existing decisions before taking ownership.
        events.shutdown()
        Thread({
            try {
                check(events.awaitTermination(30,TimeUnit.SECONDS)) { "lobby-retirement-timeout" }
                val previous=active;active=null;previous?.request?.cancel();admissions.close();clearSave()
                gameWorker.execute {
                    try {
                        previous?.handle?.close();check(!cleanupFailed.get())
                        termination.complete(Unit)
                    } catch (_:Exception) { termination.completeExceptionally(IllegalStateException("game-retirement-failed")) }
                    finally { previous?.request?.personalSave?.fill(0);gameWorker.shutdown() }
                }
            } catch (_:Exception) {
                cleanupFailed.set(true);active?.request?.cancel();admissions.close()
                termination.completeExceptionally(IllegalStateException("lobby-retirement-failed"))
            }
        },"wantap-lobby-retirement").apply { isDaemon=true;start() }
        return termination
    }
    override fun close() { closeAsync() }
    companion object {
        private fun observe(action:()->Unit) { try { action() } catch (_:Exception) { } }
        private fun number(v:Any?,min:Long):Long {
            val d=v as? Double ?: error("integer");require(d.isFinite() && d>=min && d<=1000000 && d==d.toLong().toDouble());return d.toLong()
        }
        @Suppress("UNCHECKED_CAST") private fun obj(v:Any?):Map<String,Any?> {
            require(v is Map<*,*> && v.keys.all { it is String });return v as Map<String,Any?>
        }
        private fun fields(m:Map<String,Any?>,required:Set<String>,optional:Set<String> = emptySet()) {
            require(m.keys.containsAll(required) && (m.keys-required-optional).isEmpty())
        }
        private fun name(n:String,empty:Boolean) {
            require(empty || n.isNotEmpty());RoomDirectory.name(n)
        }
    }
}
