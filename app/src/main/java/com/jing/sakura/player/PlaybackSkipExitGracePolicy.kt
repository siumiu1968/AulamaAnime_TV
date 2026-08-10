package com.jing.sakura.player

/** Prevents a single inaccurate player position tick from dismissing skip actions. */
internal class PlaybackSkipExitGracePolicy(
    private val graceMs: Long = EXIT_GRACE_MS
) {
    private var pendingExitAtMs: Long? = null

    fun onSegmentActive() {
        pendingExitAtMs = null
    }

    /** Returns the remaining delay before an inactive segment may be dismissed. */
    fun scheduleExit(nowMs: Long): Long {
        val exitAtMs = pendingExitAtMs ?: (nowMs + graceMs).also { pendingExitAtMs = it }
        return (exitAtMs - nowMs).coerceAtLeast(0L)
    }

    fun shouldCommitExit(nowMs: Long): Boolean = pendingExitAtMs?.let { nowMs >= it } == true

    fun clear() {
        pendingExitAtMs = null
    }

    companion object {
        const val EXIT_GRACE_MS = 600L
    }
}
