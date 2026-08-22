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
    private val loader: DiscoverPageLoader
) : PagingSource<Int, AnimeData>() {
    private val primaryFilters = selectedFilters.withoutBlankValues()
    private val seenAnimeIds = linkedSetOf<String>()

    override fun getRefreshKey(state: PagingState<Int, AnimeData>): Int? = null

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, AnimeData> {
        val page = (params.key ?: FIRST_PAGE).coerceAtLeast(FIRST_PAGE)
        if (params is LoadParams.Refresh) {
            seenAnimeIds.clear()
        }

        val result = try {
            loader.load(
                filters = primaryFilters.map { (key, value) ->
                    NamedValue(name = key, value = value)
                },
                page = page
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            return LoadResult.Error(
                IllegalStateException("暫時未能載入發現頁內容", error)
            )
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

    private companion object {
        const val FIRST_PAGE = 1
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
