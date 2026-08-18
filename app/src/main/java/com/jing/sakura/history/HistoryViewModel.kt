package com.jing.sakura.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.*
import com.jing.sakura.auth.AulamaAuthRepository
import com.jing.sakura.auth.GuestLibraryStore
import com.jing.sakura.auth.TvLibraryPayload
import com.jing.sakura.auth.toAnimeData
import com.jing.sakura.data.AnimeData
import com.jing.sakura.repo.WebPageRepository
import com.jing.sakura.room.VideoHistoryDao
import com.jing.sakura.room.VideoHistoryEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class HistoryViewModel(
    private val videoHistoryDao: VideoHistoryDao,
    private val repository: WebPageRepository,
    private val authRepository: AulamaAuthRepository,
    private val guestLibraryStore: GuestLibraryStore
) :
    ViewModel() {

    private val _library = MutableStateFlow(TvLibraryPayload())
    val library: StateFlow<TvLibraryPayload> = _library

    private val _libraryLoading = MutableStateFlow(true)
    val libraryLoading: StateFlow<Boolean> = _libraryLoading

    private val _libraryError = MutableStateFlow<String?>(null)
    val libraryError: StateFlow<String?> = _libraryError

    private val _guestMode = MutableStateFlow(authRepository.session.value == null)
    val guestMode: StateFlow<Boolean> = _guestMode

    @OptIn(ExperimentalPagingApi::class)
    val pager = Pager(
        config = PagingConfig(pageSize = 20),
        remoteMediator = HistoryRemoteMediator()
    ) {
        videoHistoryDao.queryHistory()
    }.flow

    init {
        refreshLibrary()
    }

    fun refreshLibrary() {
        viewModelScope.launch(Dispatchers.IO) {
            _libraryLoading.value = true
            _libraryError.value = null
            val isGuest = authRepository.session.value == null
            _guestMode.value = isGuest
            if (isGuest) {
                loadGuestLibrary()
                _libraryLoading.value = false
                return@launch
            }
            runCatching { authRepository.fetchTvLibrary() }
                .onSuccess { _library.value = it }
                .onFailure { _libraryError.value = it.message ?: "未能同步片庫" }
            _libraryLoading.value = false
        }
    }


    fun deleteAllHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            videoHistoryDao.deleteAll()
            if (authRepository.session.value == null) loadGuestLibrary()
        }
    }

    fun getSourceName(sourceId: String): String = repository.requireAnimationSource(sourceId).name

    fun deleteHistoryByAnimeId(animeId: String, sourceId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            videoHistoryDao.deleteHistoryByAnimeId(animeId, sourceId)
            if (authRepository.session.value == null) loadGuestLibrary()
        }
    }

    private fun loadGuestLibrary() {
        val continueWatching = videoHistoryDao.queryRecentHistory(GUEST_HISTORY_LIMIT)
            .map(VideoHistoryEntity::toGuestAnimeData)
        _library.value = TvLibraryPayload(
            continueWatching = continueWatching,
            favorites = guestLibraryStore.favorites.value.map { it.toAnimeData() }
        )
    }

    @OptIn(ExperimentalPagingApi::class)
    private class HistoryRemoteMediator() :
        RemoteMediator<Int, VideoHistoryEntity>() {

        override suspend fun load(
            loadType: LoadType,
            state: PagingState<Int, VideoHistoryEntity>
        ): MediatorResult {
            return MediatorResult.Success(true)
        }
    }

    private companion object {
        const val GUEST_HISTORY_LIMIT = 50
    }
}

private fun VideoHistoryEntity.toGuestAnimeData(): AnimeData = AnimeData(
    id = animeId,
    url = "",
    title = animeName,
    currentEpisode = lastEpisodeName,
    imageUrl = coverUrl,
    sourceId = sourceId
)
