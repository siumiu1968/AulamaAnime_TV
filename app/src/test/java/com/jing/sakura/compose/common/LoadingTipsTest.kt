package com.jing.sakura.compose.common

import java.net.URI
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LoadingTipsTest {
    @Test
    fun poolContainsFortySourcedFactsAndTenLightLines() {
        assertEquals(50, loadingTips.size)
        assertEquals(40, loadingTips.count { it.sourceUrl != null })
        assertEquals(10, loadingTips.count { it.sourceUrl == null })
        assertEquals(loadingTips.size, loadingTips.map { it.text }.distinct().size)
    }

    @Test
    fun factsKeepTheirOfficialSourceAndReadableLength() {
        loadingTips.forEach { tip ->
            assertTrue(tip.text.isNotBlank() && tip.text.length <= 80)
            tip.sourceUrl?.let { url ->
                val uri = URI(url)
                assertEquals("https", uri.scheme)
                assertTrue(uri.host in setOf("science.nasa.gov", "oceanservice.noaa.gov"))
                assertTrue(tip.sourceName in setOf("NASA", "NOAA"))
            }
        }
    }

    @Test
    fun consumesEntirePoolBeforeRepeatingAcrossPageVisits() {
        val deck = LoadingTipDeck(random = Random(14))
        val firstPass = List(loadingTips.size) { deck.next() }
        assertEquals(loadingTips.toSet(), firstPass.toSet())
    }

    @Test
    fun reshuffleDoesNotRepeatThePreviousTip() {
        val deck = LoadingTipDeck(random = Random(3))
        val shown = List(loadingTips.size * 4) { deck.next() }
        shown.zipWithNext().forEach { (previous, next) -> assertNotEquals(previous, next) }
    }

    @Test
    fun rotationIntervalLeavesTimeToReadOnTv() {
        assertTrue(LOADING_TIP_INTERVAL_MS in 6_000L..8_000L)
    }
}
