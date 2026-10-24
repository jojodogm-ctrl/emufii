package eu.emufii.dsnemul

import android.net.Uri
import android.os.IBinder
import android.os.Parcel
import android.os.SharedMemory

internal object NemulRpc {
    const val DESCRIPTOR = "eu.emufii.dsnemul.Round"
    const val START = IBinder.FIRST_CALL_TRANSACTION
    const val STATUS = START + 1
    const val MEMORY = START + 2
    const val INPUT = START + 3
    const val AUDIO = START + 4
    const val STOP = START + 5
    const val PIXELS = 256 * 192
    const val MEMORY_BYTES = 8 + PIXELS * 4 * 2
    data class Status(val state: Int, val frames: Long, val error: String)

    private fun <T> call(remote: IBinder, code: Int, write: (Parcel) -> Unit = {},
                         read: (Parcel) -> T): T {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(DESCRIPTOR)
            write(data)
            check(remote.transact(code, data, reply, 0)) { "Round service unavailable" }
            reply.readException()
            return read(reply)
        } finally { data.recycle(); reply.recycle() }
    }
    fun start(remote: IBinder, r: NemulRoundRequest) = call(remote, START, { p ->
        p.writeString(r.romUri.toString()); p.writeString(r.expectedRomSha256)
        p.writeString(r.roomCode); p.writeInt(r.nativeSeat); p.writeInt(r.players)
        p.writeInt(r.inputDelay); p.writeString(r.relayAddress); p.writeInt(r.relayPort)
        p.writeByteArray(r.sessionId); p.writeByteArray(r.capability); p.writeString(r.playerName)
    }) { it.readInt() == 1 }
    fun readRequest(p: Parcel) = NemulRoundRequest(
        Uri.parse(p.readString().orEmpty()), p.readString().orEmpty(), p.readString().orEmpty(),
        p.readInt(), p.readInt(), p.readInt(), p.readString().orEmpty(), p.readInt(),
        p.createByteArray() ?: byteArrayOf(), p.createByteArray() ?: byteArrayOf(), p.readString().orEmpty(),
    )
    fun status(remote: IBinder): Status = call(remote, STATUS) {
        Status(it.readInt(), it.readLong(), it.readString().orEmpty())
    }
    fun memory(remote: IBinder): SharedMemory? = call(remote, MEMORY) {
        it.readTypedObject(SharedMemory.CREATOR)
    }
    fun input(remote: IBinder, keys: Int, x: Int, y: Int, down: Boolean, mic: Int) =
        call(remote, INPUT, { it.writeInt(keys); it.writeInt(x); it.writeInt(y)
            it.writeInt(if (down) 1 else 0); it.writeInt(mic) }) { Unit }
    fun audio(remote: IBinder): ShortArray = call(remote, AUDIO) {
        val bytes = it.createByteArray() ?: byteArrayOf()
        require(bytes.size <= 4096 && bytes.size % 2 == 0)
        ShortArray(bytes.size / 2) { index ->
            ((bytes[index * 2].toInt() and 255) or (bytes[index * 2 + 1].toInt() shl 8)).toShort()
        }
    }
    fun stop(remote: IBinder) = call(remote, STOP) { Unit }
}
