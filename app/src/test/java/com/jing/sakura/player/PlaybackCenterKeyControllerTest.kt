package com.jing.sakura.player

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackCenterKeyControllerTest {
    @Test
    fun shortPressTogglesPlaybackWithoutStartingBoost() {
        val controller = PlaybackCenterKeyController(longPressThresholdMs = 500L)

        assertEquals(CenterKeyAction.NONE, controller.onKeyDown(1_000L, 0))
        assertEquals(CenterKeyAction.SHORT_PRESS, controller.onKeyUp())
    }

    @Test
    fun holdStartsTemporaryBoostAndReleaseStopsIt() {
        val controller = PlaybackCenterKeyController(longPressThresholdMs = 500L)

        controller.onKeyDown(1_000L, 0)
        assertEquals(CenterKeyAction.START_BOOST, controller.onLongPressTimeout(1_500L))
        assertEquals(CenterKeyAction.STOP_BOOST, controller.onKeyUp())
    }

    @Test
    fun cancellingAStartedHoldRestoresNormalSpeed() {
        val controller = PlaybackCenterKeyController(longPressThresholdMs = 500L)

        controller.onKeyDown(1_000L, 0)
        controller.onLongPressTimeout(1_500L)

        assertEquals(CenterKeyAction.STOP_BOOST, controller.cancel())
    }
}
