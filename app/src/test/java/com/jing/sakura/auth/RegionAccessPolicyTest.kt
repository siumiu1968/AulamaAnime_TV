package com.jing.sakura.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RegionAccessPolicyTest {
    @Test
    fun recognisesTheDedicatedRegionHeader() {
        assertTrue(
            isRegionBlockedResponse(
                responseCode = 403,
                markerHeader = "1"
            )
        )
    }

    @Test
    fun recognisesTheExistingHtmlBlockPageWithoutNewHeaders() {
        assertTrue(
            isRegionBlockedResponse(
                responseCode = 403,
                markerHeader = null,
                responseBody = "<html data-block-country=\"JP\">anime-region-403</html>"
            )
        )
        assertEquals(
            "JP",
            blockedCountryCode(
                headerValue = null,
                responseBody = "<html data-block-country=\"jp\"></html>"
            )
        )
    }

    @Test
    fun doesNotTreatOrdinaryApiForbiddenAsARegionBlock() {
        assertFalse(
            isRegionBlockedResponse(
                responseCode = 403,
                markerHeader = null,
                responseBody = "{\"error\":\"forbidden\"}"
            )
        )
    }

    @Test
    fun doesNotTreatAnUnmarkedHtmlForbiddenPageAsARegionBlock() {
        assertFalse(
            isRegionBlockedResponse(
                responseCode = 403,
                markerHeader = null,
                responseBody = "<html><title>Forbidden</title></html>"
            )
        )
    }

    @Test
    fun probeAllowsOnlySuccessfulResponsesAndKeepsFailuresUnavailable() {
        assertEquals(
            RegionAccessProbeResult.Allowed,
            classifyRegionAccessProbe(204, markerHeader = null)
        )
        assertEquals(
            RegionAccessProbeResult.Blocked,
            classifyRegionAccessProbe(403, markerHeader = "1")
        )
        listOf(401, 403, 404, 429, 500, 503).forEach { responseCode ->
            assertEquals(
                RegionAccessProbeResult.Unavailable,
                classifyRegionAccessProbe(responseCode, markerHeader = null)
            )
        }
        val existingBlock = RegionBlockState(countryCode = "JP")
        assertEquals(
            existingBlock,
            regionBlockAfterProbe(existingBlock, RegionAccessProbeResult.Unavailable)
        )
        assertEquals(
            null,
            regionBlockAfterProbe(existingBlock, RegionAccessProbeResult.Allowed)
        )
    }

    @Test
    fun backsOffAfterRepeatedAllowedChecks() {
        assertEquals(5 * 60_000L, regionAccessPollDelayMs(0, blocked = false))
        assertEquals(10 * 60_000L, regionAccessPollDelayMs(1, blocked = false))
        assertEquals(20 * 60_000L, regionAccessPollDelayMs(2, blocked = false))
        assertEquals(30 * 60_000L, regionAccessPollDelayMs(3, blocked = false))
        assertEquals(30_000L, regionAccessPollDelayMs(99, blocked = true))
    }
}
