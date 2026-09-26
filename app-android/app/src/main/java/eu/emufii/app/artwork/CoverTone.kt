package eu.emufii.app.artwork

import android.graphics.Bitmap
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.snapshots.Snapshot
import java.util.concurrent.ConcurrentHashMap
import androidx.compose.ui.graphics.Color
import coil3.Image
import coil3.toBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * The colour a cover glows with. Only the icons embedded in 3DS and DS files carried an
 * accent (`Rom.accentArgb`); every downloaded cover had none, so the coloured shadow never
 * showed on most of a library. It is read here from the picture actually displayed, once,
 * off the main thread, and kept for the process.
 * pourquoi : docs/decisions/matiere-et-mouvement-trailer.md § Shadows take the game's colour
 */
object CoverTone {

    /**
     * Plain map, plus one counter that says "a tone arrived". Never a state made on read:
     * a composition already running (the lazy list prefetches in its own snapshot) cannot
     * see a state born after it, and reading one crashed the app twice on 2026-09-26. The
     * counter is made at application start, before any composition; the tone is read at
     * draw time, so an arrival repaints a shadow and recomposes nothing.
     */
    private val tones = ConcurrentHashMap<Any, Color>()
    private val pending: MutableSet<Any> = ConcurrentHashMap.newKeySet()
    private val arrived = Snapshot.global { mutableIntStateOf(0) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Called from `EmufiiApplication.onCreate`, so the counter exists before any screen. */
    fun warm() = Unit

    /** For a draw lambda: subscribes the redraw to arrivals. Null until decoded, or for a grey cover. */
    fun of(model: Any?): Color? {
        arrived.intValue
        return model?.let { tones[it] }
    }

    fun learn(model: Any?, image: Image) {
        if (model == null || !pending.add(model)) return
        scope.launch {
            // A hardware bitmap cannot be read; the copy is 24 by 24 and costs nothing.
            val tone = runCatching {
                val source = image.toBitmap()
                val readable = if (source.config == Bitmap.Config.HARDWARE) {
                    source.copy(Bitmap.Config.ARGB_8888, false)
                } else source
                dominant(Bitmap.createScaledBitmap(readable, SAMPLE, SAMPLE, true))
            }.getOrNull()
            if (tone != null) {
                tones[model] = tone
                Snapshot.withMutableSnapshot { arrived.intValue++ }
            }
        }
    }

    /**
     * The hue that covers the most of the picture, not the brightest one. Weighing pixels
     * by saturation times brightness let a small vivid logo win over the colour the cover is
     * actually made of: Luigi's Mansion glowed yellow for its face. Here a pixel weighs its
     * saturation squared, so skin and washed tones lose to real colour, neighbouring hues
     * pool together, and a cover with too little colour gives nothing. Checked against the
     * 48 icons cached on the Thor: Luigi green, Kid Icarus blue, Yo-kai purple.
     */
    private fun dominant(bitmap: Bitmap): Color? {
        val weight = FloatArray(BUCKETS)
        val sums = Array(BUCKETS) { FloatArray(3) }
        val hsv = FloatArray(3)
        var coloured = 0f
        var counted = 0
        for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
            val p = bitmap.getPixel(x, y)
            if (android.graphics.Color.alpha(p) < 128) continue
            counted++
            android.graphics.Color.colorToHSV(p, hsv)
            if (hsv[1] < 0.25f || hsv[2] < 0.18f) continue
            val w = hsv[1] * hsv[1]
            val b = ((hsv[0] / 360f) * BUCKETS).toInt().coerceIn(0, BUCKETS - 1)
            weight[b] += w
            coloured += hsv[1]
            sums[b][0] += android.graphics.Color.red(p) * w
            sums[b][1] += android.graphics.Color.green(p) * w
            sums[b][2] += android.graphics.Color.blue(p) * w
        }
        if (counted == 0 || coloured < counted * MIN_COLOURED) return null
        // A hue pooled with its two neighbours, so a gradient is not split in three.
        fun pooled(b: Int) =
            weight[b] + 0.5f * (weight[(b + BUCKETS - 1) % BUCKETS] + weight[(b + 1) % BUCKETS])
        val best = (0 until BUCKETS).maxByOrNull { pooled(it) } ?: return null
        var r = 0f; var g = 0f; var bl = 0f; var w = 0f
        for (d in -1..1) {
            val k = (best + d + BUCKETS) % BUCKETS
            r += sums[k][0]; g += sums[k][1]; bl += sums[k][2]; w += weight[k]
        }
        if (w <= 0f) return null
        val rgb = android.graphics.Color.rgb((r / w).toInt(), (g / w).toInt(), (bl / w).toInt())
        android.graphics.Color.colorToHSV(rgb, hsv)
        // Lifted a little so it reads as light, never pushed to neon.
        hsv[1] = hsv[1].coerceIn(0.45f, 0.85f)
        hsv[2] = hsv[2].coerceAtLeast(0.70f)
        return Color(android.graphics.Color.HSVToColor(hsv))
    }

    private const val SAMPLE = 24
    private const val BUCKETS = 18

    /** Under this share of the picture in real colour, the cover is essentially grey. */
    private const val MIN_COLOURED = 0.06f
}

/**
 * One size for every cover request, the grid's, the carousel's, the card's and the panel's.
 * With the size left to layout, Coil looks the memory cache up only once measured, so a
 * tile arriving at the end of a flight drew its bare white plate for a frame before its
 * cover: the flash. A fixed size makes the lookup synchronous, and one size makes every
 * place hit the same entry. 640 covers the carousel's card at the Thor's density.
 * pourquoi : docs/decisions/matiere-et-mouvement-trailer.md § Library
 */
const val COVER_REQUEST_PX = 640
