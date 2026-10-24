package eu.emufii.dsnemul

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SharedMemory
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.min

/** Emufii's in-APK front end for the assigned local console. */
class NemulGameActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private val closing = AtomicBoolean(false)
    private val retirementConfirmed = AtomicBoolean(false)
    private val audioLive = AtomicBoolean(false)
    private var remote: IBinder? = null
    private var bound = false
    private var showingFailure = false
    private var handle = ""
    private var request: NemulRoundRequest? = null
    private var shared: SharedMemory? = null
    private var memory: ByteBuffer? = null
    private lateinit var screens: Screens
    private lateinit var status: TextView
    private lateinit var microphone: Button
    @Volatile private var keys = 0
    @Volatile private var touchX = 0
    @Volatile private var touchY = 0
    @Volatile private var touchDown = false
    @Volatile private var micEnabled = false
    @Volatile private var mic = 0
    @Volatile private var muted = false

    private val death = IBinder.DeathRecipient { handler.post { retired() } }
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            remote = service
            try {
                service.linkToDeath(death, 0)
                if (closing.get()) { NemulRpc.stop(service); return }
                val r = request ?: error("Admission lost")
                check(NemulRpc.start(service, r))
                r.destroySecrets(); request = null
                handler.post(tick)
                startAudio(service)
            } catch (_: Exception) { status.text = "La partie n’a pas pu démarrer."; closeRound() }
        }
        override fun onServiceDisconnected(name: ComponentName) = retired()
        override fun onBindingDied(name: ComponentName) = retired()
        override fun onNullBinding(name: ComponentName) = retired()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        handle = intent.getStringExtra(DsNemulRuntime.EXTRA_HANDLE).orEmpty()
        request = DsNemulRuntime.claim(handle)
        if (request == null) { finish(); return }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(17, 21, 29))
        }
        status = TextView(this).apply { setTextColor(Color.WHITE); text = "Préparation de la partie…"; setPadding(16, 12, 16, 8) }
        root.addView(status)
        screens = Screens(this)
        root.addView(screens, LinearLayout.LayoutParams(-1, 0, 1f))
        addRow(root, listOf("◀" to 5, "▲" to 6, "▼" to 7, "▶" to 4, "L" to 9, "R" to 8))
        addRow(root, listOf("Select" to 2, "Start" to 3, "X" to 10, "Y" to 11, "B" to 1, "A" to 0))
        val tools = LinearLayout(this)
        tools.addView(Button(this).apply { text = "Quitter"; setOnClickListener { closeRound(); finish() } }, LinearLayout.LayoutParams(0, -2, 1f))
        tools.addView(Button(this).apply { text = "Son"; setOnClickListener {
            muted = !muted; text = if (muted) "Son coupé" else "Son"
        } }, LinearLayout.LayoutParams(0, -2, 1f))
        microphone = Button(this).apply { text = "Micro"; setOnClickListener {
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 77)
            } else toggleMicrophone()
        } }
        tools.addView(microphone, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(tools)
        setContentView(root)
        DsNemulRuntime.attachCloser(handle) { handler.post { closeRound(); finish() } }
        bound = runCatching {
            bindService(Intent(this, NemulEngineService::class.java), connection, BIND_AUTO_CREATE)
        }.getOrDefault(false)
        if (!bound) { request?.destroySecrets(); retired(); finish() }
    }

    private fun addRow(root: LinearLayout, buttons: List<Pair<String, Int>>) {
        val row = LinearLayout(this)
        buttons.forEach { (name, bit) ->
            row.addView(Button(this).apply {
                text = name
                contentDescription = "Bouton $name"
                setOnTouchListener { _, event ->
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> { keys = keys or (1 shl bit); sendInput() }
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { keys = keys and (1 shl bit).inv(); sendInput() }
                    }
                    true
                }
            }, LinearLayout.LayoutParams(0, -2, 1f))
        }
        root.addView(row)
    }

    private fun toggleMicrophone() {
        micEnabled = !micEnabled
        if (!micEnabled) { mic = 0; sendInput() }
        microphone.text = if (micEnabled) "Micro actif" else "Micro"
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 77 && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) toggleMicrophone()
    }

    private val tick = object : Runnable {
        private var polls = 0
        override fun run() {
            if (closing.get()) return
            val service = remote ?: return
            try {
                if (memory == null) {
                    shared = NemulRpc.memory(service)
                    memory = shared?.mapReadOnly()?.order(ByteOrder.LITTLE_ENDIAN)
                }
                memory?.let { screens.update(it) }
                if (polls++ % 20 == 0) {
                    val current = NemulRpc.status(service)
                    status.text = when (current.state) {
                        1 -> "Ouvre le mode multijoueur du jeu."
                        2 -> current.error.ifBlank { "La partie a été interrompue." }
                        else -> "Préparation de la partie…"
                    }
                    if (current.state == 2) {
                        showingFailure = true
                        closeRound()
                        android.app.AlertDialog.Builder(this@NemulGameActivity)
                            .setMessage(status.text).setPositiveButton("Fermer") { _, _ -> finish() }
                            .setOnCancelListener { finish() }.show()
                        return
                    }
                }
                handler.postDelayed(this, 16)
            } catch (_: Exception) { closeRound(); finish() }
        }
    }

    private fun sendInput() {
        if (closing.get()) return
        remote?.let { runCatching { NemulRpc.input(it, keys, touchX, touchY, touchDown, mic) } }
    }

    private fun startAudio(service: IBinder) {
        if (!audioLive.compareAndSet(false, true)) return
        Thread({
            var track: AudioTrack? = null
            var record: AudioRecord? = null
            try {
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO)
                val format = AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(32823).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).build()
                val playback = AudioTrack.Builder().setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                    .setAudioFormat(format).setBufferSizeInBytes(maxOf(16384,
                        AudioTrack.getMinBufferSize(32823, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT)))
                    .setTransferMode(AudioTrack.MODE_STREAM).build()
                track = playback
                playback.play()
                val capture = ShortArray(320)
                while (audioLive.get()) {
                    if (micEnabled && record == null && checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                        record = runCatching {
                            val candidate = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, 16000,
                                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                                maxOf(4096, AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)))
                            try {
                                check(candidate.state == AudioRecord.STATE_INITIALIZED)
                                candidate.startRecording()
                                candidate
                            } catch (failure: Exception) { candidate.release(); throw failure }
                        }.getOrElse {
                            // A busy/revoked microphone must not also stop game audio.
                            micEnabled = false; mic = 0; sendInput()
                            handler.post { microphone.text = "Micro indisponible" }
                            null
                        }
                    }
                    if (!micEnabled && record != null) {
                        record?.let { it.stop(); it.release() }; record = null; mic = 0; sendInput()
                    }
                    record?.let { source ->
                        val count = source.read(capture, 0, capture.size, AudioRecord.READ_NON_BLOCKING)
                        var peak = 0
                        for (i in 0 until maxOf(count, 0)) peak = maxOf(peak, abs(capture[i].toInt()))
                        if (count > 0) { mic = (peak / 128).coerceIn(0, 255); sendInput() }
                    }
                    val samples = NemulRpc.audio(service)
                    if (samples.isNotEmpty()) {
                        if (muted) samples.fill(0)
                        playback.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
                    } else Thread.sleep(6)
                }
            } catch (_: Exception) { /* Closing the binder also ends audio. */ }
            finally {
                audioLive.set(false)
                runCatching { record?.stop() }; record?.release(); track?.release()
            }
        }, "silver-audio").start()
    }

    private fun closeRound() {
        if (!closing.compareAndSet(false, true)) return
        handler.removeCallbacks(tick)
        audioLive.set(false)
        micEnabled = false
        request?.destroySecrets(); request = null
        val service = remote
        if (service == null) {
            // If binding is pending, onServiceConnected retires it before START.
            if (!bound) retired()
        } else Thread({ runCatching { NemulRpc.stop(service) }
            if (!service.isBinderAlive) handler.post { retired() }
        }, "silver-close").start()
    }

    private fun retired() {
        if (!retirementConfirmed.compareAndSet(false, true)) return
        closing.set(true)
        audioLive.set(false)
        request?.destroySecrets(); request = null
        handler.removeCallbacks(tick)
        if (bound) { runCatching { unbindService(connection) }; bound = false }
        remote = null
        DsNemulRuntime.retired(handle)
        if (!isFinishing && !showingFailure) finish()
    }
    override fun onDestroy() {
        closeRound()
        memory?.let { SharedMemory.unmap(it) }; memory = null
        shared?.close(); shared = null
        super.onDestroy()
    }
    override fun onPause() {
        // Android may revoke microphone access in the background. Also release
        // held buttons/touch when another Activity takes focus.
        micEnabled = false; mic = 0; keys = 0; touchDown = false
        if (::microphone.isInitialized) microphone.text = "Micro"
        sendInput()
        super.onPause()
    }
    override fun onResume() {
        super.onResume()
        if (!closing.get()) remote?.let(::startAudio)
    }
    @Suppress("DEPRECATION") override fun onBackPressed() { closeRound(); super.onBackPressed() }
    private fun keyBit(code: Int): Int? = when (code) {
        KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_Z -> 0
        KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_X -> 1
        KeyEvent.KEYCODE_BUTTON_SELECT, KeyEvent.KEYCODE_SHIFT_LEFT -> 2
        KeyEvent.KEYCODE_BUTTON_START, KeyEvent.KEYCODE_ENTER -> 3
        KeyEvent.KEYCODE_DPAD_RIGHT -> 4
        KeyEvent.KEYCODE_DPAD_LEFT -> 5
        KeyEvent.KEYCODE_DPAD_UP -> 6
        KeyEvent.KEYCODE_DPAD_DOWN -> 7
        KeyEvent.KEYCODE_BUTTON_R1, KeyEvent.KEYCODE_W -> 8
        KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_Q -> 9
        KeyEvent.KEYCODE_BUTTON_X, KeyEvent.KEYCODE_A -> 10
        KeyEvent.KEYCODE_BUTTON_Y, KeyEvent.KEYCODE_S -> 11
        else -> null
    }
    override fun onKeyDown(code: Int, event: KeyEvent): Boolean = keyBit(code)?.let {
        keys = keys or (1 shl it); sendInput(); true
    } ?: super.onKeyDown(code, event)
    override fun onKeyUp(code: Int, event: KeyEvent): Boolean = keyBit(code)?.let {
        keys = keys and (1 shl it).inv(); sendInput(); true
    } ?: super.onKeyUp(code, event)

    private inner class Screens(context: Context) : View(context) {
        private val top = Bitmap.createBitmap(256, 192, Bitmap.Config.ARGB_8888)
        private val bottom = Bitmap.createBitmap(256, 192, Bitmap.Config.ARGB_8888)
        private val pixels = IntArray(NemulRpc.PIXELS * 2)
        private val paint = Paint().apply { isFilterBitmap = false }
        private val targetTop = RectF()
        private val targetBottom = RectF()
        private var lastSequence = 0L
        fun update(buffer: ByteBuffer) {
            val start = buffer.getLong(0)
            if (start == 0L || start == lastSequence || (start and 1L) != 0L) return
            buffer.position(8)
            buffer.asIntBuffer().get(pixels)
            if (buffer.getLong(0) != start) return
            lastSequence = start
            top.setPixels(pixels, 0, 256, 0, 0, 256, 192)
            bottom.setPixels(pixels, NemulRpc.PIXELS, 256, 0, 0, 256, 192)
            invalidate()
        }
        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            val scale = min(w / 256f, h / 384f)
            val x = (w - 256 * scale) / 2
            val y = (h - 384 * scale) / 2
            targetTop.set(x, y, x + 256 * scale, y + 192 * scale)
            targetBottom.set(x, y + 192 * scale, x + 256 * scale, y + 384 * scale)
        }
        override fun onDraw(canvas: Canvas) {
            canvas.drawBitmap(top, null, targetTop, paint)
            canvas.drawBitmap(bottom, null, targetBottom, paint)
        }
        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (event.actionMasked == MotionEvent.ACTION_DOWN && !targetBottom.contains(event.x, event.y)) return false
            touchX = ((event.x - targetBottom.left) * 256 / targetBottom.width()).toInt().coerceIn(0, 255)
            touchY = ((event.y - targetBottom.top) * 192 / targetBottom.height()).toInt().coerceIn(0, 191)
            touchDown = event.actionMasked != MotionEvent.ACTION_UP && event.actionMasked != MotionEvent.ACTION_CANCEL
            sendInput()
            if (!touchDown) performClick()
            return true
        }
        override fun performClick(): Boolean { super.performClick(); return true }
    }
}
