package com.jing.sakura.player

internal data class PlaybackSkipUiDecision(
    val active: ActivePlaybackSkip?,
    val isVisible: Boolean,
    val shouldStartCountdown: Boolean,
    val shouldRequestInitialFocus: Boolean
)

/** Keeps skip actions predictable while progress seeking and D-pad input are in flight. */
internal class PlaybackSkipUiStateMachine {
    private var current: ActivePlaybackSkip? = null
    private var countdownCancelled = false
    private var continuePlaybackChosen = false
    private var nextEntryComesFromSeek = false
    private var currentEnteredThroughSeek = false

    fun update(next: ActivePlaybackSkip?): PlaybackSkipUiDecision {
        if (next == null) {
            resetSegment()
            nextEntryComesFromSeek = false
            return PlaybackSkipUiDecision(null, false, false, false)
        }

        val changed = current != next
        if (changed) {
            current = next
            currentEnteredThroughSeek = nextEntryComesFromSeek
            nextEntryComesFromSeek = false
            countdownCancelled = currentEnteredThroughSeek
            continuePlaybackChosen = false
        }
        val visible = !continuePlaybackChosen
        return PlaybackSkipUiDecision(
            active = next,
            isVisible = visible,
            shouldStartCountdown = changed && visible && next.advancesEpisode && !countdownCancelled
                && !currentEnteredThroughSeek,
            shouldRequestInitialFocus = changed && visible && !currentEnteredThroughSeek
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

    fun onSeek() {
        resetSegment()
        nextEntryComesFromSeek = true
    }

    fun reset() {
        resetSegment()
        nextEntryComesFromSeek = false
    }

    private fun resetSegment() {
        current = null
        countdownCancelled = false
        continuePlaybackChosen = false
        currentEnteredThroughSeek = false
    }

    companion object {
        const val AUTO_NEXT_COUNTDOWN_MS = 8_000L
    }
}
