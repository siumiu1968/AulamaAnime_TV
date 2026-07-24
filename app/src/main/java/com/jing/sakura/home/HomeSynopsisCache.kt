package com.jing.sakura.home

import android.content.SharedPreferences

internal class HomeSynopsisCache(
    private val preferences: SharedPreferences,
    private val timelinePreferences: SharedPreferences
) {
    fun get(sourceId: String, animeId: String, nowMs: Long = System.currentTimeMillis()): String? {
        val storageKey = homeSynopsisKey(sourceId, animeId)
        val cached = preferences.getString(storageKey, null)
            ?.trim()
            ?.takeIf(String::isNotBlank)
        val savedAt = preferences.getLong(timestampKey(storageKey), 0L)
        if (cached != null && savedAt > 0L && nowMs - savedAt <= MAX_AGE_MS) {
            return cached
        }

        val sharedCached = timelinePreferences.getString("synopsis_$animeId", null)
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?: return null
        put(sourceId, animeId, sharedCached, nowMs)
        return sharedCached
    }

    @Synchronized
    fun put(
        sourceId: String,
        animeId: String,
        synopsis: String,
        nowMs: Long = System.currentTimeMillis()
    ) {
        val value = synopsis.trim()
        if (value.isBlank()) return
        val storageKey = homeSynopsisKey(sourceId, animeId)
        val keys = preferences.getString(INDEX_KEY, "")
            .orEmpty()
            .lineSequence()
            .filter(String::isNotBlank)
            .filterNot { it == storageKey }
            .toMutableList()
            .apply { add(storageKey) }
        val expired = keys.take((keys.size - MAX_ENTRIES).coerceAtLeast(0))
        preferences.edit().apply {
            expired.forEach { key ->
                remove(key)
                remove(timestampKey(key))
            }
            putString(storageKey, value)
            putLong(timestampKey(storageKey), nowMs)
            putString(INDEX_KEY, keys.takeLast(MAX_ENTRIES).joinToString("\n"))
        }.apply()
    }

    private fun timestampKey(storageKey: String): String = "${storageKey}_saved_at"

    private companion object {
        const val INDEX_KEY = "cached_keys"
        const val MAX_ENTRIES = 120
        const val MAX_AGE_MS = 14L * 24L * 60L * 60L * 1_000L
    }
}
