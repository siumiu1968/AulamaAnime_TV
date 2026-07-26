package com.jing.sakura.compose.screen

import kotlin.math.min
import kotlin.math.roundToInt

internal object DeviceLoginLayoutPolicy {
    private const val MIN_QR_SIZE_DP = 160
    private const val MAX_QR_SIZE_DP = 228

    fun qrSizeDp(availableWidthDp: Float, availableHeightDp: Float): Int {
        val widthBound = availableWidthDp.coerceAtLeast(0f) * 0.175f
        val heightBound = availableHeightDp.coerceAtLeast(0f) * 0.31f
        return min(widthBound, heightBound)
            .roundToInt()
            .coerceIn(MIN_QR_SIZE_DP, MAX_QR_SIZE_DP)
    }
}
