package com.jing.sakura.search

import org.junit.Assert.assertEquals
import org.junit.Test

class SearchUiPolicyTest {
    @Test
    fun formatsKeywordAsResultHeading() {
        assertEquals("「葬送的芙莉蓮」搜尋結果", searchResultTitle("  葬送的芙莉蓮  "))
    }

    @Test
    fun keepsNewestFiveNonBlankUniqueSearches() {
        assertEquals(
            listOf("A", "B", "C", "D", "E"),
            latestSearchKeywords(listOf(" A ", "B", "", "a", "C", "D", "E", "F"))
        )
    }

    @Test
    fun normalizesWhitespaceAndCaseForStableDeduplication() {
        assertEquals("New Century EVA", normalizeSearchKeyword("  New   Century\nEVA  "))
        assertEquals("new century eva", searchKeywordKey(" New Century EVA "))
    }
}
