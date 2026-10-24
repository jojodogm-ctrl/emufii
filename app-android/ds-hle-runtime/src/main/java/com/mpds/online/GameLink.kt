package com.mpds.online

import android.graphics.Bitmap

/**
 * Process-wide bridge between the two activities that make up the dual-screen game on AYN Thor-class clamshells:
 *  - [MainActivity] on the TOP panel (display 0): renders the DS top screen, owns the emulation/render loop.
 *  - [BottomScreenActivity] on the BOTTOM panel (display N): renders the DS touch screen + takes stylus touch.
 *
 * Both activities live in the same process, so a plain singleton is the simplest safe channel. Input is merged from
 * every source (overlay buttons, and the physical gamepad handled in BOTH activities so key focus landing on either
 * display still drives the game). The render thread (in MainActivity's DsView) pushes each bottom framebuffer to
 * [bottomSink], which BottomScreenActivity sets to blit into its surface.
 */
object GameLink {
    internal val notices=RoomGameNotices()
    @Volatile var bottomLobby:String? = "WANTAP PARTY\nREJOIGNEZ UN SALON SUR L ECRAN PRINCIPAL"
    // ---- input (all volatile: written on UI/input threads, read on the render thread) ----
    @Volatile var overlay = 0      // on-screen overlay buttons (single-screen phones)
    @Volatile var padMain = 0      // gamepad while MainActivity has key focus
    @Volatile var padBottom = 0    // gamepad while BottomScreenActivity has key focus
    @Volatile var touchX = 0
    @Volatile var touchY = 0
    @Volatile var touchDown = false

    fun buttonMask(): Int = overlay or padMain or padBottom

    fun setTouch(x: Int, y: Int, down: Boolean) { touchX = x; touchY = y; touchDown = down }

    // ---- bottom framebuffer out (set by BottomScreenActivity, invoked by the DsView render thread) ----
    @Volatile var bottomSink: ((Bitmap) -> Unit)? = null

    // ---- lifecycle signal: MainActivity sets false to ask the bottom activity to finish ----
    @Volatile var bottomShouldRun = false
}
