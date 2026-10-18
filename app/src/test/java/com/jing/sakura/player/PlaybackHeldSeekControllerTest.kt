package com.jing.sakura.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackHeldSeekControllerTest {
    private val duration = 24 * 60_000L

    @Test fun repeatsAccelerateByHeldTimeAndFastRepeatsDoNotMultiplyNativeSeeks() {
        val controller = PlaybackHeldSeekController()
        assertEquals(5_000L, controller.onKeyDown(1, 100, 100, 0, duration))
        assertNull(controller.onKeyDown(1, 100, 150, 1, duration))
        assertEquals(5_000L, controller.onKeyDown(1, 100, 300, 2, duration))
        assertEquals(10_000L, controller.onKeyDown(1, 100, 900, 3, duration))
        assertEquals(20_000L, controller.onKeyDown(1, 100, 2100, 4, duration))
        assertEquals(28_800L, controller.onKeyDown(1, 100, 6100, 5, duration))
    }

    @Test fun releaseDirectionChangeNewPressAndFocusResetAllStartWithSmallStep() {
        val controller = PlaybackHeldSeekController()
        controller.onKeyDown(1, 100, 100, 0, duration)
        assertEquals(28_800L, controller.onKeyDown(1, 100, 6100, 5, duration))
        assertEquals(5_000L, controller.onKeyDown(-1, 100, 6300, 6, duration))
        controller.reset()
        assertEquals(5_000L, controller.onKeyDown(-1, 100, 10_000, 20, duration))
        assertEquals(5_000L, controller.onKeyDown(-1, 10_500, 10_500, 0, duration))
        assertEquals(5_000L, controller.onKeyDown(1, 11_000, 11_000, 0, duration))
    }
}
