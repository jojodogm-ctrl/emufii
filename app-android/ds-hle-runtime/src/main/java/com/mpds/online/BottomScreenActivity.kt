package com.mpds.online

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.WindowManager
import android.widget.FrameLayout

/**
 * The DS BOTTOM screen, launched by [MainActivity] directly onto the Thor's lower panel via
 * ActivityOptions.setLaunchDisplayId(displayId). A real Activity (not a Presentation) so it works even when the
 * iiSU launcher / AYN Dual Screen Assistant owns that display and keeps it out of the presentation-display category.
 *
 * Renders the DS bottom framebuffer 1:1 into a 256x192 fixed surface (GPU-composited upscale, letterboxed to keep
 * 4:3) and maps its touch straight to DS coordinates. The physical gamepad is ALSO handled here (feeding
 * GameLink.padBottom) so if key focus lands on this display the buttons still drive the game.
 */
class BottomScreenActivity : Activity() {

    private lateinit var surface: AspectSurface
    @Volatile private var ready = false
    private var touchLogs = 0   // log only the first taps (e2e touch verification, not a hot-path spam)
    private val ui=android.os.Handler(android.os.Looper.getMainLooper())
    private lateinit var waiting:android.widget.TextView
    private lateinit var noticeBanner:RoomNoticeBanner
    private val updateWaiting=object:Runnable {
        override fun run() {
            if(!GameLink.bottomShouldRun) { finish();return }
            val message=GameLink.bottomLobby
            waiting.visibility=if(message==null)android.view.View.GONE else android.view.View.VISIBLE
            if(message!=null && waiting.text.toString()!=message)waiting.text=message
            noticeBanner.refresh(if(message==null)GameLink.notices.text()else null)
            ui.postDelayed(this,200)
        }
    }

    class AspectSurface(ctx: android.content.Context) : SurfaceView(ctx) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val aw = MeasureSpec.getSize(widthMeasureSpec)
            val ah = MeasureSpec.getSize(heightMeasureSpec)
            var h = ah
            var w = h * DsView.DS_W / DsView.DS_H
            if (w > aw) { w = aw; h = w * DsView.DS_H / DsView.DS_W }
            setMeasuredDimension(w, h)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        volumeControlStream = android.media.AudioManager.STREAM_MUSIC
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        surface = AspectSurface(this)
        surface.holder.setFixedSize(DsView.DS_W, DsView.DS_H)
        surface.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) { ready = true }
            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}
            override fun surfaceDestroyed(holder: SurfaceHolder) { ready = false }
        })
        @Suppress("ClickableViewAccessibility")
        surface.setOnTouchListener { v, ev ->
            val vw = v.width.toFloat(); val vh = v.height.toFloat()
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE ->
                    if (vw > 0f && vh > 0f) {
                        val dx = (ev.x / vw * DsView.DS_W).toInt().coerceIn(0, DsView.DS_W - 1)
                        val dy = (ev.y / vh * DsView.DS_H).toInt().coerceIn(0, DsView.DS_H - 1)
                        GameLink.setTouch(dx, dy, true)
                        if (ev.actionMasked == MotionEvent.ACTION_DOWN && touchLogs < 10) { touchLogs++
                            android.util.Log.i("MPDS-touch", "bottom-panel touch -> DS ($dx,$dy)") }   // e2e touch verification
                    }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> GameLink.setTouch(0, 0, false)
            }
            true
        }
        root.addView(surface, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        waiting=android.widget.TextView(this).apply {
            setBackgroundColor(Color.rgb(14,15,26));setTextColor(Color.rgb(255,92,138))
            gravity=android.view.Gravity.CENTER;textSize=24f;setPadding(32,32,32,32)
            text=GameLink.bottomLobby?:""
            visibility=if(GameLink.bottomLobby==null)android.view.View.GONE else android.view.View.VISIBLE
        }
        root.addView(waiting,FrameLayout.LayoutParams(-1,-1));PixelUi.style(waiting)
        noticeBanner=RoomNoticeBanner(this)
        PixelUi.style(noticeBanner)
        root.addView(noticeBanner,FrameLayout.LayoutParams(-1,-2,android.view.Gravity.TOP))
        setContentView(root)
        ui.post(updateWaiting)

        GameLink.bottomSink = { bmp -> blit(bmp) }
        android.util.Log.i("MPDS-life", "BottomScreenActivity created on display ${display?.displayId}")
    }

    private fun blit(bmp: Bitmap) {
        if (!ready) return
        val c = surface.holder.lockCanvas() ?: return
        try { c.drawBitmap(bmp, 0f, 0f, null) } finally { surface.holder.unlockCanvasAndPost(c) }
    }

    // gamepad while THIS display has key focus -> same merged input the main activity feeds
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val bit = keyBit(event.keyCode)
        if (bit != 0) {
            when (event.action) {
                KeyEvent.ACTION_DOWN -> GameLink.padBottom = GameLink.padBottom or bit
                KeyEvent.ACTION_UP   -> GameLink.padBottom = GameLink.padBottom and bit.inv()
            }
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    private fun keyBit(keyCode: Int): Int = when (keyCode) {
        KeyEvent.KEYCODE_BUTTON_A -> NativeBridge.Keys.A
        KeyEvent.KEYCODE_BUTTON_B -> NativeBridge.Keys.B
        KeyEvent.KEYCODE_BUTTON_X -> NativeBridge.Keys.X
        KeyEvent.KEYCODE_BUTTON_Y -> NativeBridge.Keys.Y
        KeyEvent.KEYCODE_BUTTON_L1 -> NativeBridge.Keys.L
        KeyEvent.KEYCODE_BUTTON_R1 -> NativeBridge.Keys.R
        KeyEvent.KEYCODE_BUTTON_START -> NativeBridge.Keys.START
        KeyEvent.KEYCODE_BUTTON_SELECT -> NativeBridge.Keys.SELECT
        KeyEvent.KEYCODE_DPAD_UP -> NativeBridge.Keys.UP
        KeyEvent.KEYCODE_DPAD_DOWN -> NativeBridge.Keys.DOWN
        KeyEvent.KEYCODE_DPAD_LEFT -> NativeBridge.Keys.LEFT
        KeyEvent.KEYCODE_DPAD_RIGHT -> NativeBridge.Keys.RIGHT
        else -> 0
    }

    override fun onDestroy() {
        ui.removeCallbacks(updateWaiting)
        super.onDestroy()
        if (GameLink.bottomSink != null) GameLink.bottomSink = null
        GameLink.padBottom = 0
        android.util.Log.i("MPDS-life", "BottomScreenActivity destroyed")
    }
}
