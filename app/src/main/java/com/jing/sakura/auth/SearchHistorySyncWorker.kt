package com.jing.sakura.auth

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import org.koin.core.context.GlobalContext
import java.util.concurrent.TimeUnit

class SearchHistorySyncWorker(
    appContext: Context,
    workerParameters: WorkerParameters
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result {
        val koin = runCatching { GlobalContext.get() }.getOrNull() ?: return Result.retry()
        val repository = koin.get<AulamaAuthRepository>()
        val accountKey = repository.session.value?.account?.email.orEmpty()
        if (accountKey.isBlank()) return Result.success()

        val queue = koin.get<SearchHistorySyncQueue>()
        var failed = false
        queue.pendingForAccount(accountKey).forEach { mutation ->
            val synced = runCatching {
                when (mutation.type) {
                    SearchHistoryMutationType.UPSERT -> repository.saveSearchHistory(
                        mutation.keyword,
                        mutation.updatedAtEpochMs
                    )
                    SearchHistoryMutationType.DELETE -> repository.deleteSearchHistory(
                        mutation.keyword,
                        mutation.updatedAtEpochMs
                    )
                    SearchHistoryMutationType.CLEAR -> repository.clearSearchHistory(
                        mutation.updatedAtEpochMs
                    )
                }
            }.getOrNull() != null
            if (synced) {
                queue.removeIfCurrent(accountKey, mutation)
            } else {
                failed = true
            }
        }
        return if (failed && repository.session.value != null) Result.retry() else Result.success()
    }
}

object SearchHistorySyncScheduler {
    private const val UNIQUE_WORK = "aulama-search-history-sync"

    fun enqueue(context: Context) {
        val request = OneTimeWorkRequestBuilder<SearchHistorySyncWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            UNIQUE_WORK,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }
}
