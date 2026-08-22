package com.jing.sakura.compose.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DetailRelatedBrowsePolicyTest {
    @Test
    fun detailViewportGeometryDoesNotDependOnFocus() {
        val policy = detailRelatedBrowsePolicy(playlistCount = 2)

        assertEquals(226, policy.listViewportTopDp)
        assertEquals(120, policy.heroClearanceDp)
        assertEquals(24, policy.upperViewportScrollOffsetDp)
    }

    @Test
    fun detailWithoutEpisodesDoesNotClipTheRelatedRowHeading() {
        val policy = detailRelatedBrowsePolicy(playlistCount = 0)

        assertEquals(226, policy.listViewportTopDp)
        assertEquals(120, policy.heroClearanceDp)
        assertEquals(0, policy.upperViewportScrollOffsetDp)
    }

    @Test
    fun relatedFocusExclusivelySwitchesHeroVisibilityAndInteraction() {
        val upperPresentation = detailHeroPresentation(rowFocused = false)
        val relatedPresentation = detailHeroPresentation(rowFocused = true)

        assertFalse(upperPresentation.showRelatedHero)
        assertTrue(upperPresentation.mainHeroInteractive)
        assertTrue(relatedPresentation.showRelatedHero)
        assertFalse(relatedPresentation.mainHeroInteractive)
    }

    @Test
    fun relatedEntryIndexIncludesClearanceAndOptionalPlaylistRows() {
        assertEquals(1, detailRelatedRowIndex(playlistCount = 0))
        assertEquals(2, detailRelatedRowIndex(playlistCount = 1))
    }

    @Test
    fun relatedCarouselLoopsFromStableMiddleIndex() {
        val itemCount = 7
        val initial = detailRelatedInitialVirtualIndex(itemCount)

        assertEquals(0, detailRelatedLogicalIndex(initial, itemCount))
        assertEquals(1, detailRelatedLogicalIndex(
            detailRelatedMoveVirtualIndex(initial, 1, itemCount),
            itemCount
        ))
        assertEquals(itemCount - 1, detailRelatedLogicalIndex(
            detailRelatedMoveVirtualIndex(initial, -1, itemCount),
            itemCount
        ))
        assertEquals(Int.MAX_VALUE, detailRelatedVirtualItemCount(itemCount))
    }

    @Test
    fun singleRelatedItemDoesNotMove() {
        assertEquals(1, detailRelatedVirtualItemCount(1))
        assertEquals(0, detailRelatedMoveVirtualIndex(0, 1, 1))
    }

    @Test
    fun blankRelatedArtworkFallsBackToDetailArtwork() {
        assertEquals(
            "detail.jpg",
            detailBackdropImageUrl("detail.jpg", "  ")
        )
        assertEquals(
            "related.jpg",
            detailBackdropImageUrl("detail.jpg", " related.jpg ")
        )
    }

    @Test
    fun relatedExitIsCancelledOnlyWhenRowActuallyRegainsFocus() {
        assertFalse(shouldCancelDetailRelatedExit(
            wasRowFocused = true,
            isRowFocused = true
        ))
        assertFalse(shouldCancelDetailRelatedExit(
            wasRowFocused = true,
            isRowFocused = false
        ))
        assertTrue(shouldCancelDetailRelatedExit(
            wasRowFocused = false,
            isRowFocused = true
        ))
    }

    @Test
    fun relatedExitRetriesFocusOnlyAfterFirstRequestFails() {
        assertTrue(shouldRetryDetailRelatedExitFocus(firstRequestSucceeded = false))
        assertFalse(shouldRetryDetailRelatedExitFocus(firstRequestSucceeded = true))
    }

    @Test
    fun parentFocusRelocationNeverMovesDetailViewport() {
        assertEquals(0f, detailParentBringIntoViewScrollDistance(24f, 44f, 720f), 0f)
        assertEquals(0f, detailParentBringIntoViewScrollDistance(-170f, 44f, 720f), 0f)
        assertEquals(0f, detailParentBringIntoViewScrollDistance(700f, 80f, 720f), 0f)
    }

    @Test
    fun relatedSelectionPublishesOnlyOnRealFocusEntry() {
        assertTrue(shouldPublishDetailRelatedSelection(
            wasRowFocused = false,
            isRowFocused = true
        ))
        assertFalse(shouldPublishDetailRelatedSelection(
            wasRowFocused = true,
            isRowFocused = true
        ))
        assertFalse(shouldPublishDetailRelatedSelection(
            wasRowFocused = true,
            isRowFocused = false
        ))
    }

    @Test
    fun relatedDimTimerRequiresFocusAndPreview() {
        assertFalse(shouldDimDetailRelatedSelection(
            rowFocused = true,
            previewEnabled = false
        ))
        assertTrue(shouldDimDetailRelatedSelection(
            rowFocused = true,
            previewEnabled = true
        ))
        assertFalse(shouldDimDetailRelatedSelection(
            rowFocused = false,
            previewEnabled = true
        ))
    }

    @Test
    fun relatedPreviewRequiresCurrentSelectionAndFirstFrame() {
        assertTrue(isDetailRelatedPreviewPlaybackActive(
            previewEnabled = true,
            isScreenResumed = true,
            rowFocused = true,
            previewArmed = true,
            firstFrameReady = true,
            readyAnimeId = "anime-2",
            readySourceId = "source-a",
            selectedAnimeId = "anime-2",
            selectedSourceId = "source-a"
        ))
        assertFalse(isDetailRelatedPreviewPlaybackActive(
            previewEnabled = true,
            isScreenResumed = true,
            rowFocused = true,
            previewArmed = true,
            firstFrameReady = true,
            readyAnimeId = "anime-1",
            readySourceId = "source-a",
            selectedAnimeId = "anime-2",
            selectedSourceId = "source-a"
        ))
        assertFalse(isDetailRelatedPreviewPlaybackActive(
            previewEnabled = true,
            isScreenResumed = false,
            rowFocused = true,
            previewArmed = true,
            firstFrameReady = true,
            readyAnimeId = "anime-2",
            readySourceId = "source-a",
            selectedAnimeId = "anime-2",
            selectedSourceId = "source-a"
        ))
    }

    @Test
    fun relatedPreviewUsesHomeTimingContract() {
        assertEquals(10_000L, DETAIL_RELATED_PREVIEW_DWELL_MS)
        assertEquals(60_000L, DETAIL_RELATED_PREVIEW_LIMIT_MS)
    }

    @Test(expected = IllegalArgumentException::class)
    fun negativePlaylistCountIsRejected() {
        detailRelatedBrowsePolicy(playlistCount = -1)
    }
}
