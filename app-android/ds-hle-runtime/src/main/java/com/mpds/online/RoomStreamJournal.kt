package com.mpds.online

/** Owned by one carrier executor. Receipts indicate local delivery, not game ACKs.
 * Port of tools/nifi2-web/stream.mjs; all retained payloads are bounded and copied.
 */
internal class RoomStreamJournal {
    data class Pending(val sequence: ULong, val payload: ByteArray,
                       var lastSent: Double = Double.NEGATIVE_INFINITY,
                       var fastRetryAt: Double? = null, var fastRetried: Boolean = false)
    var next = 0uL; private set
    var acknowledged = 0uL; private set
    var received = 0uL; private set
    private val outgoing = linkedMapOf<ULong, Pending>()
    private val incoming = linkedMapOf<ULong, ByteArray>()
    private val history = linkedMapOf<ULong, ByteArray>()
    val outgoingCount: Int get() = outgoing.size
    fun pending(): List<Pending> = outgoing.values.toList()
    fun append(payload: ByteArray): Pending {
        require(outgoing.size < 256 && payload.size in 1..1024 && next < ULong.MAX_VALUE)
        return Pending(++next, payload.copyOf()).also { outgoing[it.sequence] = it }
    }
    /** Atomic stream admission: a false return means no bytes were retained. */
    fun appendStream(bytes: ByteArray): Boolean {
        require(bytes.size in 1..65536)
        val records = (bytes.size + 1023) / 1024
        if (outgoing.size + records > 256 || ULong.MAX_VALUE - next < records.toULong()) return false
        for (at in bytes.indices step 1024) append(bytes.copyOfRange(at, minOf(at + 1024, bytes.size)))
        return true
    }
    fun acknowledge(through: ULong) {
        require(through <= next) { "ack-outside-stream" }
        if (through <= acknowledged) return
        acknowledged = through
        val it = outgoing.iterator()
        while (it.hasNext()) {
            val record = it.next()
            if (record.key > through) break
            record.value.payload.fill(0); it.remove()
        }
    }
    fun stage(sequence: ULong, payload: ByteArray): Boolean {
        require(sequence > 0uL && payload.size in 1..1024)
        val prior = if (sequence <= received) history[sequence] else incoming[sequence]
        require(prior == null || prior.contentEquals(payload)) { "conflicting-record" }
        if (sequence <= received || prior != null || sequence - received > 256u) return false
        require(incoming.size < 256)
        incoming[sequence] = payload.copyOf(); return true
    }
    fun nextDelivery(): Pending? {
        if (received == ULong.MAX_VALUE) return null
        return incoming[received + 1u]?.let { Pending(received + 1u, it.copyOf()) }
    }
    fun commit(sequence: ULong) {
        require(received != ULong.MAX_VALUE && sequence == received + 1u && incoming.containsKey(sequence))
        history[sequence] = incoming.remove(sequence)!!; received = sequence
        if (history.size > 256) history.remove(history.keys.first())?.fill(0)
    }
    fun clear() {
        outgoing.values.forEach { it.payload.fill(0) }; outgoing.clear()
        incoming.values.forEach { it.fill(0) }; incoming.clear()
        history.values.forEach { it.fill(0) }; history.clear()
    }
    class GapRecovery {
        private var counter = 0uL
        private var through = 0uL
        private var head = 0uL
        private var duplicates = 0
        fun observe(counter: ULong, through: ULong, journal: RoomStreamJournal) {
            if (counter <= this.counter) return
            this.counter = counter
            if (through < journal.acknowledged) return
            val sent = journal.pending().filter { it.lastSent.isFinite() }
            if (sent.size < 4) { this.through = through; head = 0u; duplicates = 0; return }
            val first = sent.first()
            if (through != this.through || first.sequence != head) {
                this.through = through; head = first.sequence; duplicates = 0
            }
            duplicates = minOf(3, duplicates + 1)
            if (duplicates == 3 && !first.fastRetried) first.fastRetryAt = first.lastSent + 20
        }
    }
}
