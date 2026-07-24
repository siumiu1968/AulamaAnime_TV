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

internal fun detailBackdropImageUrl(
    detailImageUrl: String,
    relatedImageUrl: String?
): String = relatedImageUrl
    ?.trim()
    ?.takeIf(String::isNotEmpty)
    ?: detailImageUrl
