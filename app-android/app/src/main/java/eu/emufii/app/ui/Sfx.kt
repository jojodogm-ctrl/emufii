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

/**
 * The interface's sounds: the cursor landing and the press, plus the trailer's five. `SoundPool` rather
 * than `MediaPlayer`: these last 96 and 144 ms and must fire without latency. They sit
 * behind Android's own interface-sound setting rather than inventing a second one.
 * pourquoi : docs/decisions/sons.md § Two sounds, one family
 */
object Sfx {

    /** Kept at preparation so [click] and [hover] ask nothing of the caller: not every place a sound fires is a composable. */
    private var app: Context? = null

    private var pool: SoundPool? = null
    private var hoverId = 0
    private var clickId = 0
    private var confirmId = 0
    private var popId = 0
    private var notifyId = 0
    private var toggleId = 0
    private var tickId = 0

    /** What is decoded: playing an id that is not ready yet does nothing. */
    private val loaded = mutableSetOf<Int>()

    fun prepare(context: Context) {
        if (pool != null) return
        app = context.applicationContext
        val attrs = AudioAttributes.Builder()
            // Media, not USAGE_ASSISTANCE_SONIFICATION: that one sorts to STREAM_SYSTEM,
            // aliased to the ringer, which the Thor's volume rocker does not move.
            // Measured 2026-09-02: music 15/15 while system sat at 4/7, so the sounds
            // stayed faint however far the rocker went.
            // pourquoi : docs/decisions/sons.md § Android's own setting is authoritative
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

    /**
     * A screen arriving places its cursor in two steps (the first focusable, then the
     * remembered one), and each landing sounded: one hover per arrival, not two.
     */
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

    /*
     * The trailer's four, plus the code tick. Same pool, same system setting.
     * pourquoi : docs/decisions/sons.md § The trailer's sounds
     */

    /** Two rising notes: a step done, a friend added. */
    fun confirm() = play(confirmId, CONFIRM_VOLUME)

    /** A player joining, a friend appearing. */
    fun pop() = play(popId, POP_VOLUME)

    /** An in-app alert arriving. */
    fun alert() = play(notifyId, NOTIFY_VOLUME)

    /** A switch changing side: replaces the click there, never adds to it. */
    fun toggle() = play(toggleId, CLICK_VOLUME)

    /** A code character landing, typed or written; distinct from the cursor's hover. */
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

    /** Six: a code writing itself ticks every 115 ms while a press may still ring. */
    private const val MAX_STREAMS = 6

    /** Below the press: hover fires on every cell crossed, and a move as loud as an action suggests something happened. */
    private const val HOVER_VOLUME = 0.55f
    private const val CLICK_VOLUME = 1.0f
    private const val CONFIRM_VOLUME = 0.85f
    private const val POP_VOLUME = 0.7f
    private const val NOTIFY_VOLUME = 0.8f
}

/**
 * `Modifier.tap` covers everything the app makes clickable itself, but not Material's
 * controls, which take their `onClick` as a parameter and pass through no modifier of
 * ours: `Button`, `OutlinedButton`, `Surface(onClick)`. Those were silent.
 * pourquoi : docs/decisions/sons.md § The sound and the click are one call
 */
fun sounded(onClick: () -> Unit): () -> Unit = { Sfx.click(); onClick() }

/**
 * This app's replacement for `Modifier.clickable`: one call for the click and the sound.
 * pourquoi : docs/decisions/sons.md § The sound and the click are one call
 */
@Composable
fun Modifier.tap(
    enabled: Boolean = true,
    role: Role? = null,
    /** A switch says `toggle`: the trailer gives it its own sound. */
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

/**
 * The short press and the hold, both audible.
 * pourquoi : docs/decisions/sons.md § The sound and the click are one call
 */
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

/**
 * Silences Android's interface sounds for this window; lay it at the root of every one, the
 * activity, each `Dialog`, the rear panel, or the two sets overlap.
 * pourquoi : docs/decisions/sons.md § Silencing Android's own is done view by view
 */
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
