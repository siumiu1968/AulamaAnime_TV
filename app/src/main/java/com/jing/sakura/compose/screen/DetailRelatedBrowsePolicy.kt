package com.jing.sakura.compose.screen

internal data class DetailRelatedBrowsePolicy(
    val listViewportTopDp: Int,
    val heroClearanceDp: Int,
    val upperViewportScrollOffsetDp: Int
)

internal data class DetailHeroPresentation(
    val showRelatedHero: Boolean,
    val mainHeroInteractive: Boolean
)

internal const val DETAIL_RELATED_PREVIEW_DWELL_MS = 10_000L
internal const val DETAIL_RELATED_PREVIEW_LIMIT_MS = 60_000L

internal fun detailRelatedBrowsePolicy(
    playlistCount: Int
): DetailRelatedBrowsePolicy {
    require(playlistCount >= 0) { "playlistCount must not be negative" }
    return DetailRelatedBrowsePolicy(
        listViewportTopDp = 226,
        heroClearanceDp = 120,
        upperViewportScrollOffsetDp = if (playlistCount > 0) 24 else 0
    )
}

internal fun detailRelatedRowIndex(playlistCount: Int): Int {
    require(playlistCount >= 0) { "playlistCount must not be negative" }
    return playlistCount + 1
}

internal fun detailHeroPresentation(rowFocused: Boolean): DetailHeroPresentation =
    DetailHeroPresentation(
        showRelatedHero = rowFocused,
        mainHeroInteractive = !rowFocused
    )

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

internal fun shouldCancelDetailRelatedExit(
    wasRowFocused: Boolean,
    isRowFocused: Boolean
): Boolean = !wasRowFocused && isRowFocused

internal fun shouldRetryDetailRelatedExitFocus(
    firstRequestSucceeded: Boolean
): Boolean = !firstRequestSucceeded

@Suppress("UNUSED_PARAMETER")
internal fun detailParentBringIntoViewScrollDistance(
    offset: Float,
    size: Float,
    containerSize: Float
): Float = 0f

internal fun shouldPublishDetailRelatedSelection(
    wasRowFocused: Boolean,
    isRowFocused: Boolean
): Boolean = !wasRowFocused && isRowFocused

internal fun shouldDimDetailRelatedSelection(
    rowFocused: Boolean,
    previewEnabled: Boolean
): Boolean = rowFocused && previewEnabled

internal fun isDetailRelatedPreviewPlaybackActive(
    previewEnabled: Boolean,
    isScreenResumed: Boolean,
    rowFocused: Boolean,
    previewArmed: Boolean,
    firstFrameReady: Boolean,
    readyAnimeId: String?,
    readySourceId: String?,
    selectedAnimeId: String?,
    selectedSourceId: String?
): Boolean = previewEnabled &&
    isScreenResumed &&
    rowFocused &&
    previewArmed &&
    firstFrameReady &&
    !readyAnimeId.isNullOrBlank() &&
    !readySourceId.isNullOrBlank() &&
    readyAnimeId == selectedAnimeId &&
    readySourceId == selectedSourceId
