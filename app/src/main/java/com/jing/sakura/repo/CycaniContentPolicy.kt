package com.jing.sakura.repo

import com.jing.sakura.data.AnimeData

internal fun isSuppressedAnime(anime: AnimeData): Boolean =
    isSuppressedAnimeTitle(anime.title)

internal fun isSuppressedAnimeTitle(title: String): Boolean {
    val normalized = title
        .replace(" ", "")
        .replace("：", ":")
        .lowercase()
    return normalized.contains("明日方舟") &&
        normalized.contains("焰") &&
        normalized.contains("曙明")
}
