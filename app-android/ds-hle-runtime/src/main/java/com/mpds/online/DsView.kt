package com.mpds.online

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView

/**
 * Renders the DS dual screen (two 256x192 panels stacked vertically, each scaled to view width) and runs
 * the emulation loop on its own thread: nativeRunFrame -> nativeGetFramebuffer -> blit. Touch on the
 * bottom panel maps to DS touch coordinates (0..255 / 0..191) and is forwarded via nativeSetInput.
 *
 * Button input (from the on-screen overlay in MainActivity) is merged in through [setButtons].
 */
class DsView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    SurfaceView(context, attrs), SurfaceHolder.Callback {

    companion object {
        const val DS_W = 256
        const val DS_H = 192
    }

    private val topBmp = Bitmap.createBitmap(DS_W, DS_H, Bitmap.Config.ARGB_8888)
    private val btmBmp = Bitmap.createBitmap(DS_W, DS_H, Bitmap.Config.ARGB_8888)
    private val topPx = IntArray(DS_W * DS_H)
    private val btmPx = IntArray(DS_W * DS_H)

    private val paint = Paint()   // nearest-neighbor upscale: far cheaper than bilinear, crisper for DS pixel art
    private val srcRect = Rect(0, 0, DS_W, DS_H)
    private val topDst = Rect()
    private val btmDst = Rect()

    // DUAL-SCREEN mode (AYN Thor-class clamshells): this view shows ONLY the DS TOP screen (the device's top panel);
    // the DS BOTTOM screen + touch live on the second display via BottomScreenActivity (fed through GameLink.bottomSink).
    @Volatile var dualMode = false
        set(v) { field = v; holder.setFixedSize(DS_W, if (v) DS_H else DS_H * 2); post { requestLayout() } }

    @Volatile private var running = false
    @Volatile private var renderGeneration = 0L
    @Volatile private var emuActive = false
    private val singleFrameLock = Any()
    @Volatile private var mpSeat = 0     // 0 = single-console; 1..4 = render+drive THIS seat of the 4-player MP
    private var thread: Thread? = null

    // input now lives in GameLink (shared with BottomScreenActivity); this view reads it each frame.

    // FPS feedback
    @Volatile var fps: Float = 0f
        private set
    var onFps: ((Float) -> Unit)? = null
    var onError: (() -> Unit)? = null

    // pacing A/B: read once at render-loop start. ON -> sub-ms sleep (closer to true 60.000Hz vs the default whole-ms).
    private var precisePace = false

    init {
        holder.addCallback(this)
        keepScreenOn = true
    }

    /** Called once the console is booted; starts the emulation/render loop. */
    fun startEmu() {
        mpSeat = 0
        emuActive = true
    }

    fun stopEmu() {
        emuActive = false
        mpSeat = 0
        GameLink.overlay = 0; GameLink.padMain = 0; GameLink.padBottom = 0
        GameLink.setTouch(0, 0, false)
    }

    /** Worker-only retirement after closing the network carrier (which wakes
     * blocked WM calls). Prevents a pre-read emuActive from entering JNI after
     * native teardown. Does not destroy either physical display surface. */
    fun stopEmuAndDrain() {
        stopEmu()
        synchronized(singleFrameLock) { /* await any in-flight JNI frame */ }
    }

    /** MP mode: native side already booted 4 consoles + launched the MP threads; render+drive seat (1..4). */
    fun startMp(seat: Int) {
        check(!BuildConfig.SINGLE_OWNER) { "Legacy multi-owner renderer is unavailable" }
        mpSeat = seat
        emuActive = true
    }

    fun setButtons(mask: Int) { GameLink.overlay = mask }

    override fun surfaceCreated(holder: SurfaceHolder) {
        // Fix the surface buffer to the DS native dual-screen res (256x384). drawFrame then blits the two panels
        // 1:1 -- no CPU upscale, no full-screen clear -- and SurfaceFlinger GPU-composites the upscale to the view.
        // This moves ALL scaling off the CPU (lockHardwareCanvas was broken on this surface). onMeasure locks the
        // view to the 256:384 aspect so the GPU upscale isn't distorted.
        holder.setFixedSize(DS_W, if (dualMode) DS_H else DS_H * 2)
        running = true
        val generation = ++renderGeneration
        thread = Thread({ renderLoop(generation) }, "ds-emu").also { it.start() }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val availW = MeasureSpec.getSize(widthMeasureSpec)
        val availH = MeasureSpec.getSize(heightMeasureSpec)
        // height is set by the layout weight; lock width to the 256:384 dual-screen aspect (centered, black bars).
        val panelH = if (dualMode) DS_H else DS_H * 2   // dual mode: this view is the 4:3 TOP screen only
        var h = availH
        var w = h * DS_W / panelH
        if (w > availW) { w = availW; h = w * panelH / DS_W }
        setMeasuredDimension(w, h)
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        layoutScreens(width, height)
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        running = false
        ++renderGeneration
        thread?.join(1000)
        thread = null
    }

    private fun layoutScreens(w: Int, h: Int) {
        // Each panel keeps the 256:192 (4:3) aspect; stack them, scaled to fit width, centered vertically.
        val panelW = w
        val panelH = (panelW * DS_H) / DS_W
        var totalH = panelH * 2
        var scaledW = panelW
        if (totalH > h) {
            // too tall -> scale to height instead
            val ph = h / 2
            scaledW = (ph * DS_W) / DS_H
            totalH = ph * 2
        }
        val finalPanelH = totalH / 2
        val left = (w - scaledW) / 2
        val top = (h - totalH) / 2
        topDst.set(left, top, left + scaledW, top + finalPanelH)
        btmDst.set(left, top + finalPanelH, left + scaledW, top + finalPanelH * 2)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (dualMode) return false   // dual mode: the DS touch screen is the OTHER panel (BottomScreenActivity handles it)
        // the view IS the aspect-correct DS dual screen; its bottom half maps to the DS touch screen (0..255/0..191).
        val vw = width.toFloat(); val vh = height.toFloat()
        val x = event.x; val y = event.y
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                if (vw > 0f && vh > 0f && y >= vh / 2f && x >= 0f && x <= vw) {
                    GameLink.setTouch(
                        (x / vw * DS_W).toInt().coerceIn(0, DS_W - 1),
                        ((y - vh / 2f) / (vh / 2f) * DS_H).toInt().coerceIn(0, DS_H - 1), true)
                } else GameLink.setTouch(0, 0, false)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> GameLink.setTouch(0, 0, false)
        }
        return true
    }

    private fun renderLoop(generation: Long) {
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_DISPLAY)
        precisePace = java.io.File(context.getExternalFilesDir(null), "precisepace").exists()   // marker = ON (default OFF)
        var frames = 0
        var lastT = System.nanoTime()
        val targetNs = 1_000_000_000L / 60L
        while (running && generation == renderGeneration) {
            val frameStart = System.nanoTime()
            if (mpSeat > 0) {
                // 4-player MP: the native threads run the 4 consoles; feed MY input + render MY seat's screens.
                if (NativeBridge.nativeMpRunning() == 1) {
                    NativeBridge.nativeSetLocalInput(GameLink.buttonMask(), GameLink.touchX, GameLink.touchY, GameLink.touchDown)
                    NativeBridge.nativeRenderMP(mpSeat - 1, topBmp, btmBmp)   // native blits straight into the bitmaps
                    drawFrame()
                    if (dualMode) GameLink.bottomSink?.invoke(btmBmp)   // second panel: the bottom activity blits the DS touch screen
                    frames++
                    val now = System.nanoTime()
                    if (now - lastT >= 1_000_000_000L) {
                        fps = frames * 1e9f / (now - lastT); onFps?.invoke(fps)
                        android.util.Log.i("MPDS-render", "MP seat%d render %.0f fps".format(mpSeat, fps))
                        frames = 0; lastT = now
                    }
                } else { drawIdle(); android.util.Log.i("MPDS-render", "MP not running yet"); Thread.sleep(16) }
            } else if (emuActive) {
                val received = try { synchronized(singleFrameLock) {
                    if (!emuActive || !running || generation != renderGeneration ||
                        (if(BuildConfig.SINGLE_OWNER)RoomEngine.nativeIsBooted() else NativeBridge.nativeIsBooted()) != 1) false
                    else {
                        if(BuildConfig.SINGLE_OWNER) {
                            RoomEngine.nativeSetInput(GameLink.buttonMask(),GameLink.touchX,GameLink.touchY,GameLink.touchDown)
                            RoomEngine.nativeRunFrame()
                            check(RoomEngine.nativeIsBooted()==1) { "native-frame-stopped" }
                        } else {
                            NativeBridge.nativeSetInput(GameLink.buttonMask(), GameLink.touchX, GameLink.touchY, GameLink.touchDown)
                            NativeBridge.nativeRunFrame()
                        }
                        if (!emuActive || !running || generation != renderGeneration) false
                        else {
                            if(BuildConfig.SINGLE_OWNER)RoomEngine.nativeGetFramebuffer(topPx,btmPx)
                            else NativeBridge.nativeGetFramebuffer(topPx, btmPx)
                            true
                        }
                    }
                } } catch (_:Exception) { stopEmu();onError?.invoke();false }
                if (!running || generation != renderGeneration) break
                if (!received || !emuActive) { Thread.sleep(16); continue }
                topBmp.setPixels(topPx, 0, DS_W, 0, 0, DS_W, DS_H)
                btmBmp.setPixels(btmPx, 0, DS_W, 0, 0, DS_W, DS_H)
                drawFrame()
                // The second physical display consumes the same sole owner's
                // bottom framebuffer, just as it did in the legacy MP renderer.
                if (dualMode) GameLink.bottomSink?.invoke(btmBmp)

                frames++
                val now = System.nanoTime()
                if (now - lastT >= 1_000_000_000L) {
                    fps = frames * 1e9f / (now - lastT)
                    onFps?.invoke(fps)
                    frames = 0; lastT = now
                }
            } else {
                drawIdle()
                Thread.sleep(16)
            }
            // frame pacing (cap ~60fps). Thread.sleep(ms) truncates to whole ms, losing up to ~1ms/frame vs the
            // 16.67ms target (drift/hitch against vsync). The refined path keeps the sub-ms remainder via sleep(0,nanos)
            // so the cap lands closer to true 60.000Hz; default keeps the proven whole-ms behaviour (A/B).
            val elapsed = System.nanoTime() - frameStart
            val sleepNs = targetNs - elapsed
            if (sleepNs > 1_000_000L) {
                try {
                    if (precisePace) Thread.sleep(sleepNs / 1_000_000L, (sleepNs % 1_000_000L).toInt())
                    else Thread.sleep(sleepNs / 1_000_000L)
                } catch (_: InterruptedException) {}
            }
        }
    }

    private fun drawFrame() {
        // surface is 256x384 -> blit the two panels 1:1 (the GPU compositor scales to the view). No scaling, no
        // clear: the two bitmaps cover the whole buffer.
        val c = holder.lockCanvas() ?: return
        try {
            c.drawBitmap(topBmp, 0f, 0f, null)
            if (!dualMode) c.drawBitmap(btmBmp, 0f, DS_H.toFloat(), null)   // dual mode: the bottom lives on panel 2
        } finally {
            holder.unlockCanvasAndPost(c)
        }
    }

    private fun drawIdle() {
        val c = holder.lockCanvas() ?: return
        try { c.drawColor(Color.rgb(18, 18, 24)) } finally { holder.unlockCanvasAndPost(c) }
    }
}
