package com.jing.sakura.auth

internal object AuthEntryPolicy {
    const val MIN_BRAND_DISPLAY_MS = 1_100L

    fun remainingBrandDisplayMs(startedAtMs: Long, nowMs: Long): Long {
        val elapsedMs = (nowMs - startedAtMs).coerceAtLeast(0L)
        return (MIN_BRAND_DISPLAY_MS - elapsedMs).coerceAtLeast(0L)
    }
}
