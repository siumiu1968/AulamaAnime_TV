package com.jing.sakura.compose.screen

internal data class DetailRelatedBrowsePolicy(
    val isBrowsingRelated: Boolean,
    val heroHeightDp: Int,
    val relatedRowIndex: Int?,
    val descriptionMaxLines: Int
)

internal fun detailRelatedBrowsePolicy(
    playlistCount: Int,
    focusedRelatedAnimeId: String?
): DetailRelatedBrowsePolicy {
    require(playlistCount >= 0) { "playlistCount must not be negative" }
    val isBrowsingRelated = !focusedRelatedAnimeId.isNullOrBlank()
    return DetailRelatedBrowsePolicy(
        isBrowsingRelated = isBrowsingRelated,
        heroHeightDp = if (isBrowsingRelated) 226 else 346,
        relatedRowIndex = playlistCount.takeIf { isBrowsingRelated },
        descriptionMaxLines = if (isBrowsingRelated) 3 else 2
    )
}

internal fun detailRelatedVirtualItemCount(itemCount: Int): Int = when {
    itemCount <= 0 -> 0
    itemCount == 1 -> 1
    else -> Int.MAX_VALUE
}

internal fun detailRelatedInitialVirtualIndex(itemCount: Int): Int {
    if (itemCount <= 1) return 0
    val midpoint = Int.MAX_VALUE / 2
    return midpoint - midpoint % itemCount
}

internal fun detailRelatedLogicalIndex(virtualIndex: Int, itemCount: Int): Int {
    require(itemCount > 0) { "itemCount must be positive" }
    return Math.floorMod(virtualIndex, itemCount)
}

internal fun detailRelatedMoveVirtualIndex(
    currentIndex: Int,
    delta: Int,
    itemCount: Int
): Int {
    if (itemCount <= 1 || delta == 0) return currentIndex
    return (currentIndex.toLong() + delta).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
}

internal fun detailBackdropImageUrl(
    detailImageUrl: String,
    relatedImageUrl: String?
): String = relatedImageUrl
    ?.trim()
    ?.takeIf(String::isNotEmpty)
    ?: detailImageUrl
