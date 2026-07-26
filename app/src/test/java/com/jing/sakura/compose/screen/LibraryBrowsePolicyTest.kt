package com.jing.sakura.compose.screen

import org.junit.Assert.assertEquals
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
}
