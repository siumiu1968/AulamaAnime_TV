package com.jing.sakura.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer

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

    @Test
    fun forcedProbeDoesNotReuseAnActiveHttp2Route() {
        MockWebServer().use { server ->
            server.protocols = listOf(Protocol.H2_PRIOR_KNOWLEDGE)
            server.enqueue(MockResponse().setBody("first"))
            server.enqueue(MockResponse().setBody("second"))
            val sharedClient = OkHttpClient.Builder()
                .protocols(listOf(Protocol.H2_PRIOR_KNOWLEDGE))
                .build()
            val activeResponse = sharedClient.newCall(
                Request.Builder().url(server.url("/active")).build()
            ).execute()

            try {
                freshRegionRouteClient(sharedClient)
                    .newCall(Request.Builder().url(server.url("/region-probe")).build())
                    .execute()
                    .close()

                assertEquals(0, server.takeRequest().sequenceNumber)
                assertEquals(0, server.takeRequest().sequenceNumber)
            } finally {
                activeResponse.close()
            }
        }
    }

    @Test
    fun staleRouteCannotRestoreARegionBlockAfterTransportReplacement() {
        assertTrue(isCurrentRegionRouteGeneration(responseGeneration = 2L, currentGeneration = 2L))
        assertFalse(isCurrentRegionRouteGeneration(responseGeneration = 1L, currentGeneration = 2L))
    }
}
