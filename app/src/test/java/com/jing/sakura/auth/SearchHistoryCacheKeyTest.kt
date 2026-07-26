package com.jing.sakura.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SearchHistoryCacheKeyTest {
    @Test
    fun guestAndAccountsUseSeparateStableCacheKeys() {
        assertEquals("guest", searchHistoryCacheKey(""))
        assertEquals(
            searchHistoryCacheKey("USER@example.com"),
            searchHistoryCacheKey(" user@example.com ")
        )
        assertNotEquals(
            searchHistoryCacheKey("first@example.com"),
            searchHistoryCacheKey("second@example.com")
        )
        assertFalse(searchHistoryCacheKey("first@example.com").contains("first@example.com"))
    }
}
