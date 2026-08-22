package com.jing.sakura.repo

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CycaniPlaybackProviderRetryTest {
    @Test
    fun retriesOneTransientFailure() = runBlocking {
        var attempts = 0

        val result = retryPlaybackProvidersOnce(retryDelayMs = 0L) {
            attempts += 1
            if (attempts == 1) throw IllegalStateException("temporary")
            "loaded"
        }

        assertEquals("loaded", result)
        assertEquals(2, attempts)
    }

    @Test
    fun cancellationIsNeverRetried() = runBlocking {
        var attempts = 0
        var cancelled = false

        try {
            retryPlaybackProvidersOnce(retryDelayMs = 0L) {
                attempts += 1
                throw CancellationException("cancelled")
            }
        } catch (_: CancellationException) {
            cancelled = true
        }

        assertTrue(cancelled)
        assertEquals(1, attempts)
    }
}
