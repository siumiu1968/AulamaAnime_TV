package com.jing.sakura.home

internal object TvDisplayDensityPolicy {
    private const val REFERENCE_WIDTH_DP = 960f
    private const val REFERENCE_HEIGHT_DP = 540f

    fun effectiveDensity(
        systemDensity: Float,
        displayWidthPx: Int,
        displayHeightPx: Int
    ): Float {
        if (displayWidthPx <= 0 || displayHeightPx <= 0) return systemDensity

        val widthScale = displayWidthPx / REFERENCE_WIDTH_DP
        val heightScale = displayHeightPx / REFERENCE_HEIGHT_DP
        return minOf(widthScale, heightScale)
    }
}
