package com.jing.sakura.player

internal data class PlaybackSkipUiDecision(
    val active: ActivePlaybackSkip?,
    val isVisible: Boolean,
    val shouldStartCountdown: Boolean,
    val shouldRequestInitialFocus: Boolean,
    val shouldScheduleAutoHide: Boolean
)

internal enum class PlaybackSkipPromptState {
    INACTIVE,
    VISIBLE,
    HIDDEN,
    REVEALED
}

internal enum class PlaybackSkipPromptKeyKind {
    DIRECTION,
    CONFIRM
}

internal enum class PlaybackSkipPromptKeyAction {
    PASS_THROUGH,
    CONSUME,
    REVEAL_PROMPT,
    SHOW_PLAYER_CONTROLS,
    ACTIVATE_SKIP
}

/** Routes the staged prompt wake-up without depending on Android key classes. */
internal class PlaybackSkipPromptKeyController {
    private var capturedKeyId: Int? = null
    private var capturedKeyUpAction = PlaybackSkipPromptKeyAction.CONSUME

    fun onKeyDown(
        keyId: Int,
        keyKind: PlaybackSkipPromptKeyKind,
        promptState: PlaybackSkipPromptState,
        playerControlsVisible: Boolean,
        repeatCount: Int
    ): PlaybackSkipPromptKeyAction {
        if (capturedKeyId != null) return PlaybackSkipPromptKeyAction.CONSUME
        if (repeatCount > 0) {
            return PlaybackSkipPromptKeyAction.PASS_THROUGH
        }
        return when {
            promptState == PlaybackSkipPromptState.HIDDEN -> {
                capture(
                    keyId,
                    if (keyKind == PlaybackSkipPromptKeyKind.CONFIRM) {
                        PlaybackSkipPromptKeyAction.ACTIVATE_SKIP
                    } else {
                        PlaybackSkipPromptKeyAction.CONSUME
                    }
                )
                PlaybackSkipPromptKeyAction.REVEAL_PROMPT
            }
            promptState == PlaybackSkipPromptState.REVEALED &&
                keyKind == PlaybackSkipPromptKeyKind.DIRECTION -> {
                capture(keyId, PlaybackSkipPromptKeyAction.CONSUME)
                PlaybackSkipPromptKeyAction.SHOW_PLAYER_CONTROLS
            }
            (promptState == PlaybackSkipPromptState.VISIBLE ||
                promptState == PlaybackSkipPromptState.REVEALED) &&
                keyKind == PlaybackSkipPromptKeyKind.CONFIRM && !playerControlsVisible -> {
                capture(keyId, PlaybackSkipPromptKeyAction.ACTIVATE_SKIP)
                PlaybackSkipPromptKeyAction.CONSUME
            }
            else -> PlaybackSkipPromptKeyAction.PASS_THROUGH
        }
    }

    fun onKeyUp(keyId: Int): PlaybackSkipPromptKeyAction {
        if (capturedKeyId != keyId) return PlaybackSkipPromptKeyAction.PASS_THROUGH
        val action = capturedKeyUpAction
        capturedKeyId = null
        capturedKeyUpAction = PlaybackSkipPromptKeyAction.CONSUME
        return action
    }

    fun reset() {
        capturedKeyId = null
        capturedKeyUpAction = PlaybackSkipPromptKeyAction.CONSUME
    }

    private fun capture(keyId: Int, keyUpAction: PlaybackSkipPromptKeyAction) {
        capturedKeyId = keyId
        capturedKeyUpAction = keyUpAction
    }
}

/** Keeps skip actions predictable while progress seeking and D-pad input are in flight. */
internal class PlaybackSkipUiStateMachine {
    private var current: ActivePlaybackSkip? = null
    private var countdownCancelled = false
    private var continuePlaybackChosen = false
    private var nextEntryComesFromSeek = false
    private var currentEnteredThroughSeek = false
    private var transientActionHidden = false
    private var transientActionRevealed = false

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
            transientActionRevealed = false
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
        transientActionRevealed = false
        return true
    }

    fun revealTransientAction(): Boolean {
        if (current == null || current?.advancesEpisode == true || !transientActionHidden) return false
        transientActionHidden = false
        transientActionRevealed = true
        return true
    }

    fun promptState(): PlaybackSkipPromptState = when {
        current == null || current?.advancesEpisode == true || continuePlaybackChosen -> {
            PlaybackSkipPromptState.INACTIVE
        }
        transientActionHidden -> PlaybackSkipPromptState.HIDDEN
        transientActionRevealed -> PlaybackSkipPromptState.REVEALED
        else -> PlaybackSkipPromptState.VISIBLE
    }

    fun onPlayerControlsShown() {
        transientActionRevealed = false
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
        transientActionRevealed = false
    }

    companion object {
        const val AUTO_NEXT_COUNTDOWN_MS = 8_000L
        const val TRANSIENT_SKIP_VISIBLE_MS = 5_000L
    }
}
