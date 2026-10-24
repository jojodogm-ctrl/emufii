package com.mpds.online

/** Bounded presentation state, independent of FPS and the blocked frame thread.
 * Only authenticated lobby data / confirmed local owner transitions enter here.
 * Observed missing data is not proof of a slow device or a bad connection. */
internal class RoomGameNotices(private val now:()->Long={System.nanoTime()/1_000_000}) {
    enum class Checkpoint { RUNNING, PAUSING, PAUSED, CAPTURING, CAPTURED, RESTORING, RESUMING, FAILED }
    data class Wait(val seats:List<Int>,val observers:List<Int>)
    private var checkpoint=Checkpoint.RUNNING
    private var wait:String?=null
    private var waitUntil=0L
    private var waitActive=false
    private var disconnected:String?=null
    private var transient:String?=null
    private var transientUntil=0L
    @Synchronized fun reset(){checkpoint=Checkpoint.RUNNING;wait=null;waitUntil=0;waitActive=false;disconnected=null;transient=null;transientUntil=0}
    @Synchronized fun checkpoint(next:Checkpoint){
        if(next==checkpoint)return
        val old=checkpoint;checkpoint=next
        if(next==Checkpoint.RUNNING&&old!=Checkpoint.RUNNING){transient="La partie reprend pour tous.";transientUntil=now()+4000}
    }
    @Synchronized fun disconnected(player:RoomDirectory.Player){
        disconnected="Connexion perdue avec ${label(player)}. En attente de l’hôte."
    }
    @Synchronized fun diagnostic(value:Wait?,roster:List<RoomDirectory.Player>){
        waitActive=value!=null
        if(value==null){if(now()>=waitUntil)wait=null;return}
        fun names(seats:List<Int>)=seats.joinToString(", "){seat->roster.find{it.seat==seat}?.let(::label)?:"P$seat"}
        val next=if(value.seats.isNotEmpty())"En attente des données de ${names(value.seats)}."
            else "${names(value.observers)} en attente des données de la partie."
        if(next!=wait){wait=next;waitUntil=now()+3000}
    }
    @Synchronized fun text():String? = disconnected ?: when(checkpoint){
        Checkpoint.PAUSING->"Mise en pause pour tous…"
        Checkpoint.PAUSED->"Pause pour tous — l’hôte peut reprendre."
        Checkpoint.CAPTURING->"Sauvegarde collective en cours…"
        Checkpoint.CAPTURED->"État de votre console capturé — pause pour tous."
        Checkpoint.RESTORING->"Restauration collective en cours…"
        Checkpoint.RESUMING->"Synchronisation avant la reprise…"
        Checkpoint.FAILED->"La synchronisation a échoué. En attente de l’hôte."
        Checkpoint.RUNNING->wait?.takeIf{waitActive||now()<waitUntil} ?: transient?.takeIf{now()<transientUntil}
    }
    private fun label(p:RoomDirectory.Player)="P${p.seat}"+if(p.name.isBlank())"" else " (${p.name})"
    companion object {
        /** Same exact bounded projection as validateSharedDiagnostic in the web client. */
        fun parseWait(value:Any?,players:Int):Wait? {
            if(value==null)return null
            require(players in 2..8)
            val m=value as? Map<*,*> ?: error("room-diagnostic")
            require(m.keys==setOf("kind","cause","seats","waitingSeats")&&m["kind"]=="waiting"&&m["cause"]=="unknown")
            fun seats(v:Any?):List<Int>{
                val list=v as? List<*> ?: error("room-diagnostic");require(list.size<=players)
                return list.map { val n=it as? Double ?: error("room-diagnostic")
                    require(n.isFinite()&&n==n.toInt().toDouble()&&n.toInt() in 1..players);n.toInt()
                }.also{require(it.distinct().size==it.size)}
            }
            return Wait(seats(m["seats"]),seats(m["waitingSeats"]).also{require(it.isNotEmpty())})
        }
    }
}
