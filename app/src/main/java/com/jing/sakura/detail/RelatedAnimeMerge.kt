package com.jing.sakura.detail

import com.jing.sakura.data.AnimeData

internal fun relatedAnimeKey(anime: AnimeData, fallbackSourceId: String = ""): String {
    return "${anime.sourceId.ifBlank { fallbackSourceId }}:${anime.id}"
}

internal fun mergeRelatedAnime(
    cloud: List<AnimeData>,
    source: List<AnimeData>,
    fallbackSourceId: String
): List<AnimeData> {
    val normalizedSource = source.map { anime ->
        anime.copy(sourceId = anime.sourceId.ifBlank { fallbackSourceId })
    }
    if (cloud.isEmpty()) return normalizedSource

    val sourceByKey = normalizedSource.associateBy(::relatedAnimeKey)
    val sourceById = normalizedSource.associateBy(AnimeData::id)
    val matchedSourceKeys = mutableSetOf<String>()

    val mergedCloud = cloud.map { cloudAnime ->
        val normalizedCloud = cloudAnime.copy(
            sourceId = cloudAnime.sourceId.ifBlank { fallbackSourceId }
        )
        val sourceAnime = sourceByKey[relatedAnimeKey(normalizedCloud)]
            ?: sourceById[normalizedCloud.id]

        if (sourceAnime == null) {
            normalizedCloud
        } else {
            matchedSourceKeys += relatedAnimeKey(sourceAnime)
            normalizedCloud.copy(
                url = normalizedCloud.url.ifBlank(sourceAnime::url),
                title = normalizedCloud.title.ifBlank(sourceAnime::title),
                currentEpisode = normalizedCloud.currentEpisode.ifBlank(sourceAnime::currentEpisode),
                imageUrl = normalizedCloud.imageUrl.ifBlank(sourceAnime::imageUrl),
                description = normalizedCloud.description.ifBlank(sourceAnime::description),
                tags = normalizedCloud.tags.ifBlank(sourceAnime::tags),
                year = normalizedCloud.year.ifBlank(sourceAnime::year)
            )
        }
    }

    return (mergedCloud + normalizedSource.filterNot {
        relatedAnimeKey(it) in matchedSourceKeys
    }).distinctBy(::relatedAnimeKey)
}
