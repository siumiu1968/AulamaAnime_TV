package com.jing.sakura.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackControlsAutoHidePolicyTest {
    @Test
    fun playingVideoHidesVisibleControlsAfterFourIdleSeconds() {
        assertTrue(
            PlaybackControlsAutoHidePolicy.shouldHide(
                controlsVisible = true,
                isPlaying = true
            )
        )
        assertTrue(PlaybackControlsAutoHidePolicy.IDLE_TIMEOUT_MS == 4_000L)
    }

    @Test
    fun pausedVideoKeepsControlsVisible() {
        assertFalse(
            PlaybackControlsAutoHidePolicy.shouldHide(
                controlsVisible = true,
                isPlaying = false
            )
        )
    }

    @Test
    fun hiddenControlsDoNotTriggerAnotherHide() {
        assertFalse(
            PlaybackControlsAutoHidePolicy.shouldHide(
                controlsVisible = false,
                isPlaying = true
            )
        )
    }
}
