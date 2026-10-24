package com.mpds.online

import android.content.Context
import java.io.File
import java.util.zip.GZIPInputStream

/**
 * The board savestates (game state at the mode-select screen) that EVERY device loads locally — the core aborts
 * without them. They ship gzipped in assets/boards/ (~14MB) exactly like the desktop client bundles the committed
 * gzips, and are decompressed to filesDir once on first run. The ROM itself is NOT bundled: it's fetched from the
 * host over the relay (thin-client), so the app only ever asks for a name + room code.
 */
object Boards {
    private val NAMES = listOf(
        "board_live2_p1.bin", "board_live2_p2.bin",
        "board_live3_p1.bin", "board_live3_p2.bin", "board_live3_p3.bin",
        "board_live4_p1.bin", "board_live4_p2.bin", "board_live4_p3.bin", "board_live4_p4.bin",
    )

    /** True if all boards are present (already unpacked). */
    fun present(dir: File): Boolean = NAMES.all { File(dir, it).length() > 0 }

    /** Materialize any missing boards from assets to [dir]. Idempotent; returns true if all are present afterward.
     *  The Android Gradle Plugin auto-decompresses .gz assets and repackages them under the name WITHOUT .gz, so we
     *  open "boards/<name>" directly; a "boards/<name>.gz" + GZIP fallback keeps it working if that ever changes. */
    fun ensure(ctx: Context, dir: File): Boolean {
        dir.mkdirs()
        for (name in NAMES) {
            val out = File(dir, name)
            if (out.length() > 0) continue
            try {
                // preferred: AGP already ungzipped it -> a plain "boards/<name>" asset.
                runCatching {
                    ctx.assets.open("boards/$name").use { input -> out.outputStream().use { o -> input.copyTo(o) } }
                }.getOrElse {
                    // fallback: a still-gzipped "boards/<name>.gz" asset.
                    ctx.assets.open("boards/$name.gz").use { gz ->
                        GZIPInputStream(gz).use { input -> out.outputStream().use { o -> input.copyTo(o) } }
                    }
                }
                android.util.Log.i("MPDS-life", "unpacked $name (${out.length()} bytes)")
            } catch (e: Exception) {
                android.util.Log.w("MPDS-life", "board unpack failed for $name: ${e.message}")
                return false
            }
        }
        return present(dir)
    }
}
