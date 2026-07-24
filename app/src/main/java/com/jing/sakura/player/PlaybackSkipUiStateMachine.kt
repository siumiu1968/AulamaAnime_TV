package com.jing.sakura.player

internal data class PlaybackSkipUiDecision(
    val active: ActivePlaybackSkip?,
    val isVisible: Boolean,
    val shouldStartCountdown: Boolean
)

/** Keeps skip actions predictable while progress seeking and D-pad input are in flight. */
internal class PlaybackSkipUiStateMachine {
    private var current: ActivePlaybackSkip? = null
    private var countdownCancelled = false
    private var continuePlaybackChosen = false

    fun update(next: ActivePlaybackSkip?): PlaybackSkipUiDecision {
        if (next == null) {
            reset()
            return PlaybackSkipUiDecision(null, false, false)
        }

        val changed = current != next
        if (changed) {
            current = next
            countdownCancelled = false
            continuePlaybackChosen = false
        }
        val visible = !continuePlaybackChosen
        return PlaybackSkipUiDecision(
            active = next,
            isVisible = visible,
            shouldStartCountdown = changed && visible && next.advancesEpisode && !countdownCancelled
        )
    }

    fun onRemoteInteraction(): Boolean {
        if (current?.advancesEpisode != true || countdownCancelled) return false
        countdownCancelled = true
        return true
    }

    fun onContinuePlayback() {
        if (current?.advancesEpisode != true) return
        countdownCancelled = true
        continuePlaybackChosen = true
    }

    fun onSeek() = reset()

    fun reset() {
        current = null
        countdownCancelled = false
        continuePlaybackChosen = false
    }

    companion object {
        const val AUTO_NEXT_COUNTDOWN_MS = 8_000L
    }
}
