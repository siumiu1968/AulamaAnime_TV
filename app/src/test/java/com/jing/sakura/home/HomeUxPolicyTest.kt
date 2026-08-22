package com.jing.sakura.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import com.jing.sakura.data.AnimeData

class HomeUxPolicyTest {
    @Test
    fun blocksOnlyWhileTheFirstRenderableHomePayloadIsMissing() {
        assertTrue(shouldBlockForInitialHomeLoad(isLoading = true, hasDisplayData = false))
        assertFalse(shouldBlockForInitialHomeLoad(isLoading = true, hasDisplayData = true))
        assertFalse(shouldBlockForInitialHomeLoad(isLoading = false, hasDisplayData = false))
    }

    @Test
    fun firstHomePayloadAppearsWithoutAnInvisibleEntranceFrame() {
        assertTrue(shouldSkipHomeContentEntrance(reducedMotion = false, contentEntranceVersion = 0))
        assertTrue(shouldSkipHomeContentEntrance(reducedMotion = true, contentEntranceVersion = 1))
        assertFalse(shouldSkipHomeContentEntrance(reducedMotion = false, contentEntranceVersion = 1))
    }

    @Test
    fun publishesOnlyTheLatestHomeRequestForTheCurrentSource() {
        assertTrue(shouldPublishHomeLoadResult(2L, 2L, "source-b", "source-b"))
        assertFalse(shouldPublishHomeLoadResult(1L, 2L, "source-b", "source-b"))
        assertFalse(shouldPublishHomeLoadResult(2L, 2L, "source-a", "source-b"))
    }

    @Test
    fun progressivePayloadPublishesOnlyForAnEmptyCurrentRequest() {
        assertTrue(
            shouldPublishProgressiveHomePayload(
                hasDisplayDataAtRequestStart = false,
                hasRenderableRows = true,
                requestGeneration = 2L,
                activeGeneration = 2L,
                requestSourceId = "source-b",
                currentSourceId = "source-b"
            )
        )
        assertFalse(
            shouldPublishProgressiveHomePayload(
                hasDisplayDataAtRequestStart = true,
                hasRenderableRows = true,
                requestGeneration = 2L,
                activeGeneration = 2L,
                requestSourceId = "source-b",
                currentSourceId = "source-b"
            )
        )
        assertFalse(
            shouldPublishProgressiveHomePayload(
                hasDisplayDataAtRequestStart = false,
                hasRenderableRows = false,
                requestGeneration = 2L,
                activeGeneration = 2L,
                requestSourceId = "source-b",
                currentSourceId = "source-b"
            )
        )
        assertFalse(
            shouldPublishProgressiveHomePayload(
                hasDisplayDataAtRequestStart = false,
                hasRenderableRows = true,
                requestGeneration = 1L,
                activeGeneration = 2L,
                requestSourceId = "source-a",
                currentSourceId = "source-b"
            )
        )
    }

    @Test
    fun hidesJapaneseSynopsisUntilLocalizedCopyIsReady() {
        assertEquals("", resolveHeroDescription("これは日本語のあらすじです", null))
        assertEquals(
            "這是已預先載入的繁體中文簡介",
            resolveHeroDescription(
                "これは日本語のあらすじです",
                "這是已預先載入的繁體中文簡介"
            )
        )
    }

    @Test
    fun keepsReadyChineseSynopsisWithoutWaitingForCache() {
        assertEquals(
            "已經是中文的作品簡介",
            resolveHeroDescription("已經是中文的作品簡介", null)
        )
        assertFalse("已經是中文的作品簡介".requiresLocalizedHeroSynopsis())
        assertTrue("これは日本語のあらすじです".requiresLocalizedHeroSynopsis())
    }

    @Test
    fun wrapsHeroButDoesNotWrapVerticalRows() {
        assertEquals(0, nextHeroIndex(currentIndex = 6, delta = 1, itemCount = 7))
        assertEquals(6, nextHeroIndex(currentIndex = 0, delta = -1, itemCount = 7))
        assertEquals(2, nextHomeRowIndex(currentIndex = 1, delta = 1, rowCount = 4))
        assertNull(nextHomeRowIndex(currentIndex = 0, delta = -1, rowCount = 4))
    }

    @Test
    fun restoresEachRowsOwnValidSelection() {
        assertEquals(3, restoredHomeRowSelection(savedIndex = 3, itemCount = 6))
        assertEquals(0, restoredHomeRowSelection(savedIndex = 9, itemCount = 6))
        assertEquals(0, restoredHomeRowSelection(savedIndex = null, itemCount = 6))
    }

    @Test
    fun favoritesRowStopsAtItsRealEdgesWithoutRepeatingItems() {
        assertFalse(homeRowShouldLoop(itemCount = 2))
        assertFalse(homeRowShouldLoop(itemCount = 5))
        assertTrue(homeRowShouldLoop(itemCount = 6))

        assertEquals(0, moveFiniteHomeRowSelection(currentIndex = 0, delta = -1, itemCount = 2))
        assertEquals(1, moveFiniteHomeRowSelection(currentIndex = 0, delta = 1, itemCount = 2))
        assertEquals(1, moveFiniteHomeRowSelection(currentIndex = 1, delta = 1, itemCount = 2))
    }

    @Test
    fun virtualRowKeysStayUniqueAcrossCopiesAndSources() {
        assertEquals("12:source-a:anime", homeRowVirtualItemKey(12, "source-a", "anime"))
        assertTrue(
            homeRowVirtualItemKey(12, "source-a", "anime") !=
                homeRowVirtualItemKey(13, "source-a", "anime")
        )
        assertTrue(
            homeRowVirtualItemKey(12, "source-a", "anime") !=
                homeRowVirtualItemKey(12, "source-b", "anime")
        )
    }

    @Test
    fun staleCardFocusCannotPublishAfterLeavingItsRow() {
        assertTrue(shouldPublishFocusedHomeCard(eventRowIndex = 0, focusedRowIndex = 0))
        assertFalse(shouldPublishFocusedHomeCard(eventRowIndex = 0, focusedRowIndex = null))
        assertFalse(shouldPublishFocusedHomeCard(eventRowIndex = 0, focusedRowIndex = 1))
    }

    @Test
    fun rowFocusStillUpdatesLayoutWhenAutoPreviewIsDisabled() {
        assertEquals(
            HomeRowFocusBehavior(notifySelection = true, dimAfterDelay = false),
            homeRowFocusBehavior(rowFocused = true, previewEnabled = false)
        )
        assertEquals(
            HomeRowFocusBehavior(notifySelection = true, dimAfterDelay = true),
            homeRowFocusBehavior(rowFocused = true, previewEnabled = true)
        )
        assertEquals(
            HomeRowFocusBehavior(notifySelection = false, dimAfterDelay = false),
            homeRowFocusBehavior(rowFocused = false, previewEnabled = true)
        )
    }

    @Test
    fun resumesRotationOnlyAfterManualHeroInteractionAndOutsideRows() {
        assertTrue(shouldResumeHeroRotation(manualInteractionCount = 1, focusedRowIndex = null))
        assertFalse(shouldResumeHeroRotation(manualInteractionCount = 0, focusedRowIndex = null))
        assertFalse(shouldResumeHeroRotation(manualInteractionCount = 1, focusedRowIndex = 0))
    }

    @Test
    fun prefetchesUniquePostersWithinABoundedBudget() {
        fun anime(id: String, imageUrl: String) = AnimeData(
            id = id,
            url = "https://example.test/$id",
            title = id,
            imageUrl = imageUrl,
            sourceId = "test"
        )

        assertEquals(
            listOf("hero.jpg", "row-a.jpg", "row-b.jpg"),
            homePosterPrefetchUrls(
                featured = listOf(anime("hero", "hero.jpg")),
                rows = listOf(
                    listOf(anime("duplicate", "hero.jpg"), anime("a", "row-a.jpg")),
                    listOf(anime("b", "row-b.jpg"), anime("c", "row-c.jpg"))
                ),
                maxItems = 3
            )
        )
    }

    @Test
    fun welcomeBackdropSelectsRecentAnimeWithMatchingPostersWithinItsLimit() {
        fun anime(id: String, imageUrl: String) = AnimeData(
            id = id,
            url = "https://example.test/$id",
            title = id,
            imageUrl = imageUrl,
            sourceId = "test"
        )
        val selected = welcomeBackdropAnime(
            rows = listOf(
                listOf(
                    anime("a", "a.jpg"),
                    anime("duplicate", "a.jpg"),
                    anime("blank", "")
                ),
                (1..10).map { anime("item-$it", "item-$it.jpg") }
            ),
            randomSeed = 42,
            maxItems = 5,
            candidateLimit = 8
        )

        assertEquals(
            selected,
            welcomeBackdropAnime(
                rows = listOf(
                    listOf(
                        anime("a", "a.jpg"),
                        anime("duplicate", "a.jpg"),
                        anime("blank", "")
                    ),
                    (1..10).map { anime("item-$it", "item-$it.jpg") }
                ),
                randomSeed = 42,
                maxItems = 5,
                candidateLimit = 8
            )
        )
        assertEquals(5, selected.size)
        assertEquals(selected.map(AnimeData::imageUrl).distinct(), selected.map(AnimeData::imageUrl))
        assertTrue(selected.all { it.id == it.title })
        assertTrue(selected.all {
            it.imageUrl == "a.jpg" ||
                it.imageUrl.removePrefix("item-").removeSuffix(".jpg").toInt() <= 7
        })
    }

    @Test
    fun welcomeBackdropSamplesAcrossRecentRowsAndRefreshesEverySixHours() {
        fun anime(row: Int, item: Int) = AnimeData(
            id = "$row-$item",
            url = "https://example.test/$row/$item",
            title = "$row-$item",
            imageUrl = "$row-$item.jpg",
            sourceId = "test"
        )
        val selected = welcomeBackdropAnime(
            rows = (0..8).map { row -> (0..5).map { item -> anime(row, item) } },
            randomSeed = 7,
            maxItems = 8,
            candidateLimit = 8
        )

        assertEquals((0..7).map { "$it-0" }.toSet(), selected.map(AnimeData::id).toSet())
        assertEquals(welcomeContentSeed(0L), welcomeContentSeed(WELCOME_CONTENT_REFRESH_INTERVAL_MS - 1))
        assertTrue(
            welcomeContentSeed(0L) != welcomeContentSeed(WELCOME_CONTENT_REFRESH_INTERVAL_MS)
        )
    }
}
