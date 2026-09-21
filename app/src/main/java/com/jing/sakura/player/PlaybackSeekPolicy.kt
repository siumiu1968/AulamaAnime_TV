package com.jing.sakura.player

internal object PlaybackSeekPolicy {
    fun incrementMs(durationMs: Long): Long = if (durationMs > 30 * 60_000L) 10_000L else 5_000L

    fun fraction(durationMs: Long): Float =
        if (durationMs > 0L) (incrementMs(durationMs).toDouble() / durationMs).toFloat().coerceAtMost(1f)
        else 0.01f
}
