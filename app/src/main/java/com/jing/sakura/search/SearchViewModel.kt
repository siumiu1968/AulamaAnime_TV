package com.jing.sakura.search

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import com.jing.sakura.SakuraApplication
import com.jing.sakura.auth.AulamaAuthRepository
import com.jing.sakura.auth.SearchHistoryMutationType
import com.jing.sakura.auth.SearchHistorySyncItem
import com.jing.sakura.auth.SearchHistorySyncQueue
import com.jing.sakura.auth.SearchHistorySyncScheduler
import com.jing.sakura.auth.searchHistoryCacheKey
import com.jing.sakura.room.SearchHistoryDao
import com.jing.sakura.room.SearchHistoryEntity
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicLong

class SearchViewModel(
    private val searchHistoryDao: SearchHistoryDao,
    private val authRepository: AulamaAuthRepository,
    private val syncQueue: SearchHistorySyncQueue,
    val sourceId:String
) : ViewModel() {

    private val accountKey = authRepository.session.value?.account?.email.orEmpty()
    private val cacheKey = searchHistoryCacheKey(accountKey)
    private val mutationVersion = AtomicLong(0L)
    private val flushMutex = Mutex()

    val searchHistoryPager = Pager(config = PagingConfig(pageSize = 10)) {
        searchHistoryDao.queryHistory(cacheKey, SEARCH_HISTORY_LIMIT)
    }.flow

    private var refreshJob: Job? = null

    init { refreshHistory() }

    fun refreshHistory() {
        if (accountKey.isBlank() || refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch(Dispatchers.IO) {
            if (authRepository.session.value?.account?.email != accountKey) return@launch
            val observedVersion = mutationVersion.get()
            flushPendingMutations()
            if (syncQueue.pendingForAccount(accountKey).isNotEmpty()) return@launch
            runCatching { authRepository.fetchSearchHistory() }
                .onFailure { Log.w(SEARCH_HISTORY_SYNC_TAG, "Unable to fetch search history", it) }
                .getOrNull()
                ?.takeIf { observedVersion == mutationVersion.get() && syncQueue.pendingForAccount(accountKey).isEmpty() }
                ?.let(::replaceLocalHistory)
        }
    }

    fun deleteAllHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            val updatedAt = System.currentTimeMillis()
            val version = mutationVersion.incrementAndGet()
            searchHistoryDao.deleteAllHistory(cacheKey)
            enqueueMutation(SearchHistoryMutationType.CLEAR, updatedAtEpochMs = updatedAt)
            flushPendingMutations()
                ?.takeIf { version == mutationVersion.get() }
                ?.let(::replaceLocalHistory)
        }
    }

    fun deleteHistory(keyword: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val normalized = normalizeSearchKeyword(keyword)
            if (normalized.isBlank()) return@launch
            val updatedAt = System.currentTimeMillis()
            val version = mutationVersion.incrementAndGet()
            searchHistoryDao.deleteHistory(cacheKey, searchKeywordKey(normalized))
            enqueueMutation(SearchHistoryMutationType.DELETE, normalized, updatedAt)
            flushPendingMutations()
                ?.takeIf { version == mutationVersion.get() }
                ?.let(::replaceLocalHistory)
        }
    }

    fun saveHistory(keyword: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val normalized = normalizeSearchKeyword(keyword)
            if (normalized.isBlank()) return@launch
            val searchTime = System.currentTimeMillis()
            val version = mutationVersion.incrementAndGet()
            searchHistoryDao.saveHistory(
                SearchHistoryEntity(
                    accountKey = cacheKey,
                    keywordKey = searchKeywordKey(normalized),
                    keyword = normalized,
                    searchTime = searchTime
                )
            )
            searchHistoryDao.trimHistory(cacheKey, SEARCH_HISTORY_LIMIT)
            enqueueMutation(SearchHistoryMutationType.UPSERT, normalized, searchTime)
            flushPendingMutations()
                ?.takeIf { version == mutationVersion.get() }
                ?.let(::replaceLocalHistory)
        }
    }

    private fun enqueueMutation(
        type: SearchHistoryMutationType,
        keyword: String = "",
        updatedAtEpochMs: Long
    ) {
        if (accountKey.isBlank()) return
        syncQueue.enqueue(accountKey, type, keyword, updatedAtEpochMs)
        SearchHistorySyncScheduler.enqueue(SakuraApplication.context)
    }

    private suspend fun flushPendingMutations(): List<SearchHistorySyncItem>? {
        if (accountKey.isBlank()) return null
        return flushMutex.withLock {
            var latestItems: List<SearchHistorySyncItem>? = null
            for (mutation in syncQueue.pendingForAccount(accountKey)) {
                val items = runCatching {
                    when (mutation.type) {
                        SearchHistoryMutationType.UPSERT -> authRepository.saveSearchHistory(
                            mutation.keyword,
                            mutation.updatedAtEpochMs
                        )
                        SearchHistoryMutationType.DELETE -> authRepository.deleteSearchHistory(
                            mutation.keyword,
                            mutation.updatedAtEpochMs
                        )
                        SearchHistoryMutationType.CLEAR -> authRepository.clearSearchHistory(
                            mutation.updatedAtEpochMs
                        )
                    }
                }.onFailure {
                    Log.w(SEARCH_HISTORY_SYNC_TAG, "Unable to sync search history", it)
                }.getOrNull() ?: break
                syncQueue.removeIfCurrent(accountKey, mutation)
                latestItems = items
            }
            latestItems
        }
    }

    private fun replaceLocalHistory(items: List<SearchHistorySyncItem>) {
        val rows = items.take(SEARCH_HISTORY_LIMIT).mapNotNull { item ->
            val normalized = normalizeSearchKeyword(item.keyword)
            if (normalized.isBlank()) null else SearchHistoryEntity(
                accountKey = cacheKey, keywordKey = searchKeywordKey(normalized),
                keyword = normalized, searchTime = item.updatedAtEpochMs
            )
        }
        searchHistoryDao.replaceSnapshot(cacheKey, rows)
    }

    private companion object {
        const val SEARCH_HISTORY_SYNC_TAG = "search-history-sync"
    }
}
