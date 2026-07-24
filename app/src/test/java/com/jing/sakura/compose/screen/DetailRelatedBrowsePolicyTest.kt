package com.jing.sakura.compose.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DetailRelatedBrowsePolicyTest {
    @Test
    fun episodeBrowsingKeepsFullHeroAndDoesNotAnchorRelatedRow() {
        val policy = detailRelatedBrowsePolicy(
            playlistCount = 2,
            focusedRelatedAnimeId = null
        )

        assertFalse(policy.isBrowsingRelated)
        assertEquals(346, policy.heroHeightDp)
        assertNull(policy.relatedRowIndex)
        assertEquals(2, policy.descriptionMaxLines)
    }

    @Test
    fun relatedBrowsingAnchorsToSameRowForEveryHorizontalSelection() {
        val first = detailRelatedBrowsePolicy(
            playlistCount = 3,
            focusedRelatedAnimeId = "related-a"
        )
        val second = detailRelatedBrowsePolicy(
            playlistCount = 3,
            focusedRelatedAnimeId = "related-b"
        )

        assertTrue(first.isBrowsingRelated)
        assertEquals(226, first.heroHeightDp)
        assertEquals(3, first.relatedRowIndex)
        assertEquals(first.relatedRowIndex, second.relatedRowIndex)
        assertEquals(first.heroHeightDp, second.heroHeightDp)
        assertEquals(3, second.descriptionMaxLines)
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

    @Test(expected = IllegalArgumentException::class)
    fun negativePlaylistCountIsRejected() {
        detailRelatedBrowsePolicy(
            playlistCount = -1,
            focusedRelatedAnimeId = "related"
        )
    }
}
