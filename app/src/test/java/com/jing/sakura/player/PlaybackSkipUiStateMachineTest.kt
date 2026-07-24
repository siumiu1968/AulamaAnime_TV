package com.jing.sakura.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackSkipUiStateMachineTest {
    private val outro = ActivePlaybackSkip(
        type = ActivePlaybackSkip.Type.OUTRO,
        targetMs = 90_000L,
        advancesEpisode = true
    )

    @Test
    fun enteringOutroOnlyShowsChoiceAndStartsEightSecondCountdown() {
        val decision = PlaybackSkipUiStateMachine().update(outro)

        assertTrue(decision.isVisible)
        assertTrue(decision.shouldStartCountdown)
        assertEquals(8_000L, PlaybackSkipUiStateMachine.AUTO_NEXT_COUNTDOWN_MS)
    }

    @Test
    fun remoteInteractionCancelsOnlyCurrentCountdown() {
        val state = PlaybackSkipUiStateMachine()
        state.update(outro)

        assertTrue(state.onRemoteInteraction())
        assertFalse(state.update(outro).shouldStartCountdown)

        state.onSeek()
        assertTrue(state.update(outro).shouldStartCountdown)
    }

    @Test
    fun continuePlaybackHidesChoiceUntilSeekReentersOutro() {
        val state = PlaybackSkipUiStateMachine()
        state.update(outro)
        state.onContinuePlayback()

        assertFalse(state.update(outro).isVisible)

        state.onSeek()
        assertTrue(state.update(outro).isVisible)
    }
}
