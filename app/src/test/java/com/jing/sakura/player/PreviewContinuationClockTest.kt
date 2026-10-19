package com.jing.sakura.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviewContinuationClockTest {
    @Test fun requiresSixtySecondsActuallyPlaying() {
        val clock = PreviewContinuationClock()
        assertFalse(clock.update(2_000, true))
        assertFalse(clock.update(61_999, true))
        assertTrue(clock.update(62_000, true))
    }

    @Test fun bufferingAndPausedTimeDoNotCount() {
        val clock = PreviewContinuationClock()
        assertFalse(clock.update(0, false))
        assertFalse(clock.update(30_000, true))
        assertFalse(clock.update(50_000, false))
        assertFalse(clock.update(110_000, false))
        assertFalse(clock.update(130_000, true))
        assertFalse(clock.update(169_999, true))
        assertTrue(clock.update(170_000, true))
    }

    @Test fun losingFocusStartsNewSessionAtZero() {
        val clock = PreviewContinuationClock()
        clock.update(0, true)
        assertFalse(clock.update(59_000, true))
        clock.reset()
        assertFalse(clock.update(60_000, true))
        assertFalse(clock.update(119_999, true))
        assertTrue(clock.update(120_000, true))
    }

    @Test fun continuationIsDispatchedOnlyOnce() {
        val clock = PreviewContinuationClock()
        clock.update(0, true)
        assertTrue(clock.update(60_000, true))
        assertFalse(clock.update(60_001, true))
        assertFalse(clock.update(90_000, false))
        assertFalse(clock.update(180_000, true))
    }

    @Test fun pausingAtDeadlineDoesNotNavigateAway() {
        val clock = PreviewContinuationClock()
        clock.update(0, true)
        assertFalse(clock.update(60_000, false))
        assertFalse(clock.update(120_000, false))
        assertTrue(clock.update(120_001, true))
    }
}
