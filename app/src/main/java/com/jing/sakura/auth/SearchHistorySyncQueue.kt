package com.jing.sakura.auth

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.jing.sakura.search.normalizeSearchKeyword
import com.jing.sakura.search.searchKeywordKey
import java.security.MessageDigest

enum class SearchHistoryMutationType {
    UPSERT,
    DELETE,
    CLEAR
}

data class QueuedSearchHistoryMutation(
    val accountFingerprint: String,
    val type: SearchHistoryMutationType,
    val keyword: String,
    val updatedAtEpochMs: Long
)

class SearchHistorySyncQueue(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES,
        Context.MODE_PRIVATE
    )
    private val gson = Gson()
    private val queueType =
        object : TypeToken<LinkedHashMap<String, QueuedSearchHistoryMutation>>() {}.type

    @Synchronized
    fun enqueue(
        accountKey: String,
        type: SearchHistoryMutationType,
        keyword: String = "",
        updatedAtEpochMs: Long
    ) {
        if (accountKey.isBlank()) return
        val fingerprint = searchHistoryCacheKey(accountKey)
        val normalizedKeyword = normalizeSearchKeyword(keyword)
        if (type != SearchHistoryMutationType.CLEAR && normalizedKeyword.isBlank()) return
        val mutation = QueuedSearchHistoryMutation(
            accountFingerprint = fingerprint,
            type = type,
            keyword = normalizedKeyword,
            updatedAtEpochMs = updatedAtEpochMs
        )
        val queue = readQueue()
        if (type == SearchHistoryMutationType.CLEAR) {
            queue.entries.removeAll { (_, pending) ->
                pending.accountFingerprint == fingerprint &&
                    pending.updatedAtEpochMs <= updatedAtEpochMs
            }
        }
        val key = entryKey(mutation)
        val existing = queue[key]
        if (existing == null || existing.updatedAtEpochMs <= updatedAtEpochMs) {
            queue[key] = mutation
        }
        writeQueue(
            queue.values
                .sortedByDescending(QueuedSearchHistoryMutation::updatedAtEpochMs)
                .take(MAX_PENDING_ITEMS)
                .associateByTo(linkedMapOf(), ::entryKey)
        )
    }

    @Synchronized
    fun pendingForAccount(accountKey: String): List<QueuedSearchHistoryMutation> {
        if (accountKey.isBlank()) return emptyList()
        val fingerprint = searchHistoryCacheKey(accountKey)
        return readQueue().values
            .filter { it.accountFingerprint == fingerprint }
            .sortedBy(QueuedSearchHistoryMutation::updatedAtEpochMs)
    }

    @Synchronized
    fun removeIfCurrent(accountKey: String, mutation: QueuedSearchHistoryMutation) {
        if (accountKey.isBlank()) return
        val fingerprint = searchHistoryCacheKey(accountKey)
        if (mutation.accountFingerprint != fingerprint) return
        val queue = readQueue()
        val key = entryKey(mutation)
        if (queue[key]?.updatedAtEpochMs != mutation.updatedAtEpochMs) return
        queue.remove(key)
        writeQueue(queue)
    }

    private fun readQueue(): LinkedHashMap<String, QueuedSearchHistoryMutation> {
        val raw = preferences.getString(KEY_QUEUE, null) ?: return linkedMapOf()
        return runCatching {
            gson.fromJson<LinkedHashMap<String, QueuedSearchHistoryMutation>>(raw, queueType)
                ?: linkedMapOf()
        }.getOrElse { linkedMapOf() }
    }

    private fun writeQueue(queue: Map<String, QueuedSearchHistoryMutation>) {
        preferences.edit().putString(KEY_QUEUE, gson.toJson(queue)).commit()
    }

    private fun entryKey(mutation: QueuedSearchHistoryMutation): String = when (mutation.type) {
        SearchHistoryMutationType.CLEAR -> "${mutation.accountFingerprint}:clear"
        else -> "${mutation.accountFingerprint}:keyword:${searchKeywordKey(mutation.keyword)}"
    }

    companion object {
        private const val PREFERENCES = "aulama_search_history_sync"
        private const val KEY_QUEUE = "pending_search_history"
        private const val MAX_PENDING_ITEMS = 50
    }
}

fun searchHistoryCacheKey(accountKey: String): String {
    val normalized = accountKey.trim().lowercase()
    if (normalized.isBlank()) return "guest"
    return MessageDigest.getInstance("SHA-256")
        .digest(normalized.toByteArray())
        .joinToString("") { "%02x".format(it) }
}
