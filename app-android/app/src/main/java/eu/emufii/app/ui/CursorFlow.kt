package eu.emufii.app.ui

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Matrix
import android.graphics.Shader
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import eu.emufii.app.ui.rememberSlowMillis
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

internal object CursorFlow {

    const val STEPS = 22

    /** One step per frame beat; a fractional rate judders. */
    const val PERIOD_MS = (STEPS * FRAME_INTERVAL_MS).toInt()

    private const val MAX_SIDE = 192

    private val cache = object : LinkedHashMap<Key, BitmapShader>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Key, BitmapShader>) =
            size > 96
    }

    private data class Key(
        val w: Int,
        val h: Int,
        val band: Int,
        val radius: Int,
        val start: Int,
        val end: Int,
        val step: Int,
    )

    fun shader(
        w: Float,
        h: Float,
        band: Float,
        radius: Float,
        start: Color,
        end: Color,
        step: Int,
    ): Shader? {
        if (w < 1f || h < 1f) return null
        val key = Key(
            w = w.roundToInt(),
            h = h.roundToInt(),
            band = (band * 10f).roundToInt(),
            radius = (radius * 10f).roundToInt(),
            start = start.toArgb(),
            end = end.toArgb(),
            step = ((step % STEPS) + STEPS) % STEPS,
        )
        cache[key]?.let { return it }

        val scale = min(1f, MAX_SIDE / max(w, h))
        val bw = max(2, (w * scale).roundToInt())
        val bh = max(2, (h * scale).roundToInt())

        val half = band / 2f
        val hx = max(1f, w / 2f - half)
        val hy = max(1f, h / 2f - half)
        val r = radius.coerceIn(0f, min(hx, hy))
        val ax = hx - r
        val ay = hy - r
        val quarter = (r * PI / 2.0).toFloat()
        val perimeter = 4f * (ax + ay + quarter)
        if (perimeter <= 0f) return null

        val s1 = ax
        val s2 = s1 + quarter
        val s3 = s2 + 2f * ay
        val s4 = s3 + quarter
        val s5 = s4 + 2f * ax
        val s6 = s5 + quarter
        val s7 = s6 + 2f * ay

        val phase = key.step.toFloat() / STEPS
        val sr = start.red; val sg = start.green; val sb = start.blue
        val er = end.red; val eg = end.green; val eb = end.blue

        val pixels = IntArray(bw * bh)
        for (row in 0 until bh) {
            val y = (row + 0.5f) / scale - h / 2f
            for (col in 0 until bw) {
                val x = (col + 0.5f) / scale - w / 2f
                val t = arcLength(x, y, ax, ay, r, s1, s2, s3, s4, s5, s6, s7, perimeter)
                var u = (t / perimeter - phase) % 1f
                if (u < 0f) u += 1f
                val mix = 0.5f - 0.5f * cos(2.0 * PI * u).toFloat()
                val cr = ((sr + (er - sr) * mix) * 255f).roundToInt().coerceIn(0, 255)
                val cg = ((sg + (eg - sg) * mix) * 255f).roundToInt().coerceIn(0, 255)
                val cb = ((sb + (eb - sb) * mix) * 255f).roundToInt().coerceIn(0, 255)
                pixels[row * bw + col] = (0xFF shl 24) or (cr shl 16) or (cg shl 8) or cb
            }
        }

        val bitmap = Bitmap.createBitmap(pixels, bw, bh, Bitmap.Config.ARGB_8888)
        val shader = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        shader.setLocalMatrix(
            Matrix().apply {
                setScale(w / bw, h / bh)
                // The cursor box origin is at (-band, -band).
                postTranslate(-band, -band)
            }
        )
        cache[key] = shader
        return shader
    }

    private fun arcLength(
        x: Float,
        y: Float,
        ax: Float,
        ay: Float,
        r: Float,
        s1: Float,
        s2: Float,
        s3: Float,
        s4: Float,
        s5: Float,
        s6: Float,
        s7: Float,
        perimeter: Float,
    ): Float {
        val halfPi = (PI / 2.0).toFloat()
        return when {
            x in -ax..ax && y < 0f -> if (x >= 0f) x else perimeter + x
            x > ax && y in -ay..ay -> s2 + (y + ay)
            x in -ax..ax && y > 0f -> s4 + (ax - x)
            x < -ax && y in -ay..ay -> s6 + (ay - y)
            x > ax && y < -ay -> s1 + (atan2(y + ay, x - ax) + halfPi).coerceIn(0f, halfPi) * r
            x > ax -> s3 + atan2(y - ay, x - ax).coerceIn(0f, halfPi) * r
            y > ay -> s5 + (atan2(y - ay, x + ax) - halfPi).coerceIn(0f, halfPi) * r
            else -> {
                var a = atan2(y + ay, x + ax)
                if (a < 0f) a += (2.0 * PI).toFloat()
                s7 + (a - PI.toFloat()).coerceIn(0f, halfPi) * r
            }
        }
    }
}

@Composable
internal fun rememberFlowStep(): Int {
    val millis = rememberSlowMillis()
    return ((millis / CursorFlow.PERIOD_MS) * CursorFlow.STEPS).toInt().mod(CursorFlow.STEPS)
}
