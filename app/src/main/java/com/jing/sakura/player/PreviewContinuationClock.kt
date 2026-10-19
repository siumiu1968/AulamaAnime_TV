package com.jing.sakura.player

/** Counts time actually playing, rather than buffering or waiting for focus dwell. */
internal class PreviewContinuationClock(private val thresholdMs: Long = 60_000L) {
    private var accumulatedMs = 0L
    private var playingSinceMs: Long? = null
    private var continued = false

    fun update(nowMs: Long, playing: Boolean): Boolean {
        val previous = playingSinceMs
        if (previous != null) accumulatedMs += (nowMs - previous).coerceAtLeast(0L)
        playingSinceMs = nowMs.takeIf { playing && !continued }
        if (continued || !playing || accumulatedMs < thresholdMs) return false
        continued = true
        playingSinceMs = null
        return true
    }

    fun reset() {
        accumulatedMs = 0L
        playingSinceMs = null
        continued = false
    }
}
