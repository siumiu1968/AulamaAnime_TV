package com.jing.sakura.player

import com.jing.sakura.auth.PlaybackSegments

data class ActivePlaybackSkip(
    val type: Type,
    val targetMs: Long,
    val advancesEpisode: Boolean
) {
    enum class Type { INTRO, OUTRO }
}

object PlaybackSkipPolicy {
    fun shouldAutoAdvanceAtEnd(
        positionMs: Long,
        durationMs: Long,
        hasNextEpisode: Boolean
    ): Boolean = hasNextEpisode &&
        durationMs > 0L &&
        positionMs >= (durationMs - 1_000L).coerceAtLeast(0L)

    fun activeSkip(
        segments: PlaybackSegments?,
        positionMs: Long,
        hasNextEpisode: Boolean,
        durationMs: Long = 0L
    ): ActivePlaybackSkip? {
        if (segments == null || positionMs < 0L) return null
        val intro = activeRange(segments.introStartMs, segments.introEndMs, positionMs)
        if (intro != null) {
            return ActivePlaybackSkip(ActivePlaybackSkip.Type.INTRO, intro, advancesEpisode = false)
        }
        val outro = activeRange(segments.outroStartMs, segments.outroEndMs, positionMs)
            ?: return null
        return ActivePlaybackSkip(
            type = ActivePlaybackSkip.Type.OUTRO,
            targetMs = outro,
            advancesEpisode = hasNextEpisode && (
                segments.outroAction == "next" || isTerminalOutro(outro, durationMs)
            )
        )
    }

    private fun isTerminalOutro(outroEndMs: Long, durationMs: Long): Boolean =
        durationMs > 0L && kotlin.math.abs(durationMs - outroEndMs) <= TERMINAL_OUTRO_TOLERANCE_MS

    private fun activeRange(startMs: Long?, endMs: Long?, positionMs: Long): Long? {
        if (startMs == null || endMs == null || endMs <= startMs) return null
        return endMs.takeIf { positionMs in startMs until endMs }
    }

    private const val TERMINAL_OUTRO_TOLERANCE_MS = 1_500L
}
