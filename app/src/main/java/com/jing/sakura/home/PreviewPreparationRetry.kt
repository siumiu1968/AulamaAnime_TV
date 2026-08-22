package com.jing.sakura.home

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

internal suspend fun <T> preparePreviewWithRetry(
    maxAttempts: Int = 2,
    retryDelayMs: Long = 350L,
    prepare: suspend () -> T
): T {
    require(maxAttempts > 0)
    var lastFailure: Exception? = null
    repeat(maxAttempts) { attempt ->
        try {
            return prepare()
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            lastFailure = exception
            if (attempt + 1 < maxAttempts && retryDelayMs > 0L) {
                delay(retryDelayMs)
            }
        }
    }
    throw requireNotNull(lastFailure)
}
