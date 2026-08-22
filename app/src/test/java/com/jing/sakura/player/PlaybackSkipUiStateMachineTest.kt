package com.jing.sakura.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackSkipUiStateMachineTest {
    private val intro = ActivePlaybackSkip(
        type = ActivePlaybackSkip.Type.INTRO,
        targetMs = 90_000L,
        advancesEpisode = false
    )
    private val outro = ActivePlaybackSkip(
        type = ActivePlaybackSkip.Type.OUTRO,
        targetMs = 90_000L,
        advancesEpisode = true
    )

    @Test
    fun naturalIntroEntryShowsForFiveSecondsWithoutTakingTransportFocus() {
        val decision = PlaybackSkipUiStateMachine().update(intro)

        assertTrue(decision.isVisible)
        assertFalse(decision.shouldRequestInitialFocus)
        assertFalse(decision.shouldStartCountdown)
        assertTrue(decision.shouldScheduleAutoHide)
        assertEquals(5_000L, PlaybackSkipUiStateMachine.TRANSIENT_SKIP_VISIBLE_MS)
    }

    @Test
    fun naturalOutroEntryFocusesNextAndStartsEightSecondCountdown() {
        val decision = PlaybackSkipUiStateMachine().update(outro)

        assertTrue(decision.isVisible)
        assertTrue(decision.shouldRequestInitialFocus)
        assertTrue(decision.shouldStartCountdown)
        assertFalse(decision.shouldScheduleAutoHide)
        assertEquals(8_000L, PlaybackSkipUiStateMachine.AUTO_NEXT_COUNTDOWN_MS)
    }

    @Test
    fun seekIntoOutroShowsActionsWithoutFocusOrCountdown() {
        val state = PlaybackSkipUiStateMachine()
        state.onSeek()

        val decision = state.update(outro)

        assertTrue(decision.isVisible)
        assertFalse(decision.shouldRequestInitialFocus)
        assertFalse(decision.shouldStartCountdown)
        assertFalse(state.update(outro).shouldStartCountdown)
    }

    @Test
    fun seekIntoIntroShowsActionWithoutTakingTransportFocus() {
        val state = PlaybackSkipUiStateMachine()
        state.onSeek()

        val decision = state.update(intro)

        assertTrue(decision.isVisible)
        assertFalse(decision.shouldRequestInitialFocus)
        assertFalse(decision.shouldStartCountdown)
    }

    @Test
    fun leavingSeekedOutroAllowsNaturalReentryToFocusAndCountdown() {
        val state = PlaybackSkipUiStateMachine()
        state.onSeek()
        state.update(outro)
        state.update(null)

        val decision = state.update(outro)

        assertTrue(decision.shouldRequestInitialFocus)
        assertTrue(decision.shouldStartCountdown)
    }

    @Test
    fun remoteInteractionCancelsOnlyCurrentCountdown() {
        val state = PlaybackSkipUiStateMachine()
        state.update(outro)

        assertTrue(state.onRemoteInteraction())
        assertFalse(state.update(outro).shouldStartCountdown)
    }

    @Test
    fun continuePlaybackHidesChoiceUntilSeekReentersOutro() {
        val state = PlaybackSkipUiStateMachine()
        state.update(outro)
        state.onContinuePlayback()

        assertFalse(state.update(outro).isVisible)

        state.onSeek()
        val decision = state.update(outro)
        assertTrue(decision.isVisible)
        assertFalse(decision.shouldRequestInitialFocus)
        assertFalse(decision.shouldStartCountdown)
    }

    @Test
    fun introTransitionsFromVisibleToHiddenToRevealedBeforeControlsShow() {
        val state = PlaybackSkipUiStateMachine()
        state.update(intro)

        assertEquals(PlaybackSkipPromptState.VISIBLE, state.promptState())
        assertTrue(state.onTransientActionTimeout())
        assertEquals(PlaybackSkipPromptState.HIDDEN, state.promptState())
        assertFalse(state.update(intro).isVisible)
        assertTrue(state.revealTransientAction())
        assertEquals(PlaybackSkipPromptState.REVEALED, state.promptState())
        assertTrue(state.update(intro).isVisible)

        state.onPlayerControlsShown()

        assertEquals(PlaybackSkipPromptState.VISIBLE, state.promptState())
    }

    @Test
    fun nextEpisodeChoiceNeverUsesTransientAutoHide() {
        val state = PlaybackSkipUiStateMachine()
        state.update(outro)

        assertEquals(PlaybackSkipPromptState.INACTIVE, state.promptState())
        assertFalse(state.onTransientActionTimeout())
        assertFalse(state.revealTransientAction())
        assertTrue(state.update(outro).isVisible)
    }

    @Test
    fun hiddenPromptConsumesFirstDirectionToRevealAndSecondDirectionShowsControls() {
        val controller = PlaybackSkipPromptKeyController()

        assertEquals(
            PlaybackSkipPromptKeyAction.REVEAL_PROMPT,
            controller.onKeyDown(
                keyId = DIRECTION_KEY,
                keyKind = PlaybackSkipPromptKeyKind.DIRECTION,
                promptState = PlaybackSkipPromptState.HIDDEN,
                playerControlsVisible = false,
                repeatCount = 0
            )
        )
        assertEquals(
            PlaybackSkipPromptKeyAction.CONSUME,
            controller.onKeyUp(DIRECTION_KEY)
        )
        assertEquals(
            PlaybackSkipPromptKeyAction.SHOW_PLAYER_CONTROLS,
            controller.onKeyDown(
                keyId = DIRECTION_KEY,
                keyKind = PlaybackSkipPromptKeyKind.DIRECTION,
                promptState = PlaybackSkipPromptState.REVEALED,
                playerControlsVisible = false,
                repeatCount = 0
            )
        )
        assertEquals(
            PlaybackSkipPromptKeyAction.CONSUME,
            controller.onKeyUp(DIRECTION_KEY)
        )
    }

    @Test
    fun hiddenPromptConfirmRevealsOnDownAndSkipsOnUp() {
        val controller = PlaybackSkipPromptKeyController()

        assertEquals(
            PlaybackSkipPromptKeyAction.REVEAL_PROMPT,
            controller.onKeyDown(
                keyId = CONFIRM_KEY,
                keyKind = PlaybackSkipPromptKeyKind.CONFIRM,
                promptState = PlaybackSkipPromptState.HIDDEN,
                playerControlsVisible = false,
                repeatCount = 0
            )
        )
        assertEquals(
            PlaybackSkipPromptKeyAction.ACTIVATE_SKIP,
            controller.onKeyUp(CONFIRM_KEY)
        )
    }

    @Test
    fun visibleTransientPromptConfirmSkipsWithoutOpeningPlayerControls() {
        val controller = PlaybackSkipPromptKeyController()

        assertEquals(
            PlaybackSkipPromptKeyAction.CONSUME,
            controller.onKeyDown(
                keyId = CONFIRM_KEY,
                keyKind = PlaybackSkipPromptKeyKind.CONFIRM,
                promptState = PlaybackSkipPromptState.VISIBLE,
                playerControlsVisible = false,
                repeatCount = 0
            )
        )
        assertEquals(
            PlaybackSkipPromptKeyAction.ACTIVATE_SKIP,
            controller.onKeyUp(CONFIRM_KEY)
        )
    }

    @Test
    fun nextEpisodeChoiceKeepsExistingFocusAndKeyRouting() {
        val controller = PlaybackSkipPromptKeyController()

        assertEquals(
            PlaybackSkipPromptKeyAction.PASS_THROUGH,
            controller.onKeyDown(
                keyId = CONFIRM_KEY,
                keyKind = PlaybackSkipPromptKeyKind.CONFIRM,
                promptState = PlaybackSkipPromptState.INACTIVE,
                playerControlsVisible = false,
                repeatCount = 0
            )
        )
        assertEquals(
            PlaybackSkipPromptKeyAction.PASS_THROUGH,
            controller.onKeyUp(CONFIRM_KEY)
        )
    }

    companion object {
        private const val DIRECTION_KEY = 1
        private const val CONFIRM_KEY = 2
    }
}
