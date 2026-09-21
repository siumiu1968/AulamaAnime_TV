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
        assertEquals(CenterKeyAction.SHORT_PRESS, controller.onKeyUp(1_700L))
    }

    @Test
    fun holdStartsTemporaryBoostAndReleaseStopsIt() {
        val controller = PlaybackCenterKeyController(longPressThresholdMs = 500L)

        controller.onKeyDown(1_000L, 0)
        assertEquals(CenterKeyAction.START_BOOST, controller.onLongPressTimeout(1_500L))
        assertEquals(CenterKeyAction.NONE, controller.onKeyUp(1_700L))
        assertEquals(CenterKeyAction.STOP_BOOST, controller.onReleaseTimeout(1_920L))
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
        assertEquals(CenterKeyAction.NONE, controller.onKeyUp(1_700L))
        assertEquals(CenterKeyAction.STOP_BOOST, controller.onReleaseTimeout(1_920L))
        assertEquals(CenterKeyAction.NONE, controller.onKeyUp(1_700L))
    }

    @Test fun threeSecondHoldLocksUntilNextClick() {
        val c = PlaybackCenterKeyController()
        c.onKeyDown(0, 0)
        assertEquals(CenterKeyAction.START_BOOST, c.onLongPressTimeout(500))
        assertEquals(CenterKeyAction.NONE, c.onLongPressTimeout(2999))
        assertEquals(CenterKeyAction.LOCK_BOOST, c.onLongPressTimeout(3000))
        assertEquals(CenterKeyAction.NONE, c.onKeyUp(3100))
        assertEquals(CenterKeyAction.NONE, c.onReleaseTimeout(4000))
        c.onKeyDown(4500, 0)
        assertEquals(CenterKeyAction.STOP_BOOST, c.onKeyUp(4600))
    }
    @Test fun interruptedRemoteRepeatKeepsCharge() {
        val c = PlaybackCenterKeyController()
        c.onKeyDown(0, 0)
        c.onLongPressTimeout(500)
        c.onKeyUp(2500)
        assertEquals(CenterKeyAction.NONE, c.onReleaseTimeout(2650))
        assertEquals(CenterKeyAction.NONE, c.onKeyDown(2660, 0))
        assertEquals(CenterKeyAction.LOCK_BOOST, c.onLongPressTimeout(3000))
    }
    @Test fun releaseBeforeThresholdCannotLockDuringGrace() {
        val c = PlaybackCenterKeyController()
        c.onKeyDown(0, 0)
        c.onLongPressTimeout(500)
        c.onKeyUp(2900)
        assertEquals(CenterKeyAction.NONE, c.onLongPressTimeout(3000))
        assertEquals(CenterKeyAction.STOP_BOOST, c.onReleaseTimeout(3120))
    }
}
