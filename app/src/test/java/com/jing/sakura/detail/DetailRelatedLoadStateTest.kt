package com.jing.sakura.detail

import java.util.concurrent.TimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class DetailRelatedLoadStateTest {
    @Test
    fun successfulRelatedResponseCompletesWithoutDelay() = runBlocking {
        assertEquals("related", fetchDetailSection { "related" }.getOrThrow())
    }

    @Test
    fun missingPayloadIsAnErrorRatherThanAnEmptySuccessfulResponse() = runBlocking {
        assertTrue(fetchDetailSection<String> { null }.isFailure)
    }

    @Test
    fun failedRequestCanBeRetried() = runBlocking {
        val failure = IllegalStateException("offline")
        assertSame(failure, fetchDetailSection<String> { throw failure }.exceptionOrNull())
        assertEquals("ready", fetchDetailSection { "ready" }.getOrThrow())
    }

    @Test
    fun stalledRequestTimesOutInsteadOfLeavingLoadingForever() = runBlocking {
        val result = fetchDetailSection(timeoutMillis = 20) {
            delay(1_000)
            "late"
        }
        assertTrue(result.exceptionOrNull() is TimeoutException)
    }

    @Test
    fun leavingThePagePreservesCancellation() = runBlocking {
        val cancellation = CancellationException("left page")
        var caught: CancellationException? = null
        try {
            fetchDetailSection<String> { throw cancellation }
        } catch (exception: CancellationException) {
            caught = exception
        }
        assertSame(cancellation, caught)
        var returned = false
        val outerResult = withTimeoutOrNull(20) {
            fetchDetailSection(timeoutMillis = 1_000) {
                delay(2_000)
                "late"
            }
            returned = true
        }
        assertNull(outerResult)
        assertEquals(false, returned)
    }
}
