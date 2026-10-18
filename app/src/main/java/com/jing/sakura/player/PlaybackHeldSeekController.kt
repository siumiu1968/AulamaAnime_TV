package com.jing.sakura.player

/** A held direction changes Leanback's next step; Leanback remains the only seek owner. */
internal class PlaybackHeldSeekController {
    private var direction: Int? = null
    private var downTimeMs = 0L
    private var startedAtMs = 0L
    private var acceptedAtMs = 0L

    fun onKeyDown(direction: Int, downTimeMs: Long, eventTimeMs: Long, repeatCount: Int,
        durationMs: Long): Long? {
        if (this.direction != direction || this.downTimeMs != downTimeMs || repeatCount == 0 ||
            eventTimeMs < acceptedAtMs) {
            this.direction = direction
            this.downTimeMs = downTimeMs
            // A repeat received after a focus/reset boundary starts a fresh hold.
            startedAtMs = if (repeatCount == 0) downTimeMs.coerceAtMost(eventTimeMs) else eventTimeMs
            acceptedAtMs = eventTimeMs
            return PlaybackSeekPolicy.incrementMs(durationMs)
        }
        if (eventTimeMs - acceptedAtMs < REPEAT_INTERVAL_MS) return null
        acceptedAtMs = eventTimeMs
        return PlaybackSeekPolicy.heldIncrementMs(durationMs, (eventTimeMs - startedAtMs).coerceAtLeast(0L))
    }

    fun reset() { direction = null }

    companion object { const val REPEAT_INTERVAL_MS = 180L }
}
