package com.jing.sakura.compose.screen

import org.junit.Assert.assertEquals
import org.junit.Test

class DiscoverCatalogPagingSourceTest {
    @Test
    fun preservesServerCategoryOrderInsteadOfSortingKeys() {
        val ordered = orderedDiscoverFilters(
            categoryKeys = listOf("type_id", "class", "year", "letter"),
            selectedValues = mapOf(
                "letter" to "A",
                "year" to "2026",
                "class" to "日常",
                "type_id" to "20"
            )
        )

        assertEquals(
            listOf("type_id", "class", "year", "letter"),
            ordered.keys.toList()
        )
    }

    @Test
    fun dropsMissingAndBlankFilterValues() {
        val ordered = orderedDiscoverFilters(
            categoryKeys = listOf("type_id", "class", "year"),
            selectedValues = mapOf("type_id" to "20", "class" to " ")
        )

        assertEquals(mapOf("type_id" to "20"), ordered)
    }
}
