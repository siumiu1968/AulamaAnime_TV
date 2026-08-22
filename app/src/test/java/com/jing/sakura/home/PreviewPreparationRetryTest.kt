package com.jing.sakura.home

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class PreviewPreparationRetryTest {
    @Test
    fun transientPreparationFailureIsRetriedOnce() = runBlocking {
        var attempts = 0

        val result = preparePreviewWithRetry(retryDelayMs = 0L) {
            attempts += 1
            if (attempts == 1) error("temporary")
            "ready"
        }

        assertEquals("ready", result)
        assertEquals(2, attempts)
    }

    @Test(expected = CancellationException::class)
    fun cancellationIsNeverRetried() = runBlocking {
        var attempts = 0
        try {
            preparePreviewWithRetry<Unit>(retryDelayMs = 0L) {
                attempts += 1
                throw CancellationException("stopped")
            }
        } finally {
            assertEquals(1, attempts)
        }
    }
}
