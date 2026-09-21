package com.jing.sakura.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import com.jing.sakura.repo.WebPageRepository
import com.jing.sakura.auth.AulamaAuthRepository
import com.jing.sakura.auth.GuestLibraryStore
import com.jing.sakura.auth.FavoritePayload
import com.jing.sakura.data.AnimeData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class SearchResultViewModel(
    val keyword: String,
    private val webPageRepository: WebPageRepository,
    val sourceId: String,
    private val authRepository: AulamaAuthRepository,
    private val guestLibraryStore: GuestLibraryStore
) : ViewModel() {

    val pager = Pager(
        PagingConfig(pageSize = webPageRepository.requireAnimationSource(sourceId).pageSize),
        pagingSourceFactory = {
            AnimeDataPagingSource {
                webPageRepository.searchAnimation(keyword, it, sourceId)
            }
        }
    ).flow.cachedIn(viewModelScope)

    private val favoritesMutex = Mutex()
    private val _favoriteIds = MutableStateFlow<Set<String>>(emptySet())
    val favoriteIds = _favoriteIds.asStateFlow()
    private val _busyIds = MutableStateFlow<Set<String>>(emptySet())
    val busyIds = _busyIds.asStateFlow()
    private val _favoriteError = MutableStateFlow("")
    val favoriteError = _favoriteError.asStateFlow()
    private val _favoritesReady = MutableStateFlow(false)
    val favoritesReady = _favoritesReady.asStateFlow()

    fun refreshFavorites() {
        viewModelScope.launch(Dispatchers.IO) {
            favoritesMutex.withLock {
                runCatching {
                    if (authRepository.session.value == null) guestLibraryStore.favorites.value
                        .filter { it.sourceTypeId == sourceId }.map { it.id }.toSet()
                    else authRepository.fetchFavorites().map { it.id }.toSet()
                }.onSuccess { _favoriteIds.value = it; _favoritesReady.value = true }
                    .onFailure { _favoriteError.value = "未能讀取收藏，請稍後再試" }
            }
        }
    }

    fun toggleFavorite(anime: AnimeData) {
        if (!_favoritesReady.value || anime.id in _busyIds.value) return
        _busyIds.update { it + anime.id }
        viewModelScope.launch(Dispatchers.IO) {
            favoritesMutex.withLock {
                val removing = anime.id in _favoriteIds.value
                val payload = FavoritePayload(id = anime.id, title = anime.title,
                    subtitle = anime.currentEpisode, poster = anime.imageUrl, year = anime.year,
                    summary = anime.description, sourceTypeId = sourceId,
                    providerRating = anime.rating.toDoubleOrNull() ?: 0.0)
                val success = runCatching {
                    if (authRepository.session.value == null) {
                        if (removing) guestLibraryStore.delete(anime.id, sourceId) else guestLibraryStore.save(payload)
                    } else if (removing) authRepository.deleteFavorite(anime.id) else authRepository.saveFavorite(payload)
                }.getOrDefault(false)
                if (success) {
                    _favoriteIds.update { if (removing) it - anime.id else it + anime.id }
                    _favoriteError.value = ""
                } else _favoriteError.value = "收藏更新失敗，請稍後再試"
                _busyIds.update { it - anime.id }
            }
        }
    }

}
