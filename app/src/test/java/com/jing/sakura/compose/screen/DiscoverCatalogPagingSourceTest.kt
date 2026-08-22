package com.jing.sakura.compose.screen

import androidx.paging.PagingSource
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    @Test
    fun sourceFailureIsRetryableAndNeverSwitchesToDifferentFilters() = runBlocking {
        val requests = mutableListOf<List<Pair<String, String>>>()
        val source = DiscoverCatalogPagingSource(
            selectedFilters = linkedMapOf(
                "type_id" to "21",
                "year" to "2026"
            ),
            loader = DiscoverPageLoader { filters, _ ->
                requests += filters.map { it.name to it.value }
                throw IllegalStateException("temporary upstream failure")
            }
        )

        val result = source.load(
            PagingSource.LoadParams.Refresh(
                key = null,
                loadSize = 20,
                placeholdersEnabled = false
            )
        )

        assertTrue(result is PagingSource.LoadResult.Error)
        assertEquals(
            listOf(listOf("type_id" to "21", "year" to "2026")),
            requests
        )
    }
}
