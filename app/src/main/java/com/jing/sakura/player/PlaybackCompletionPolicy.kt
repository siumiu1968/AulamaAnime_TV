package com.jing.sakura.player

internal object PlaybackCompletionPolicy {
    private const val RESUME_END_GUARD_MS = 10_000L

    fun resumePosition(lastPositionMs: Long, durationMs: Long): Long =
        lastPositionMs.takeIf { position ->
            position > 0L &&
                (durationMs <= 0L || durationMs - position >= RESUME_END_GUARD_MS)
        } ?: 0L

    fun completedPosition(durationMs: Long): Long =
        durationMs.takeIf { it > 0L } ?: 0L

    fun mappedCloudPosition(
        currentTimeMs: Long,
        durationMs: Long,
        completed: Boolean
    ): Long = if (completed) completedPosition(durationMs) else currentTimeMs.coerceAtLeast(0L)
}
