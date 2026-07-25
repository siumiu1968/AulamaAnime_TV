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
    fun naturalIntroEntryRequestsInitialFocusWithoutCountdown() {
        val decision = PlaybackSkipUiStateMachine().update(intro)

        assertTrue(decision.isVisible)
        assertTrue(decision.shouldRequestInitialFocus)
        assertFalse(decision.shouldStartCountdown)
    }

    @Test
    fun naturalOutroEntryFocusesNextAndStartsEightSecondCountdown() {
        val decision = PlaybackSkipUiStateMachine().update(outro)

        assertTrue(decision.isVisible)
        assertTrue(decision.shouldRequestInitialFocus)
        assertTrue(decision.shouldStartCountdown)
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
}
