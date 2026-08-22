package com.jing.sakura.compose.common

import org.junit.Assert.assertEquals
import org.junit.Test

class CinematicArtworkBackdropTest {
    @Test
    fun usesEachArtworkIntrinsicAspectRatio() {
        assertEquals(2f / 3f, cinematicArtworkAspectRatio(1_000, 1_500), 0.001f)
        assertEquals(4f / 5f, cinematicArtworkAspectRatio(1_200, 1_500), 0.001f)
    }

    @Test
    fun keepsInvalidMetadataOnSafePortraitFallback() {
        assertEquals(2f / 3f, cinematicArtworkAspectRatio(0, 0), 0.001f)
    }

    @Test
    fun capsExtremelyWideArtworkAtTelevisionRatio() {
        assertEquals(16f / 9f, cinematicArtworkAspectRatio(4_000, 1_000), 0.001f)
    }
}
