package com.jing.sakura.timeline

import com.jing.sakura.data.AnimeData
import com.jing.sakura.data.UpdateTimeLine

internal const val TIMELINE_POSTERS_PER_DAY_PREFETCH = 5
private const val TIMELINE_CAROUSEL_COPIES = 2_001

internal fun timelineVirtualItemCount(itemCount: Int): Int = when {
    itemCount <= 0 -> 0
    itemCount == 1 -> 1
    else -> itemCount * TIMELINE_CAROUSEL_COPIES
}

internal fun timelineInitialVirtualIndex(itemCount: Int): Int {
    if (itemCount <= 1) return 0
    return itemCount * (TIMELINE_CAROUSEL_COPIES / 2)
}

internal fun timelineLogicalIndex(virtualIndex: Int, itemCount: Int): Int {
    if (itemCount <= 0) return 0
    return virtualIndex.floorMod(itemCount)
}

internal fun timelineMoveVirtualIndex(
    currentIndex: Int,
    delta: Int,
    itemCount: Int
): Int {
    val virtualCount = timelineVirtualItemCount(itemCount)
    if (virtualCount <= 1) return 0
    return (currentIndex + delta).coerceIn(1, virtualCount - 2)
}

internal fun timelinePrefetchDayIndices(selectedDayIndex: Int, dayCount: Int): List<Int> {
    if (dayCount <= 0) return emptyList()
    val selected = selectedDayIndex.coerceIn(0, dayCount - 1)
    if (dayCount == 1) return listOf(0)
    return listOf(
        selected,
        (selected - 1).floorMod(dayCount),
        (selected + 1).floorMod(dayCount)
    ).distinct()
}

internal fun timelinePrefetchAnime(
    data: UpdateTimeLine,
    selectedDayIndex: Int,
    maxPerDay: Int = TIMELINE_POSTERS_PER_DAY_PREFETCH
): List<AnimeData> {
    if (maxPerDay <= 0) return emptyList()
    return timelinePrefetchDayIndices(selectedDayIndex, data.timeline.size)
        .asSequence()
        .flatMap { dayIndex -> data.timeline[dayIndex].second.take(maxPerDay).asSequence() }
        .filter { it.id.isNotBlank() }
        .distinctBy { "${it.sourceId}:${it.id}" }
        .toList()
}

internal fun timelinePosterPrefetchUrls(
    data: UpdateTimeLine,
    selectedDayIndex: Int,
    maxPerDay: Int = TIMELINE_POSTERS_PER_DAY_PREFETCH
): List<String> = timelinePrefetchAnime(data, selectedDayIndex, maxPerDay)
    .map(AnimeData::imageUrl)
    .map(String::trim)
    .filter(String::isNotBlank)
    .distinct()

internal fun resolveTimelineSynopsis(original: String, localized: String?): String {
    val readyLocalized = localized.orEmpty().trim()
    if (readyLocalized.isNotBlank() && !readyLocalized.requiresTimelineTranslation()) {
        return readyLocalized
    }
    return original.trim().takeUnless(String::requiresTimelineTranslation).orEmpty()
}

internal fun String.requiresTimelineTranslation(): Boolean {
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

private fun Int.floorMod(divisor: Int): Int = ((this % divisor) + divisor) % divisor
