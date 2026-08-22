package com.jing.sakura.compose.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VirtualCarouselPolicyTest {
    @Test
    fun identicalOrderedItemsInDifferentRowsHaveDifferentIdentity() {
        val itemKeys = listOf("source:a", "source:b")

        val recommendations = virtualCarouselIdentity("為你推介", itemKeys)
        val favorites = virtualCarouselIdentity("我的收藏", itemKeys)

        assertTrue(recommendations != favorites)
        assertEquals(recommendations, virtualCarouselIdentity("為你推介", itemKeys))
    }

    @Test
    fun repeatedRightMovesStayBoundedAndPreserveLogicalOrder() {
        assertPressureDirection(delta = 1)
    }

    @Test
    fun repeatedLeftMovesStayBoundedAndPreserveLogicalOrder() {
        assertPressureDirection(delta = -1)
    }

    @Test
    fun corruptedVirtualIndicesRecoverWithoutNegativeLogicalIndices() {
        val left = boundedVirtualCarouselMove(-500, -1, itemCount = 7)
        val right = boundedVirtualCarouselMove(Int.MAX_VALUE, 1, itemCount = 7)

        assertTrue(left.targetIndex >= 0)
        assertTrue(right.targetIndex < virtualCarouselItemCount(7))
        assertTrue(left.logicalIndex in 0 until 7)
        assertTrue(right.logicalIndex in 0 until 7)
    }

    private fun assertPressureDirection(delta: Int) {
        val itemCount = 7
        val virtualCount = virtualCarouselItemCount(itemCount)
        var virtualIndex = virtualCarouselCenterIndex(itemCount)
        var expectedLogicalIndex = 0
        var sawRecenter = false

        repeat(10_000) {
            val move = boundedVirtualCarouselMove(
                currentIndex = virtualIndex,
                delta = delta,
                itemCount = itemCount
            )
            expectedLogicalIndex = Math.floorMod(expectedLogicalIndex + delta, itemCount)
            assertEquals(expectedLogicalIndex, move.logicalIndex)
            assertTrue(move.targetIndex in 0 until virtualCount)
            move.recenterIndex?.let { recentered ->
                sawRecenter = true
                assertTrue(recentered in 0 until virtualCount)
                assertEquals(move.logicalIndex, virtualCarouselLogicalIndex(recentered, itemCount))
            }
            virtualIndex = move.recenterIndex ?: move.targetIndex
        }
        assertTrue(sawRecenter)
    }
}
