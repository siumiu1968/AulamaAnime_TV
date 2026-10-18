package com.jing.sakura.detail

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.TimeoutException

sealed class DetailRelatedLoadState {
    object Loading : DetailRelatedLoadState()
    object Complete : DetailRelatedLoadState()
    data class Error(val message: String = "相關動漫暫時未能載入") : DetailRelatedLoadState()
}

internal suspend fun <T : Any> fetchDetailSection(
    timeoutMillis: Long = 20_000L,
    fetch: suspend () -> T?
): Result<T> = try {
    withTimeoutOrNull(timeoutMillis) {
        Result.success(checkNotNull(fetch()) { "資料回應不完整" })
    } ?: Result.failure(TimeoutException("資料載入逾時"))
} catch (exception: CancellationException) {
    throw exception
} catch (exception: Exception) {
    Result.failure(exception)
}
