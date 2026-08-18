package com.jing.sakura.auth

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.jing.sakura.data.AnimeData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class GuestLibraryStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val gson = Gson()
    private val favoriteListType = object : TypeToken<List<FavoritePayload>>() {}.type
    private val _favorites = MutableStateFlow(readFavorites())

    val favorites: StateFlow<List<FavoritePayload>> = _favorites

    @Synchronized
    fun contains(animeId: String, sourceId: String): Boolean = _favorites.value.any {
        it.id == animeId && it.sourceTypeId == sourceId
    }

    @Synchronized
    fun save(payload: FavoritePayload): Boolean {
        val now = System.currentTimeMillis()
        val normalized = payload.copy(
            addedAt = payload.addedAt.ifBlank { CloudTimestamp.formatEpochMs(now) },
            updatedAt = CloudTimestamp.formatEpochMs(now)
        )
        val next = buildList {
            add(normalized)
            addAll(_favorites.value.filterNot { favoriteKey(it) == favoriteKey(normalized) })
        }.take(MAX_FAVORITES)
        return persist(next)
    }

    @Synchronized
    fun delete(animeId: String, sourceId: String): Boolean {
        val next = _favorites.value.filterNot {
            it.id == animeId && (sourceId.isBlank() || it.sourceTypeId == sourceId)
        }
        return persist(next)
    }

    private fun readFavorites(): List<FavoritePayload> {
        val raw = preferences.getString(KEY_FAVORITES, null) ?: return emptyList()
        return runCatching {
            gson.fromJson<List<FavoritePayload>>(raw, favoriteListType).orEmpty()
        }.getOrDefault(emptyList())
            .filter { it.id.isNotBlank() && it.title.isNotBlank() && it.sourceTypeId.isNotBlank() }
            .distinctBy(::favoriteKey)
            .take(MAX_FAVORITES)
    }

    private fun persist(next: List<FavoritePayload>): Boolean {
        val committed = preferences.edit()
            .putString(KEY_FAVORITES, gson.toJson(next, favoriteListType))
            .commit()
        if (committed) _favorites.value = next
        return committed
    }

    private fun favoriteKey(payload: FavoritePayload): String =
        "${payload.sourceTypeId.trim()}:${payload.id.trim()}"

    private companion object {
        const val PREFERENCES = "aulama_guest_library"
        const val KEY_FAVORITES = "favorites"
        const val MAX_FAVORITES = 250
    }
}

internal fun FavoritePayload.toAnimeData(): AnimeData = AnimeData(
    id = id,
    url = "",
    title = title,
    currentEpisode = subtitle,
    imageUrl = poster,
    description = summary,
    tags = tags.joinToString("、"),
    sourceId = sourceTypeId,
    year = year
)
