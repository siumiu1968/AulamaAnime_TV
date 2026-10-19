package com.jing.sakura.home

internal const val HOME_ENTRANCE_DURATION_MS = 900

/** One shared timeline keeps the hero, artwork and ambient colour together. */
internal fun homeEntranceFraction(timeline: Float, delay: Float, span: Float): Float {
    val value = ((timeline - delay) / span.coerceAtLeast(0.001f)).coerceIn(0f, 1f)
    return value * value * (3f - 2f * value)
}

internal fun homeCardEntranceDelay(visibleIndex: Int): Float =
    0.30f + visibleIndex.coerceIn(0, 7) * 0.055f
