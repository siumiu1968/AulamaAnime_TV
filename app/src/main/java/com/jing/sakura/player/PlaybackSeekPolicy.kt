package com.jing.sakura.player

internal object PlaybackSeekPolicy {
    fun incrementMs(durationMs: Long): Long {
        val standard = if (durationMs > 30 * 60_000L) 10_000L else 5_000L
        return if (durationMs > 0L) (durationMs / 100).coerceIn(100L, standard).coerceAtMost(durationMs)
            else standard
    }

    fun heldIncrementMs(durationMs: Long, heldMs: Long): Long {
        val base = incrementMs(durationMs)
        if (durationMs <= 0L) return base
        val multiplier = when {
            heldMs >= 6_000L -> 16
            heldMs >= 4_000L -> 8
            heldMs >= 2_000L -> 4
            heldMs >= 800L -> 2
            else -> 1
        }
        val maximum = (durationMs / 50).coerceIn(base, 60_000L)
        return (base * multiplier).coerceAtMost(maximum)
    }

    fun fraction(durationMs: Long, stepMs: Long = incrementMs(durationMs)): Float =
        if (durationMs > 0L) (stepMs.toDouble() / durationMs).toFloat().coerceIn(0f, 1f)
        else 0.01f
}
