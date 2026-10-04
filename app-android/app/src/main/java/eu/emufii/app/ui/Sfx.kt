package eu.emufii.app.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.provider.Settings
import android.view.View
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Indication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.semantics.Role
import eu.emufii.app.R

object Sfx {

    private var app: Context? = null

    private var pool: SoundPool? = null
    private var hoverId = 0
    private var clickId = 0
    private var confirmId = 0
    private var popId = 0
    private var notifyId = 0
    private var toggleId = 0
    private var tickId = 0

    private val loaded = mutableSetOf<Int>()

    fun prepare(context: Context) {
        if (pool != null) return
        app = context.applicationContext
        val attrs = AudioAttributes.Builder()
            // Not SONIFICATION: that maps to STREAM_SYSTEM, which the Thor's volume rocker doesn't move.
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val p = SoundPool.Builder().setMaxStreams(MAX_STREAMS).setAudioAttributes(attrs).build()
        p.setOnLoadCompleteListener { _, id, status -> if (status == 0) loaded += id }
        hoverId = p.load(context.applicationContext, R.raw.sfx_hover, 1)
        clickId = p.load(context.applicationContext, R.raw.sfx_click, 1)
        confirmId = p.load(context.applicationContext, R.raw.sfx_confirm, 1)
        popId = p.load(context.applicationContext, R.raw.sfx_pop, 1)
        notifyId = p.load(context.applicationContext, R.raw.sfx_notify, 1)
        toggleId = p.load(context.applicationContext, R.raw.sfx_toggle, 1)
        tickId = p.load(context.applicationContext, R.raw.sfx_tick, 1)
        pool = p
    }

    /** Screen arrival lands the cursor twice; play one hover, not two. */
    private var settleUntil = 0L
    private var settledHoverPlayed = false

    fun settle(windowMs: Long = 400) {
        settleUntil = android.os.SystemClock.uptimeMillis() + windowMs
        settledHoverPlayed = false
    }

    fun hover() {
        if (android.os.SystemClock.uptimeMillis() < settleUntil) {
            if (settledHoverPlayed) return
            settledHoverPlayed = true
        }
        play(hoverId, HOVER_VOLUME)
    }

    fun click() = play(clickId, CLICK_VOLUME)

    fun confirm() = play(confirmId, CONFIRM_VOLUME)

    fun pop() = play(popId, POP_VOLUME)

    fun alert() = play(notifyId, NOTIFY_VOLUME)

    fun toggle() = play(toggleId, CLICK_VOLUME)

    fun tick() = play(tickId, HOVER_VOLUME)

    private fun play(id: Int, volume: Float) {
        val context = app ?: return
        if (id == 0 || id !in loaded) return
        val on = runCatching {
            Settings.System.getInt(
                context.contentResolver,
                Settings.System.SOUND_EFFECTS_ENABLED,
                1
            ) != 0
        }.getOrDefault(true)
        if (on) pool?.play(id, volume, volume, 1, 0, 1f)
    }

    private const val MAX_STREAMS = 6

    private const val HOVER_VOLUME = 0.55f
    private const val CLICK_VOLUME = 1.0f
    private const val CONFIRM_VOLUME = 0.85f
    private const val POP_VOLUME = 0.7f
    private const val NOTIFY_VOLUME = 0.8f
}

/** For Material controls that take onClick directly and bypass `Modifier.tap`. */
fun sounded(onClick: () -> Unit): () -> Unit = { Sfx.click(); onClick() }

@Composable
fun Modifier.tap(
    enabled: Boolean = true,
    role: Role? = null,
    sound: () -> Unit = Sfx::click,
    onClick: () -> Unit
): Modifier {
    return this.clickable(enabled = enabled, role = role) { sound(); onClick() }
}

@Composable
fun Modifier.tap(
    interactionSource: MutableInteractionSource,
    indication: Indication?,
    enabled: Boolean = true,
    role: Role? = null,
    onClick: () -> Unit
): Modifier {
    return this.clickable(
        interactionSource = interactionSource,
        indication = indication,
        enabled = enabled,
        role = role
    ) { Sfx.click(); onClick() }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.tapOrHold(
    interactionSource: MutableInteractionSource,
    indication: Indication?,
    enabled: Boolean = true,
    onLongClick: () -> Unit,
    onClick: () -> Unit
): Modifier {
    return this.combinedClickable(
        interactionSource = interactionSource,
        indication = indication,
        enabled = enabled,
        onLongClick = { Sfx.click(); onLongClick() },
        onClick = { Sfx.click(); onClick() }
    )
}

@Composable
fun SilenceSystemSfx() {
    val view = LocalView.current
    SideEffect {
        // `playSoundEffect` is gated by the flag on the calling view, not always Compose's.
        var v: View? = view
        while (v != null) {
            v.isSoundEffectsEnabled = false
            v = v.parent as? View
        }
    }
}
