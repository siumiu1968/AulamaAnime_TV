package com.jing.sakura.compose.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LibraryBrowsePolicyTest {
    @Test
    fun rowsWithFewerThanSixItemsStopAtTheirRealEdges() {
        assertEquals(0, moveLibraryRowIndex(0, -1, itemCount = 2, loopEnabled = false))
        assertEquals(1, moveLibraryRowIndex(0, 1, itemCount = 2, loopEnabled = false))
        assertEquals(1, moveLibraryRowIndex(1, 1, itemCount = 2, loopEnabled = false))
    }

    @Test
    fun rowsWithSixOrMoreItemsKeepTheirVirtualMovement() {
        assertEquals(100, moveLibraryRowIndex(99, 1, itemCount = 6, loopEnabled = true))
        assertEquals(98, moveLibraryRowIndex(99, -1, itemCount = 6, loopEnabled = true))
    }

    @Test
    fun verticalMovementStopsAtTheLibraryEdges() {
        assertNull(nextLibraryRowIndex(currentIndex = 0, delta = -1, rowCount = 2))
        assertEquals(1, nextLibraryRowIndex(currentIndex = 0, delta = 1, rowCount = 2))
        assertEquals(0, nextLibraryRowIndex(currentIndex = 1, delta = -1, rowCount = 2))
        assertNull(nextLibraryRowIndex(currentIndex = 1, delta = 1, rowCount = 2))
    }

    @Test
    fun rowAndItemSelectionFollowStableIdentityAcrossReordering() {
        assertEquals(
            1,
            restoredLibraryIdentityIndex("continue", listOf("favorites", "continue"))
        )
        assertEquals(
            2,
            restoredLibraryIdentityIndex("source:anime-2", listOf("a", "b", "source:anime-2"))
        )
    }

    @Test
    fun missingStableIdentityFallsBackOnlyThen() {
        assertEquals(0, restoredLibraryIdentityIndex("removed", listOf("a", "b")))
        assertEquals(0, restoredLibraryIdentityIndex(null, listOf("a", "b")))
        assertEquals(0, restoredLibraryIdentityIndex("removed", emptyList()))
    }
}
