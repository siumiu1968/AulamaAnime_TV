package com.jing.sakura.auth

internal const val REGION_BLOCK_HEADER = "X-Aulama-Region-Blocked"
internal const val REGION_COUNTRY_HEADER = "X-Aulama-Region-Country"

data class RegionBlockState(
    val countryCode: String = ""
)

enum class RegionAccessProbeResult {
    Allowed,
    Blocked,
    Unavailable
}

internal fun isRegionBlockedResponse(
    responseCode: Int,
    markerHeader: String?,
    responseBody: String = ""
): Boolean {
    if (responseCode != 403) return false
    if (markerHeader == "1") return true
    return responseBody.contains("data-block-country=", ignoreCase = true) ||
        responseBody.contains("anime-region-403", ignoreCase = true)
}

internal fun classifyRegionAccessProbe(
    responseCode: Int,
    markerHeader: String?,
    responseBody: String = ""
): RegionAccessProbeResult = when {
    isRegionBlockedResponse(responseCode, markerHeader, responseBody) ->
        RegionAccessProbeResult.Blocked
    responseCode in 200..299 -> RegionAccessProbeResult.Allowed
    else -> RegionAccessProbeResult.Unavailable
}

internal fun regionBlockAfterProbe(
    current: RegionBlockState?,
    result: RegionAccessProbeResult
): RegionBlockState? = if (result == RegionAccessProbeResult.Allowed) null else current

internal fun blockedCountryCode(headerValue: String?, responseBody: String): String {
    val headerCountry = headerValue.orEmpty().trim().uppercase()
    if (headerCountry.matches(Regex("[A-Z]{2}"))) return headerCountry
    return Regex("data-block-country=[\\\"']([A-Za-z]{2})[\\\"']", RegexOption.IGNORE_CASE)
        .find(responseBody)
        ?.groupValues
        ?.getOrNull(1)
        .orEmpty()
        .uppercase()
}

internal fun regionAccessPollDelayMs(allowedStreak: Int, blocked: Boolean): Long {
    if (blocked) return 30_000L
    return when (allowedStreak.coerceAtLeast(0)) {
        0 -> 5 * 60_000L
        1 -> 10 * 60_000L
        2 -> 20 * 60_000L
        else -> 30 * 60_000L
    }
}
