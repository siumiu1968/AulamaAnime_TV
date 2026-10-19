package com.jing.sakura.compose.common

import com.jing.sakura.home.HeroPreviewSpec
import com.jing.sakura.home.HeroPreviewState
import com.jing.sakura.player.NavigateToPlayerArg
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviewPreparationStateTest {
    @Test fun loadingAppearsOnlyForFocusedAnimeAndSource() {
        val loading = HeroPreviewState.Loading("source:anime")
        assertTrue(pending(loading))
        assertFalse(pending(loading, animeId = "another"))
        assertFalse(pending(loading, sourceId = "another"))
        assertFalse(pending(loading, animeId = null))
    }

    @Test fun readyWaitsOnlyUntilCurrentFirstFrame() {
        val ready = HeroPreviewState.Ready(
            HeroPreviewSpec(
                key = "source:anime:episode:1",
                url = "https://example.com/preview.mp4",
                headers = emptyMap(),
                startPositionMs = 0,
                posterUrl = "",
                navigateToPlayerArg = NavigateToPlayerArg("Anime", "anime", "episode", 0, emptyList(), "source")
            )
        )
        assertTrue(pending(ready))
        assertFalse(pending(ready, firstFrameReady = true))
        assertFalse(pending(ready, animeId = "another"))
        assertFalse(pending(ready, sourceId = "another"))
    }

    @Test fun failureAndCancellationDoNotLeaveLoadingDots() {
        assertFalse(pending(HeroPreviewState.Idle))
        assertFalse(pending(HeroPreviewState.Error("source:anime", "unavailable")))
    }

    private fun pending(
        state: HeroPreviewState,
        animeId: String? = "anime",
        sourceId: String? = "source",
        firstFrameReady: Boolean = false
    ) = isPreviewPreparationPending(state, animeId, sourceId, firstFrameReady)
}
