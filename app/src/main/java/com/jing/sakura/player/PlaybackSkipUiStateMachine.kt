package com.jing.sakura.player

internal data class PlaybackSkipUiDecision(
    val active: ActivePlaybackSkip?,
    val isVisible: Boolean,
    val shouldStartCountdown: Boolean,
    val shouldRequestInitialFocus: Boolean,
    val shouldScheduleAutoHide: Boolean
)

/** Keeps skip actions predictable while progress seeking and D-pad input are in flight. */
internal class PlaybackSkipUiStateMachine {
    private var current: ActivePlaybackSkip? = null
    private var countdownCancelled = false
    private var continuePlaybackChosen = false
    private var nextEntryComesFromSeek = false
    private var currentEnteredThroughSeek = false
    private var transientActionHidden = false

    fun update(next: ActivePlaybackSkip?): PlaybackSkipUiDecision {
        if (next == null) {
            resetSegment()
            nextEntryComesFromSeek = false
            return PlaybackSkipUiDecision(null, false, false, false, false)
        }

        val changed = current != next
        if (changed) {
            current = next
            currentEnteredThroughSeek = nextEntryComesFromSeek
            nextEntryComesFromSeek = false
            countdownCancelled = currentEnteredThroughSeek
            continuePlaybackChosen = false
            transientActionHidden = false
        }
        val visible = !continuePlaybackChosen && (next.advancesEpisode || !transientActionHidden)
        return PlaybackSkipUiDecision(
            active = next,
            isVisible = visible,
            shouldStartCountdown = changed && visible && next.advancesEpisode && !countdownCancelled
                && !currentEnteredThroughSeek,
            shouldRequestInitialFocus = changed && visible && !currentEnteredThroughSeek,
            shouldScheduleAutoHide = changed && visible && !next.advancesEpisode
        )
    }

    fun onTransientActionTimeout(): Boolean {
        if (current == null || current?.advancesEpisode == true || transientActionHidden) return false
        transientActionHidden = true
        return true
    }

    fun revealTransientAction(): Boolean {
        if (current == null || current?.advancesEpisode == true || !transientActionHidden) return false
        transientActionHidden = false
        return true
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
        transientActionHidden = false
    }

    companion object {
        const val AUTO_NEXT_COUNTDOWN_MS = 8_000L
        const val TRANSIENT_SKIP_VISIBLE_MS = 10_000L
    }
}
