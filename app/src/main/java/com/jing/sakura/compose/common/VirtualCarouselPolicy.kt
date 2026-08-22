package com.jing.sakura.compose.common

internal const val VIRTUAL_CAROUSEL_COPIES = 101

internal data class VirtualCarouselMove(
    val targetIndex: Int,
    val logicalIndex: Int,
    val recenterIndex: Int?
)

internal fun virtualCarouselIdentity(rowKey: String, itemKeys: List<String>): String =
    buildString {
        append(rowKey.length)
        append(':')
        append(rowKey)
        itemKeys.forEach { itemKey ->
            append('|')
            append(itemKey.length)
            append(':')
            append(itemKey)
        }
    }

internal fun virtualCarouselItemCount(
    itemCount: Int,
    copies: Int = VIRTUAL_CAROUSEL_COPIES
): Int {
    if (itemCount <= 0 || copies <= 0) return 0
    return (itemCount.toLong() * copies)
        .coerceAtMost(Int.MAX_VALUE.toLong())
        .toInt()
}

internal fun virtualCarouselLogicalIndex(virtualIndex: Int, itemCount: Int): Int {
    if (itemCount <= 0) return 0
    return Math.floorMod(virtualIndex, itemCount)
}

internal fun virtualCarouselCenterIndex(
    itemCount: Int,
    logicalIndex: Int = 0,
    copies: Int = VIRTUAL_CAROUSEL_COPIES
): Int {
    if (itemCount <= 0 || copies <= 0) return 0
    val virtualCount = virtualCarouselItemCount(itemCount, copies)
    val centered = itemCount.toLong() * (copies / 2) +
        virtualCarouselLogicalIndex(logicalIndex, itemCount)
    return centered.coerceIn(0L, (virtualCount - 1).coerceAtLeast(0).toLong()).toInt()
}

internal fun boundedVirtualCarouselMove(
    currentIndex: Int,
    delta: Int,
    itemCount: Int,
    copies: Int = VIRTUAL_CAROUSEL_COPIES
): VirtualCarouselMove {
    val virtualCount = virtualCarouselItemCount(itemCount, copies)
    if (virtualCount <= 1 || itemCount <= 1 || delta == 0) {
        val bounded = currentIndex.coerceIn(0, (virtualCount - 1).coerceAtLeast(0))
        return VirtualCarouselMove(
            targetIndex = bounded,
            logicalIndex = virtualCarouselLogicalIndex(bounded, itemCount),
            recenterIndex = null
        )
    }

    val boundedCurrent = currentIndex.coerceIn(0, virtualCount - 1)
    val target = (boundedCurrent.toLong() + delta)
        .coerceIn(0L, (virtualCount - 1).toLong())
        .toInt()
    val logical = virtualCarouselLogicalIndex(target, itemCount)
    val lowerRecenterBoundary = virtualCount / 4
    val upperRecenterBoundary = virtualCount - lowerRecenterBoundary
    val recenter = if (
        target <= lowerRecenterBoundary ||
        target >= upperRecenterBoundary
    ) {
        virtualCarouselCenterIndex(itemCount, logical, copies)
    } else null

    return VirtualCarouselMove(
        targetIndex = target,
        logicalIndex = logical,
        recenterIndex = recenter?.takeIf { it != target }
    )
}
