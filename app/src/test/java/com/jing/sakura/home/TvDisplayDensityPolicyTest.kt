package com.jing.sakura.home

import org.junit.Assert.assertEquals
import org.junit.Test

class TvDisplayDensityPolicyTest {
    @Test
    fun normalizesFullHdTvToReferenceViewport() {
        assertEquals(
            2f,
            TvDisplayDensityPolicy.effectiveDensity(
                systemDensity = 1.5f,
                displayWidthPx = 1_920,
                displayHeightPx = 1_080
            )
        )
    }

    @Test
    fun ignoresManufacturerDensityForTheSameWindowSize() {
        assertEquals(2f, TvDisplayDensityPolicy.effectiveDensity(2f, 1_920, 1_080))
        assertEquals(2f, TvDisplayDensityPolicy.effectiveDensity(2.5f, 1_920, 1_080))
    }

    @Test
    fun normalizesHdAndRaw4kWindowsToTheSameViewport() {
        assertEquals(
            4f / 3f,
            TvDisplayDensityPolicy.effectiveDensity(1f, 1_280, 720)
        )
        assertEquals(4f, TvDisplayDensityPolicy.effectiveDensity(2f, 3_840, 2_160))
    }

    @Test
    fun fitsReferenceViewportInsideWiderAspectRatio() {
        assertEquals(2f, TvDisplayDensityPolicy.effectiveDensity(2f, 1_920, 1_200))
    }

    @Test
    fun fallsBackToSystemDensityBeforeWindowMetricsAreAvailable() {
        assertEquals(1.5f, TvDisplayDensityPolicy.effectiveDensity(1.5f, 0, 0))
    }
}
