package com.jing.sakura.compose.screen

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.jing.sakura.data.AnimeData
import com.jing.sakura.data.AnimePageData
import com.jing.sakura.data.NamedValue
import kotlinx.coroutines.CancellationException

internal fun interface DiscoverPageLoader {
    suspend fun load(filters: List<NamedValue<String>>, page: Int): AnimePageData
}

internal class DiscoverCatalogPagingSource(
    selectedFilters: Map<String, String>,
    defaultFilters: Map<String, String>,
    private val loader: DiscoverPageLoader
) : PagingSource<Int, AnimeData>() {
    private val primaryFilters = selectedFilters.withoutBlankValues()
    private val fallbackFilters = discoverFallbackFilters(primaryFilters, defaultFilters)
    private val seenAnimeIds = linkedSetOf<String>()
    private var fallbackActive = false

    override fun getRefreshKey(state: PagingState<Int, AnimeData>): Int? = null

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, AnimeData> {
        val page = (params.key ?: FIRST_PAGE).coerceAtLeast(FIRST_PAGE)
        if (params is LoadParams.Refresh) {
            seenAnimeIds.clear()
            fallbackActive = false
        }

        val activeFilters = if (fallbackActive) fallbackFilters else primaryFilters
        val activeResult = loadPage(activeFilters, page)
        val result = when {
            activeResult != null -> activeResult

            page == FIRST_PAGE && activeFilters != fallbackFilters -> {
                val fallbackResult = loadPage(fallbackFilters, FIRST_PAGE)
                    ?: return LoadResult.Error(
                        IllegalStateException("暫時未能載入發現頁內容")
                    )
                fallbackActive = true
                fallbackResult
            }

            page > FIRST_PAGE -> {
                return LoadResult.Page(
                    data = emptyList(),
                    prevKey = null,
                    nextKey = null
                )
            }

            else -> {
                return LoadResult.Error(
                    IllegalStateException("暫時未能載入發現頁內容")
                )
            }
        }

        val uniqueItems = result.animeList.filter { anime ->
            anime.id.isNotBlank() && seenAnimeIds.add(anime.id)
        }
        return LoadResult.Page(
            data = uniqueItems,
            prevKey = null,
            nextKey = if (result.hasNextPage && uniqueItems.isNotEmpty()) page + 1 else null
        )
    }

    private suspend fun loadPage(
        filters: Map<String, String>,
        page: Int
    ): AnimePageData? = try {
        loader.load(
            filters = filters.map { (key, value) -> NamedValue(name = key, value = value) },
            page = page
        )
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        null
    }

    private companion object {
        const val FIRST_PAGE = 1
    }
}

internal fun discoverFallbackFilters(
    selectedFilters: Map<String, String>,
    defaultFilters: Map<String, String>
): Map<String, String> {
    val selectedType = selectedFilters[TYPE_FILTER].orEmpty()
    val defaultType = defaultFilters[TYPE_FILTER].orEmpty()
    return linkedMapOf<String, String>().apply {
        (selectedType.ifBlank { defaultType })
            .takeIf(String::isNotBlank)
            ?.let { put(TYPE_FILTER, it) }
    }
}

internal fun orderedDiscoverFilters(
    categoryKeys: List<String>,
    selectedValues: Map<String, String>
): Map<String, String> = categoryKeys.mapNotNull { key ->
    selectedValues[key]
        ?.takeIf(String::isNotBlank)
        ?.let { key to it }
}.toMap(linkedMapOf())

internal fun discoverPosterPrefetchIndices(
    firstVisibleIndex: Int,
    lastVisibleIndex: Int,
    itemCount: Int,
    columns: Int = 5,
    rowsBehind: Int = 1,
    rowsAhead: Int = 2,
    maxItems: Int = 20
): List<Int> {
    if (
        itemCount <= 0 ||
        firstVisibleIndex < 0 ||
        lastVisibleIndex < firstVisibleIndex ||
        columns <= 0 ||
        maxItems <= 0
    ) {
        return emptyList()
    }
    val start = (firstVisibleIndex - columns * rowsBehind.coerceAtLeast(0))
        .coerceAtLeast(0)
    val end = (lastVisibleIndex + columns * rowsAhead.coerceAtLeast(0))
        .coerceAtMost(itemCount - 1)
    return (start..end).take(maxItems)
}

internal enum class DiscoverFocusZone {
    TOP_NAVIGATION,
    RESULTS
}

internal enum class DiscoverBackAction {
    RETURN_TO_TOP,
    EXIT
}

internal fun discoverBackAction(zone: DiscoverFocusZone): DiscoverBackAction =
    if (zone == DiscoverFocusZone.RESULTS) {
        DiscoverBackAction.RETURN_TO_TOP
    } else {
        DiscoverBackAction.EXIT
    }

internal fun discoverAdjacentIndex(current: Int, itemCount: Int, step: Int): Int {
    if (itemCount <= 0) return 0
    return (current + step).coerceIn(0, itemCount - 1)
}

private fun Map<String, String>.withoutBlankValues(): Map<String, String> =
    entries
        .filter { it.value.isNotBlank() }
        .associateTo(linkedMapOf()) { it.key to it.value }

private const val TYPE_FILTER = "type_id"
