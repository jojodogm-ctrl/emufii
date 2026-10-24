package com.mpds.online

import android.graphics.Bitmap

/**
 * JNI surface to libmelonjni.so (the melonDS-fork core, single console, native A64 JIT).
 *
 * The native side (melonjni.cpp) owns ONE heap-allocated NDS console. nativeInit boots it; the render
 * thread calls nativeRunFrame + nativeGetFramebuffer; the UI thread feeds input via nativeSetInput.
 */
object NativeBridge {
    /** Unpacks an authenticated boot/seat transfer into a NEW preparation/
     * directory before nativeConfigureRoom. Does not start any emulator. */
    external fun nativeStageRoomPreparation(game: Int, players: Int, seat: Int,
        input: String, directory: String, generatedSha: String, bundleSha: String): Int
    /** Phase one in the fresh round process; does not boot a game. Game IDs:
     * NSMB=0, MKDS=1, MPDS=2, DKR=3, experimental=4, download receiver=5.
     * Boot IDs: cartridge=0, prepared=1, download=2. Seat is ONE-based.
     * Connect the returned carrier before running the typed bootstrap.
     * Unlike nativeInit, this applies the exact per-game policy before any
     * native environment cache or NDS construction can run. */
    external fun nativeConfigureRoom(game: Int, boot: Int, players: Int, seat: Int,
        bufferTicks: Int, language: Int, epoch: String, nickname: String,
        romSha: String, romBytes: Long, personalSave: Boolean, roomNames: Boolean,
        generatedSha: String, preparationDirectory: String): Long
    /** Phase two on the round worker, after RoomGameNetwork.ready. Private
     * directory contains verified game.nds, optional player.sav, roster files
     * and preparation/. Native revalidates identities; one attempt per process. */
    external fun nativeBootRoom(ticket: Long, directory: String): Int
    /** Writes current cartridge backup memory to a fresh private file; 0 if none. */
    external fun nativeExportRoomSave(path: String): Int
    // New single-owner SDK transport. The authenticated room worker owns these
    // methods; no public room credentials or game bytes are interpreted by JNI.
    // A fresh process is required to switch between SDK and prepared MPDS modes.
    /** Zero-based seat; returns a session ticket, or 0 if startup is rejected. */
    external fun nativeConfigureSdkRoom(owner: Int, players: Int, epoch: String): Long
    /** lane 0 = broker control, 1 = peer state. Result: 1 accepted, 0 full, -1 rejected. */
    external fun nativeDeliverRoomPacket(ticket: Long, lane: Int, source: Int, bytes: ByteArray): Int
    /** Two header bytes (lane, peer), then native wire bytes; null when empty. */
    external fun nativeTakeRoomPacket(ticket: Long): ByteArray?
    /** Wakes blocked RPCs; callable independently of the frame thread. */
    external fun nativeCloseRoomCarrier(ticket: Long)
    /** Owner-thread commands; 0 park, 1 prepare, 2 frozen, 3 capture, 4 release,
     * 5 released, 6 collective resume, 7 native carrier drained, 8 retire source,
     * 9 attach authenticated authority, 10 attach fresh SDK. Native status
     * 0 is success; never interpret merely parking as a complete snapshot. */
    external fun nativeCheckpointStatus(ticket: Long, action: Int, operation: String,
        incarnation: String, releasedMask: Int, inFlightAccounted: Boolean): Int
    /** Four-byte little-endian total length, then <=64 KiB immutable component
     * data. Components: core, SDK, MPDS, authority, metadata, transport, frontend. */
    external fun nativeCheckpointPart(ticket: Long, operation: String, incarnation: String,
        component: Int, offset: Int): ByteArray?
    /** Transfers held native state ownership: source byte then N2S1 payload. */
    external fun nativeTakeHeldRoomState(ticket: Long): ByteArray?
    external fun nativeCheckpointUpload(ticket:Long,operation:String,freshIncarnation:String,
        capturedIncarnation:String,component:Int,total:Int,offset:Int,bytes:ByteArray):Int
    external fun nativeCheckpointMaterialize(ticket:Long,operation:String,freshIncarnation:String,capturedIncarnation:String,endpoint:String,frame:Long):Int
    external fun nativeCheckpointNewCarrier(ticket:Long,operation:String,freshIncarnation:String):Long
    external fun nativeStopSingleOwner()
    external fun nativeSingleOwnerError(): String
    external fun nativeReadSingleOwnerAudio(out: ShortArray, maxPairs: Int): Int
    init {
        System.loadLibrary("melonjni")
        if (!BuildConfig.SINGLE_OWNER) System.loadLibrary("mpdsprobe")
    }

    /**
     * THE KEY BET: boot 4 DS consoles + run `frames` of NiFi lockstep, only inputs crossing the UDP relay
     * (delay-lockstep + window redundancy). BLOCKS for the whole run (~frames/60 s) -> call on a background
     * thread. Returns the measured per-console fps (>=55 means a phone CAN carry 4 native consoles; the WASM
     * ceiling is gone). Run this phone as seat 1 with three desktop melon-play-wan.exe seats 2/3/4 on the same
     * relay. Detailed progress ([barrier]/[hb]/[crc]/[done]) is in logcat under tags MPDS / MPDS-core.
     *
     *  stateDir must contain board_live4_p1..p4.bin; relayHost:relayPort = the shared UDP relay (relay.py:6000).
     */
    external fun nativeRunHeadlessMP(
        romPath: String, stateDir: String, seat: Int,
        relayHost: String, relayPort: Int, delay: Int, frames: Long
    ): Double

    // ---- interactive 4-player (the playable app): boot 4 consoles + MP threads; drive + render MY seat ----
    external fun nativeInitMP(romPath: String, stateDir: String, seat: Int, relayHost: String, relayPort: Int, delay: Int): Int

    // ---- JOIN-BY-CODE client (like the Windows/Mac apps): enter name + room code, ROM is fetched from the host ----
    /** Set the player's display name (<=10 chars) before joining. */
    external fun nativeSetName(name: String)
    /** Set the room code to join (sanitized to uppercase alnum). */
    external fun nativeSetRoom(code: String)
    /** Join the room as a client. romPath = writable cache path for the fetched ROM (need not exist). BLOCKS for the
     *  whole session (lobby + play) -> call on a background thread; poll [nativeMpRunning]/[nativeUiMode]. Returns 1 if
     *  it reached play. */
    external fun nativeJoinRoom(romPath: String, stateDir: String, relayHost: String, relayPort: Int, delay: Int): Int
    /** Lobby status: 0 waiting-for-players, 1 host-ready, 2 REJOINING, 3 waiting-for-host, 4 need-name, 5 starting. */
    external fun nativeUiMode(): Int
    /** ROM download from the host: -1 idle, 0..100 percent. */
    external fun nativeRomFetchPct(): Int
    /** My seat (1..4), or 0 until assigned. */
    external fun nativeMySeat(): Int
    /** 1 if this client is the room host. */
    external fun nativeIsHost(): Int
    external fun nativeSetLocalInput(buttons: Int, touchX: Int, touchY: Int, touchDown: Boolean)
    external fun nativeGetFbMP(seatIdx: Int, top: IntArray, bottom: IntArray)
    /** Render MY seat's two screens directly into the ARGB_8888 bitmaps (no IntArray copy/convert). */
    external fun nativeRenderMP(seatIdx: Int, top: Bitmap, bottom: Bitmap)
    external fun nativeMpRunning(): Int
    external fun nativeStopMP()
    /** Pop up to maxPairs stereo pairs of MY seat's audio into buf (int16 interleaved L/R). Returns pairs written. */
    external fun nativeReadAudio(buf: ShortArray, maxPairs: Int): Int

    /** Boot one console. romPath = the user's .nds dump; biosDir = dir with optional bios9/7.bin +
     *  firmware.bin (use "" for FreeBIOS + generated firmware). Returns 1 on success. */
    external fun nativeInit(romPath: String, biosDir: String): Int

    /** Run exactly one emulated frame on the calling thread. */
    external fun nativeRunFrame()

    /** Copy the top + bottom 256x192 screens (ARGB_8888) into the supplied int arrays
     *  (each must be >= 256*192). */
    external fun nativeGetFramebuffer(top: IntArray, bottom: IntArray)

    /** Latch input. buttons = active-high DS key bitmask (see [Keys]); touch on the bottom screen
     *  in 0..255 / 0..191. */
    external fun nativeSetInput(buttons: Int, touchX: Int, touchY: Int, touchDown: Boolean)

    /** 1 if a console is currently booted. */
    external fun nativeIsBooted(): Int

    /** DS key bit positions (matches melonDS SetKeyMask layout: A,B,Sel,Start,Right,Left,Up,Down,R,L,X,Y). */
    object Keys {
        const val A      = 1 shl 0
        const val B      = 1 shl 1
        const val SELECT = 1 shl 2
        const val START  = 1 shl 3
        const val RIGHT  = 1 shl 4
        const val LEFT   = 1 shl 5
        const val UP     = 1 shl 6
        const val DOWN   = 1 shl 7
        const val R      = 1 shl 8
        const val L      = 1 shl 9
        const val X      = 1 shl 10
        const val Y      = 1 shl 11
    }
}
