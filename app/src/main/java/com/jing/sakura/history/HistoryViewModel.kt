package com.jing.sakura.history

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jing.sakura.SakuraApplication
import androidx.paging.*
import com.jing.sakura.auth.AulamaAuthRepository
import com.jing.sakura.auth.GuestLibraryStore
import com.jing.sakura.auth.TvLibraryPayload
import com.jing.sakura.auth.applyFavoriteNewEpisodeBadges
import com.jing.sakura.auth.toAnimeData
import com.jing.sakura.data.AnimeData
import com.jing.sakura.data.AnimeDetailPageData
import com.jing.sakura.home.HomeSynopsisCache
import com.jing.sakura.home.resolveHeroDescription
import com.jing.sakura.repo.WebPageRepository
import com.jing.sakura.repo.CycaniSource
import com.jing.sakura.room.VideoHistoryDao
import com.jing.sakura.room.VideoHistoryEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.LinkedHashMap
import java.util.concurrent.atomic.AtomicLong

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

    private val _animeDetails = MutableStateFlow<Map<String, AnimeData>>(emptyMap())
    val animeDetails: StateFlow<Map<String, AnimeData>> = _animeDetails

    private val refreshLock = Any()
    private val refreshGeneration = AtomicLong(0L)
    private var refreshJob: Job? = null
    private val detailRequestLock = Any()
    private val focusedDetailGeneration = AtomicLong(0L)
    private var focusedDetailKey: String? = null
    private var focusedDetailJob: Job? = null
    private val detailCache = object : LinkedHashMap<String, AnimeDetailPageData>(
        DETAIL_CACHE_LIMIT,
        0.75f,
        true
    ) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<String, AnimeDetailPageData>?
        ): Boolean = size > DETAIL_CACHE_LIMIT
    }
    private val synopsisCache = HomeSynopsisCache(
        SakuraApplication.context.getSharedPreferences(
            "home_synopsis_cache",
            Context.MODE_PRIVATE
        ),
        SakuraApplication.context.getSharedPreferences(
            "timeline_synopsis_cache",
            Context.MODE_PRIVATE
        )
    )

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
        val job = synchronized(refreshLock) {
            if (refreshJob != null) return
            val generation = refreshGeneration.incrementAndGet()
            viewModelScope.launch(Dispatchers.IO, start = CoroutineStart.LAZY) {
                try {
                    publishRefreshState(generation) {
                        _libraryLoading.value = true
                        _libraryError.value = null
                    }
                    val isGuest = authRepository.session.value == null
                    publishRefreshState(generation) { _guestMode.value = isGuest }
                    val payload = coroutineScope {
                        val libraryRequest = async {
                            if (isGuest) guestLibraryPayload() else authRepository.fetchTvLibrary()
                        }
                        val scheduleRequest = async {
                            runCatching { authRepository.fetchPublicSchedule() }.getOrNull()
                        }
                        val localHistoryRequest = async {
                            videoHistoryDao.queryAllHistoryRecords()
                        }
                        val library = libraryRequest.await()
                        library.copy(
                            favorites = applyFavoriteNewEpisodeBadges(
                                favorites = library.favorites,
                                schedule = scheduleRequest.await(),
                                remoteHistory = library.historyItems,
                                localHistory = localHistoryRequest.await()
                            )
                        )
                    }
                    publishRefreshState(generation) { _library.value = payload }
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    publishRefreshState(generation) {
                        _libraryError.value = error.message ?: "未能同步片庫"
                    }
                } finally {
                    publishRefreshState(generation) { _libraryLoading.value = false }
                    synchronized(refreshLock) {
                        if (refreshGeneration.get() == generation) refreshJob = null
                    }
                }
            }.also { refreshJob = it }
        }
        job.start()
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

    fun loadAnimeDetail(anime: AnimeData) {
        if (anime.id.isBlank() || anime.sourceId.isBlank()) return
        val key = libraryKey(anime.id, anime.sourceId)
        val (generation, previousJob) = synchronized(detailRequestLock) {
            if (focusedDetailKey == key && focusedDetailJob?.isActive == true) return
            val nextGeneration = focusedDetailGeneration.incrementAndGet()
            val jobToCancel = focusedDetailJob
            focusedDetailKey = key
            focusedDetailJob = null
            nextGeneration to jobToCancel
        }
        previousJob?.cancel()
        val cachedDescription = resolveHeroDescription(
            original = anime.description,
            cachedLocalized = synopsisCache.get(anime.sourceId, anime.id)
        )
        if (cachedDescription.isNotBlank()) {
            publishAnimeDescription(anime, cachedDescription, generation)
        }
        synchronized(detailCache) { detailCache[key] }?.let { detail ->
            publishAnimeDetail(anime, detail, generation)
            return
        }
        val job = viewModelScope.launch(Dispatchers.IO, start = CoroutineStart.LAZY) {
            try {
                val source = repository.requireAnimationSource(anime.sourceId)
                if (source is CycaniSource && cachedDescription.isBlank()) {
                    try {
                        val synopsis = source.fetchTimelineSynopsis(anime.id).trim()
                        if (synopsis.isNotBlank() && isFocusedDetailRequest(key, generation)) {
                            synopsisCache.put(anime.sourceId, anime.id, synopsis)
                            publishAnimeDescription(anime, synopsis, generation)
                        }
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        Log.d("library-detail", "片庫簡介快取載入失敗：$key", error)
                    }
                }
                val detail = repository.fetchDetailPage(anime.id, anime.sourceId)
                synchronized(detailRequestLock) {
                    if (!isFocusedDetailRequest(key, generation)) return@launch
                    synchronized(detailCache) { detailCache[key] = detail }
                }
                publishAnimeDetail(anime, detail, generation)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.d("library-detail", "片庫詳情載入失敗：$key", error)
            } finally {
                synchronized(detailRequestLock) {
                    if (isFocusedDetailRequest(key, generation)) focusedDetailJob = null
                }
            }
        }
        synchronized(detailRequestLock) {
            if (isFocusedDetailRequest(key, generation)) {
                focusedDetailJob = job
            } else {
                job.cancel()
            }
        }
        job.start()
    }

    fun cancelAnimeDetailLoad(key: String) {
        val job = synchronized(detailRequestLock) {
            if (focusedDetailKey != key) return
            focusedDetailGeneration.incrementAndGet()
            focusedDetailKey = null
            focusedDetailJob.also { focusedDetailJob = null }
        }
        job?.cancel()
    }

    private fun publishAnimeDetail(
        anime: AnimeData,
        detail: AnimeDetailPageData,
        generation: Long
    ) {
        val key = libraryKey(anime.id, anime.sourceId)
        val detailTags = detail.infoList.joinToString("、")
        val description = resolveHeroDescription(
            original = detail.description.ifBlank { anime.description },
            cachedLocalized = synopsisCache.get(anime.sourceId, anime.id)
        )
        val enriched = anime.copy(
            title = detail.animeName.ifBlank { anime.title },
            imageUrl = detail.imageUrl.ifBlank { anime.imageUrl },
            description = description,
            tags = anime.tags.ifBlank { detailTags }
        )
        synchronized(detailRequestLock) {
            if (!isFocusedDetailRequest(key, generation)) return
            if (description.isNotBlank()) {
                synopsisCache.put(anime.sourceId, anime.id, description)
            }
            publishBoundedAnimeDetail(key, enriched)
        }
    }

    private fun publishAnimeDescription(
        anime: AnimeData,
        description: String,
        generation: Long
    ) {
        val localized = resolveHeroDescription(
            original = description,
            cachedLocalized = description
        )
        if (localized.isBlank()) return
        val key = libraryKey(anime.id, anime.sourceId)
        synchronized(detailRequestLock) {
            if (!isFocusedDetailRequest(key, generation)) return
            val existing = _animeDetails.value[key] ?: anime
            publishBoundedAnimeDetail(key, existing.copy(description = localized))
        }
    }

    private fun publishBoundedAnimeDetail(key: String, anime: AnimeData) {
        _animeDetails.update { current ->
            LinkedHashMap(current).apply {
                remove(key)
                put(key, anime)
                while (size > DETAIL_CACHE_LIMIT) remove(keys.first())
            }
        }
    }

    private fun isFocusedDetailRequest(key: String, generation: Long): Boolean =
        focusedDetailKey == key && focusedDetailGeneration.get() == generation

    private inline fun publishRefreshState(generation: Long, publish: () -> Unit) {
        synchronized(refreshLock) {
            if (refreshGeneration.get() == generation) publish()
        }
    }

    private fun loadGuestLibrary() {
        _library.value = guestLibraryPayload()
    }

    private fun guestLibraryPayload(): TvLibraryPayload {
        val continueWatching = videoHistoryDao.queryRecentHistory(GUEST_HISTORY_LIMIT)
            .map(VideoHistoryEntity::toGuestAnimeData)
        return TvLibraryPayload(
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
        const val DETAIL_CACHE_LIMIT = 24
    }

}

private fun libraryKey(animeId: String, sourceId: String): String = "$sourceId:$animeId"

private fun VideoHistoryEntity.toGuestAnimeData(): AnimeData = AnimeData(
    id = animeId,
    url = "",
    title = animeName,
    currentEpisode = lastEpisodeName,
    imageUrl = coverUrl,
    sourceId = sourceId
)
