package com.jing.sakura.home

import com.jing.sakura.data.AnimeData
import kotlin.random.Random

internal const val HERO_ROTATION_INTERVAL_MS = 8_000L
internal const val HERO_MANUAL_RESUME_DELAY_MS = 12_000L
internal const val HOME_ROW_LOOP_MIN_ITEMS = 6

internal fun nextHeroIndex(currentIndex: Int, delta: Int, itemCount: Int): Int {
    if (itemCount <= 0) return 0
    return (currentIndex + delta).floorMod(itemCount)
}

internal fun nextHomeRowIndex(currentIndex: Int, delta: Int, rowCount: Int): Int? {
    if (rowCount <= 0) return null
    return (currentIndex + delta).takeIf { it in 0 until rowCount }
}

internal fun restoredHomeRowSelection(savedIndex: Int?, itemCount: Int): Int {
    if (itemCount <= 0) return 0
    return savedIndex?.takeIf { it in 0 until itemCount } ?: 0
}

internal fun homeRowShouldLoop(itemCount: Int): Boolean =
    itemCount >= HOME_ROW_LOOP_MIN_ITEMS

internal fun moveFiniteHomeRowSelection(
    currentIndex: Int,
    delta: Int,
    itemCount: Int
): Int {
    if (itemCount <= 0) return 0
    return (currentIndex + delta).coerceIn(0, itemCount - 1)
}

internal fun shouldResumeHeroRotation(
    manualInteractionCount: Int,
    focusedRowIndex: Int?
): Boolean = manualInteractionCount > 0 && focusedRowIndex == null

internal fun resolveHeroDescription(original: String, cachedLocalized: String?): String {
    val localized = cachedLocalized.orEmpty().trim()
    if (localized.isNotBlank() && !localized.requiresLocalizedHeroSynopsis()) {
        return localized
    }
    return original.trim().takeUnless(String::requiresLocalizedHeroSynopsis).orEmpty()
}

internal fun String.requiresLocalizedHeroSynopsis(): Boolean {
    if (isBlank()) return true
    val kanaCount = count { char ->
        char in '\u3040'..'\u30ff' ||
            char in '\u31f0'..'\u31ff' ||
            char in '\uff66'..'\uff9f'
    }
    if (kanaCount == 0) return false
    val letterCount = count(Char::isLetter).coerceAtLeast(1)
    return kanaCount >= 4 && kanaCount * 5 >= letterCount
}

internal fun homeSynopsisKey(sourceId: String, animeId: String): String =
    "synopsis_${sourceId.trim()}_${animeId.trim()}"

internal fun homeDescriptionKey(anime: AnimeData): String =
    homeSynopsisKey(anime.sourceId, anime.id)

internal fun homePosterPrefetchUrls(
    featured: List<AnimeData>,
    rows: List<List<AnimeData>>,
    maxItems: Int = 28
): List<String> {
    if (maxItems <= 0) return emptyList()
    return sequence {
        yieldAll(featured)
        rows.forEach { yieldAll(it) }
    }
        .map(AnimeData::imageUrl)
        .map(String::trim)
        .filter(String::isNotBlank)
        .distinct()
        .take(maxItems)
        .toList()
}

internal fun welcomeBackdropAnime(
    rows: List<List<AnimeData>>,
    randomSeed: Int,
    maxItems: Int = 5,
    candidateLimit: Int = 24
): List<AnimeData> {
    if (maxItems <= 0 || candidateLimit <= 0) return emptyList()
    val candidates = ArrayList<AnimeData>(candidateLimit)
    val seenImageUrls = HashSet<String>(candidateLimit)
    rowLoop@ for (row in rows) {
        for (anime in row) {
            val imageUrl = anime.imageUrl.trim()
            if (imageUrl.isBlank() || !seenImageUrls.add(imageUrl)) continue
            candidates += anime.copy(imageUrl = imageUrl)
            if (candidates.size >= candidateLimit) break@rowLoop
        }
    }
    candidates.shuffle(Random(randomSeed))
    return candidates.take(maxItems)
}

private fun Int.floorMod(divisor: Int): Int = ((this % divisor) + divisor) % divisor
