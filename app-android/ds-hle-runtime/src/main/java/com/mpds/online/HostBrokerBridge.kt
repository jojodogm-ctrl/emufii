package com.mpds.online

/** JNI for the standalone copied transport broker, never an emulator. */
internal object HostBrokerBridge {
    init { System.loadLibrary("emufii_hle_broker") }
    external fun create(players: Int, rttMs: Int, snapshots: Boolean): Long
    external fun createSdkBlocks(players: Int, rttMs: Int, lead: Int): Long
    external fun attach(ticket: Long): Long
    external fun detach(ticket: Long, peer: Long): Boolean
    external fun receive(ticket: Long, peer: Long, bytes: ByteArray, nowMs: Long): Boolean
    external fun tick(ticket: Long, nowMs: Long): Boolean
    external fun peek(ticket: Long, peer: Long): ByteArray?
    external fun pop(ticket: Long, peer: Long): Boolean
    external fun failed(ticket: Long): Boolean
    external fun snapshotStatus(ticket: Long, operation: Long, action: Int): Int
    external fun exportSnapshot(ticket: Long, operation: Long): ByteArray?
    external fun validateSnapshot(bytes: ByteArray): Int
    external fun restore(bytes: ByteArray, freshIncarnation: Long, operation: Long): Long
    external fun attachRestored(ticket: Long, endpoint: Long): Long
    external fun close(ticket: Long)
}
