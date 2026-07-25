package com.jing.sakura.player

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackCenterKeyControllerTest {
    @Test
    fun visibleTransportControlsReceiveCenterKeyInsteadOfGlobalPlayback() {
        assertEquals(
            CenterKeyRoute.FOCUSED_CONTROL,
            PlaybackCenterKeyRoutingPolicy.route(
                controlsOverlayVisible = true
            )
        )
    }

    @Test
    fun hiddenControlsUseGlobalPlaybackShortcut() {
        assertEquals(
            CenterKeyRoute.GLOBAL_PLAYBACK,
            PlaybackCenterKeyRoutingPolicy.route(
                controlsOverlayVisible = false
            )
        )
    }

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

    @Test
    fun repeatedKeyEventsStartBoostOnlyOnceAndReleaseNeverClicks() {
        val controller = PlaybackCenterKeyController(longPressThresholdMs = 500L)

        assertEquals(CenterKeyAction.NONE, controller.onKeyDown(1_000L, 0))
        assertEquals(CenterKeyAction.START_BOOST, controller.onKeyDown(1_500L, 1))
        assertEquals(CenterKeyAction.NONE, controller.onKeyDown(1_620L, 2))
        assertEquals(CenterKeyAction.STOP_BOOST, controller.onKeyUp())
        assertEquals(CenterKeyAction.NONE, controller.onKeyUp())
    }
}
