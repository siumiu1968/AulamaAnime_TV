package com.jing.sakura.home

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jing.sakura.SakuraApplication
import com.jing.sakura.auth.AulamaAuthRepository
import com.jing.sakura.auth.GuestLibraryStore
import com.jing.sakura.auth.applyFavoriteNewEpisodeBadges
import com.jing.sakura.auth.toAnimeData
import com.jing.sakura.data.AnimeData
import com.jing.sakura.data.AnimeDetailPageData
import com.jing.sakura.data.AnimePlayListEpisode
import com.jing.sakura.data.HomePageData
import com.jing.sakura.data.NamedValue
import com.jing.sakura.data.Resource
import com.jing.sakura.player.NavigateToPlayerArg
import com.jing.sakura.repo.AnimationSource
import com.jing.sakura.repo.CycaniSource
import com.jing.sakura.repo.WebPageRepository
import com.jing.sakura.room.VideoHistoryDao
import com.jing.sakura.room.VideoHistoryEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

class HomeViewModel(
    private val repository: WebPageRepository,
    private val authRepository: AulamaAuthRepository,
    private val videoHistoryDao: VideoHistoryDao,
    private val guestLibraryStore: GuestLibraryStore
) : ViewModel() {

    private val _homePageData = MutableStateFlow<Resource<HomePageData>>(Resource.Loading)
    private val _recommendations = MutableStateFlow<List<AnimeData>>(emptyList())
    private val _syncedRows = MutableStateFlow<List<NamedValue<List<AnimeData>>>>(emptyList())
    private val _todayUpdates = MutableStateFlow<List<AnimeData>>(emptyList())
    private val _theaterItems = MutableStateFlow<List<AnimeData>>(emptyList())
    private val _heroDescriptions = MutableStateFlow<Map<String, String>>(emptyMap())
    private val requestedHeroDetails = ConcurrentHashMap.newKeySet<String>()
    private val heroDescriptionSemaphore = Semaphore(2)
    private val heroSynopsisCache = HomeSynopsisCache(
        SakuraApplication.context.getSharedPreferences(
            "home_synopsis_cache",
            Context.MODE_PRIVATE
        ),
        SakuraApplication.context.getSharedPreferences(
            "timeline_synopsis_cache",
            Context.MODE_PRIVATE
        )
    )
    private val _heroPreviewState = MutableStateFlow<HeroPreviewState>(HeroPreviewState.Idle)
    private val remoteHeroPreviewHistory = ConcurrentHashMap<String, HeroPreviewHistory>()

    private val _sp = SakuraApplication.context.getSharedPreferences("source", Context.MODE_PRIVATE)

    var currentSourceId: String = _sp.getString("id", CycaniSource.SOURCE_ID)?.takeIf { id ->
        repository.animationSources.any { it.sourceId == id }
    } ?: CycaniSource.SOURCE_ID
        private set


    private val _currentSource =
        MutableStateFlow(repository.requireAnimationSource(currentSourceId))

    val currentSource: StateFlow<AnimationSource>
        get() = _currentSource

    val homePageData: StateFlow<Resource<HomePageData>>
        get() = _homePageData

    val recommendations: StateFlow<List<AnimeData>>
        get() = _recommendations

    val syncedRows: StateFlow<List<NamedValue<List<AnimeData>>>>
        get() = _syncedRows

    val todayUpdates: StateFlow<List<AnimeData>>
        get() = _todayUpdates

    val theaterItems: StateFlow<List<AnimeData>>
        get() = _theaterItems

    val heroDescriptions: StateFlow<Map<String, String>>
        get() = _heroDescriptions

    val heroPreviewState: StateFlow<HeroPreviewState>
        get() = _heroPreviewState

    @Volatile
    var lastHomePageData: HomePageData? = null
        private set

    private var loadDataJob: Pair<String, Job>? = null
    private val homeLoadGeneration = AtomicLong(0L)
    private var syncedContentRefreshJob: Job? = null
    private val lastSyncedContentStartedAtMs = AtomicLong(0L)
    private var heroPreviewJob: Job? = null
    private var heroPreviewRequestKey: String? = null
    private val heroPreviewGeneration = AtomicLong(0L)

    init {
        loadData(false)
        viewModelScope.launch(Dispatchers.IO) {
            combine(authRepository.session, guestLibraryStore.favorites) { session, favorites ->
                session to favorites
            }.collectLatest { (session, favorites) ->
                if (session == null) {
                    loadGuestContent(favorites)
                } else {
                    loadSyncedContent()
                }
            }
        }
    }

    fun changeSource(newSourceId: String) {
        if (newSourceId == currentSourceId) {
            return
        }
        val source = repository.requireAnimationSource(newSourceId)
        currentSourceId = newSourceId
        _sp.edit().putString("id", newSourceId).apply()
        viewModelScope.launch {
            _currentSource.emit(source)
        }
        loadData(false, saveLastData = false)
    }

    private fun processHomePageData(data: HomePageData): HomePageData {
        val map = mutableMapOf<String, MutableList<AnimeData>>()
        data.seriesList.forEach { (name, videos) ->
            var exists = map[name]
            if (exists == null) {
                exists = mutableListOf()
                map[name] = exists
            }
            exists.addAll(videos)
        }
        return data.copy(
            seriesList = map.entries.map { (name, videos) ->
                NamedValue(
                    name = name,
                    value = videos.distinctBy { it.id }
                )
            }
        )
    }

    fun loadData(silent: Boolean = false, saveLastData: Boolean = true) {
        val sourceId = currentSourceId
        val job = loadDataJob
        if (job != null) {
            if (job.first == sourceId) {
                return
            }
            job.second.cancel()
        }
        val lastValue = _homePageData.value
        val retainedHomeData = if (saveLastData) {
            (lastValue.getOrNull() ?: lastHomePageData)
                ?.takeIf { it.sourceId == sourceId }
        } else null
        val hasDisplayDataAtRequestStart = retainedHomeData != null
        lastHomePageData = retainedHomeData
        val generation = homeLoadGeneration.incrementAndGet()
        loadDataJob = sourceId to viewModelScope.launch(Dispatchers.IO) {
            var publishedPartial = false
            try {
                _homePageData.emit(Resource.Loading(silent = silent))
                val homeData = repository.fetchHomePageProgressively(sourceId) { partialData ->
                    if (shouldPublishProgressiveHomePayload(
                            hasDisplayDataAtRequestStart = hasDisplayDataAtRequestStart,
                            hasRenderableRows = partialData.seriesList.any { it.value.isNotEmpty() },
                            requestGeneration = generation,
                            activeGeneration = homeLoadGeneration.get(),
                            requestSourceId = sourceId,
                            currentSourceId = currentSourceId
                        )
                    ) {
                        _homePageData.emit(Resource.Success(processHomePageData(partialData)))
                        publishedPartial = true
                    }
                }
                if (shouldPublishHomeLoadResult(
                        requestGeneration = generation,
                        activeGeneration = homeLoadGeneration.get(),
                        requestSourceId = sourceId,
                        currentSourceId = currentSourceId
                    )
                ) {
                    _homePageData.emit(Resource.Success(processHomePageData(homeData)))
                }
            } catch (ex: Exception) {
                if (ex is CancellationException) {
                    throw ex
                }
                if (!shouldPublishHomeLoadResult(
                        requestGeneration = generation,
                        activeGeneration = homeLoadGeneration.get(),
                        requestSourceId = sourceId,
                        currentSourceId = currentSourceId
                    )
                ) {
                    return@launch
                }
                if (publishedPartial) {
                    Log.w("homepage", "完整首頁資料載入失敗，保留已載入時間表", ex)
                    return@launch
                }
                lastHomePageData = null
                Log.e("homepage", "请求数据失败", ex)

                val message = "請求資料失敗：" + ex.message
                _homePageData.emit(Resource.Error(message))
            } finally {
                if (homeLoadGeneration.get() == generation) {
                    loadDataJob = null
                }
            }
        }
    }

    fun getAllSources(): List<AnimationSource> = repository.animationSources

    fun refreshSyncedContent() {
        val now = System.currentTimeMillis()
        if (
            syncedContentRefreshJob?.isActive == true ||
            now - lastSyncedContentStartedAtMs.get() < SYNCED_CONTENT_REFRESH_DEBOUNCE_MS
        ) return
        syncedContentRefreshJob = viewModelScope.launch(Dispatchers.IO) {
            if (authRepository.session.value == null) {
                loadGuestContent(guestLibraryStore.favorites.value)
            } else {
                loadSyncedContent()
            }
        }.also { job ->
            job.invokeOnCompletion {
                if (syncedContentRefreshJob === job) syncedContentRefreshJob = null
            }
        }
    }

    fun prefetchHeroDescriptions(items: List<AnimeData>) {
        val candidates = items
            .asSequence()
            .filter { it.id.isNotBlank() && it.sourceId.isNotBlank() }
            .distinctBy(::homeDescriptionKey)
            .take(HERO_DESCRIPTION_PREFETCH_LIMIT)
            .toList()
        hydrateCachedHeroDescriptions(candidates)
        candidates.forEach(::requestHeroDescription)
    }

    fun loadHeroDescription(anime: AnimeData) {
        hydrateCachedHeroDescriptions(listOf(anime))
        requestHeroDescription(anime)
    }

    private fun hydrateCachedHeroDescriptions(items: List<AnimeData>) {
        val cached = items.mapNotNull { anime ->
            val value = heroSynopsisCache.get(anime.sourceId, anime.id)
            resolveHeroDescription(original = "", cachedLocalized = value)
                .takeIf(String::isNotBlank)
                ?.let { homeDescriptionKey(anime) to it }
        }.toMap()
        if (cached.isNotEmpty()) {
            _heroDescriptions.update { current -> current + cached }
        }
    }

    private fun requestHeroDescription(anime: AnimeData) {
        if (
            anime.id.isBlank() ||
            anime.sourceId.isBlank() ||
            !anime.description.requiresLocalizedHeroSynopsis()
        ) {
            return
        }
        val requestKey = homeDescriptionKey(anime)
        if (!_heroDescriptions.value[requestKey].isNullOrBlank()) return
        if (!requestedHeroDetails.add(requestKey)) return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val source = repository.requireAnimationSource(anime.sourceId)
                val synopsis = heroDescriptionSemaphore.withPermit {
                    if (source is CycaniSource) {
                        source.fetchTimelineSynopsis(anime.id)
                    } else {
                        repository.fetchDetailPage(anime.id, anime.sourceId).description
                    }
                }.trim()
                val localized = resolveHeroDescription(original = "", cachedLocalized = synopsis)
                if (localized.isNotBlank()) {
                    heroSynopsisCache.put(anime.sourceId, anime.id, localized)
                    _heroDescriptions.update { current -> current + (requestKey to localized) }
                } else if (synopsis.isBlank()) {
                    requestedHeroDetails.remove(requestKey)
                }
            } catch (error: CancellationException) {
                requestedHeroDetails.remove(requestKey)
                throw error
            } catch (error: Exception) {
                requestedHeroDetails.remove(requestKey)
                Log.d("home-synopsis", "預取中文簡介失敗：$requestKey", error)
            }
        }
    }

    fun replaceRemoteHeroPreviewHistory(items: List<HeroPreviewHistory>) {
        remoteHeroPreviewHistory.clear()
        for (item in items) {
            val key = previewRequestKey(item.animeId, item.sourceId)
            val current = remoteHeroPreviewHistory[key]
            if (current == null || item.updatedAtMs > current.updatedAtMs) {
                remoteHeroPreviewHistory[key] = item
            }
        }
    }

    fun prepareHeroPreview(anime: AnimeData, force: Boolean = false) {
        if (anime.id.isBlank() || anime.sourceId.isBlank()) {
            cancelHeroPreview()
            return
        }
        val requestKey = previewRequestKey(anime.id, anime.sourceId)
        val currentState = _heroPreviewState.value
        if (!force && heroPreviewRequestKey == requestKey && heroPreviewJob?.isActive == true) {
            return
        }
        if (!force && currentState is HeroPreviewState.Ready &&
            currentState.spec.navigateToPlayerArg.animeId == anime.id &&
            currentState.spec.navigateToPlayerArg.sourceId == anime.sourceId
        ) {
            return
        }

        val generation = heroPreviewGeneration.incrementAndGet()
        heroPreviewJob?.cancel()
        heroPreviewRequestKey = requestKey
        _heroPreviewState.value = HeroPreviewState.Loading(requestKey)
        heroPreviewJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val spec = preparePreviewWithRetry {
                    val detail = repository.fetchDetailPage(anime.id, anime.sourceId)
                    val selection = selectPreviewEpisode(detail, anime.id, anime.sourceId)
                    val response = repository.fetchVideoUrl(
                        episodeId = selection.episode.episodeId,
                        sourceId = anime.sourceId,
                        animeId = anime.id
                    )
                    val video = when (response) {
                        is Resource.Success -> response.data
                        is Resource.Error -> throw PreviewPreparationException(response.message)
                        is Resource.Loading -> throw PreviewPreparationException("預覽影片尚未準備好")
                    }
                    val playerArg = NavigateToPlayerArg(
                        animeName = detail.animeName.ifBlank { anime.title },
                        animeId = anime.id,
                        coverUrl = detail.imageUrl.ifBlank { anime.imageUrl },
                        playIndex = selection.playIndex,
                        playlist = selection.playlist,
                        sourceId = anime.sourceId,
                        playlists = detail.playLists,
                        playlistIndex = selection.playlistIndex
                    )
                    HeroPreviewSpec(
                        key = "$requestKey:${selection.episode.episodeId}:$generation",
                        url = video.url,
                        headers = video.headers,
                        startPositionMs = selection.startPositionMs,
                        posterUrl = playerArg.coverUrl,
                        navigateToPlayerArg = playerArg
                    )
                }
                publishHeroPreview(
                    generation,
                    HeroPreviewState.Ready(spec)
                )
            } catch (exception: Exception) {
                if (exception is CancellationException) throw exception
                Log.e("hero-preview", "準備首頁預覽失敗", exception)
                publishHeroPreview(
                    generation,
                    HeroPreviewState.Error(
                        requestKey = requestKey,
                        message = exception.message ?: "預覽影片載入失敗"
                    )
                )
            } finally {
                if (heroPreviewGeneration.get() == generation) {
                    heroPreviewJob = null
                    heroPreviewRequestKey = null
                }
            }
        }
    }

    fun cancelHeroPreview() {
        heroPreviewGeneration.incrementAndGet()
        heroPreviewJob?.cancel()
        heroPreviewJob = null
        heroPreviewRequestKey = null
        _heroPreviewState.value = HeroPreviewState.Idle
    }

    private fun publishHeroPreview(generation: Long, state: HeroPreviewState) {
        if (heroPreviewGeneration.get() == generation) {
            _heroPreviewState.value = state
        }
    }

    private fun selectPreviewEpisode(
        detail: AnimeDetailPageData,
        animeId: String,
        sourceId: String
    ): PreviewEpisodeSelection {
        val remoteHistory = remoteHeroPreviewHistory[previewRequestKey(animeId, sourceId)]
        selectHistoryEpisode(
            detail = detail,
            episodeId = remoteHistory?.episodeId,
            episodeIndex = remoteHistory?.episodeIndex,
            positionMs = remoteHistory?.positionMs
        )?.let { return it }

        val localHistory = videoHistoryDao.queryLastHistoryOfAnimeId(animeId, sourceId)
        selectHistoryEpisode(
            detail = detail,
            episodeId = localHistory?.episodeId,
            episodeIndex = null,
            positionMs = localHistory?.lastPlayTime
        )?.let { return it }

        val defaultPlaylist = detail.playLists
            .getOrNull(detail.defaultPlayListIndex)
            ?.takeIf { it.episodeList.isNotEmpty() }
            ?: detail.playLists.firstOrNull { it.defaultPlayList && it.episodeList.isNotEmpty() }
            ?: detail.playLists.firstOrNull { it.episodeList.isNotEmpty() }
            ?: throw PreviewPreparationException("未有可播放集數")
        return PreviewEpisodeSelection(
            playlistIndex = detail.playLists.indexOf(defaultPlaylist),
            playlist = defaultPlaylist.episodeList,
            playIndex = 0,
            episode = defaultPlaylist.episodeList.first(),
            startPositionMs = 0L
        )
    }

    private fun selectHistoryEpisode(
        detail: AnimeDetailPageData,
        episodeId: String?,
        episodeIndex: Int?,
        positionMs: Long?
    ): PreviewEpisodeSelection? {
        if (!episodeId.isNullOrBlank()) {
            detail.playLists.forEachIndexed { playlistIndex, playlist ->
                val playIndex = playlist.episodeList.indexOfFirst { it.episodeId == episodeId }
                if (playIndex >= 0) {
                    return PreviewEpisodeSelection(
                        playlistIndex = playlistIndex,
                        playlist = playlist.episodeList,
                        playIndex = playIndex,
                        episode = playlist.episodeList[playIndex],
                        startPositionMs = positionMs?.coerceAtLeast(0L) ?: 0L
                    )
                }
            }
        }
        val fallbackPlaylist = detail.playLists
            .getOrNull(detail.defaultPlayListIndex)
            ?.takeIf { it.episodeList.isNotEmpty() }
            ?: detail.playLists.firstOrNull { it.defaultPlayList && it.episodeList.isNotEmpty() }
            ?: detail.playLists.firstOrNull { it.episodeList.isNotEmpty() }
            ?: return null
        val fallbackIndex = episodeIndex
            ?.coerceIn(0, fallbackPlaylist.episodeList.lastIndex)
            ?: return null
        return PreviewEpisodeSelection(
            playlistIndex = detail.playLists.indexOf(fallbackPlaylist),
            playlist = fallbackPlaylist.episodeList,
            playIndex = fallbackIndex,
            episode = fallbackPlaylist.episodeList[fallbackIndex],
            startPositionMs = positionMs?.coerceAtLeast(0L) ?: 0L
        )
    }

    private suspend fun loadSyncedContent() {
        lastSyncedContentStartedAtMs.set(System.currentTimeMillis())
        val homeResult = runCatching { authRepository.fetchTvHome() }
        homeResult
            .onSuccess { payload ->
                _recommendations.value = payload.recommendations
                _todayUpdates.value = payload.todayUpdates
                _theaterItems.value = payload.theaterItems
            }
            .onFailure { Log.e("tv-home-sync", "載入首頁同步內容失敗", it) }
        val schedule = homeResult.getOrNull()?.schedule
            ?: runCatching { authRepository.fetchPublicSchedule() }.getOrNull()
        val localHistory = videoHistoryDao.queryAllHistoryRecords()

        runCatching { authRepository.fetchTvLibrary() }
            .onSuccess { payload ->
                replaceRemoteHeroPreviewHistory(
                    payload.historyItems.mapIndexed { index, history ->
                        HeroPreviewHistory(
                            animeId = history.animeId.ifBlank { history.anime.id },
                            sourceId = history.sourceTypeId.ifBlank { history.anime.sourceId },
                            episodeId = history.episodeId,
                            episodeIndex = history.episodeIndex,
                            positionMs = history.currentTimeSeconds.toPositionMs(),
                            updatedAtMs = history.updatedAtEpochMs.coerceAtLeast(
                                (payload.historyItems.size - index).toLong()
                            )
                        )
                    }
                )
                val favorites = applyFavoriteNewEpisodeBadges(
                    favorites = payload.favorites,
                    schedule = schedule,
                    remoteHistory = payload.historyItems,
                    localHistory = localHistory
                )
                _syncedRows.value = buildList {
                    if (payload.continueWatching.isNotEmpty()) {
                        add(NamedValue("繼續觀看", payload.continueWatching))
                    }
                    if (favorites.isNotEmpty()) {
                        add(NamedValue("我的收藏", favorites))
                    }
                }
            }
            .onFailure { Log.e("tv-library-sync", "載入帳戶收藏及紀錄失敗", it) }
    }

    private suspend fun loadGuestContent(favorites: List<com.jing.sakura.auth.FavoritePayload>) {
        lastSyncedContentStartedAtMs.set(System.currentTimeMillis())
        replaceRemoteHeroPreviewHistory(emptyList())
        _recommendations.value = emptyList()
        _todayUpdates.value = emptyList()
        val schedule = runCatching { authRepository.fetchPublicSchedule() }.getOrNull()
        val localHistory = videoHistoryDao.queryAllHistoryRecords()
        val recent = videoHistoryDao.queryRecentHistory(GUEST_CONTINUE_WATCHING_LIMIT)
            .map(VideoHistoryEntity::toLocalAnimeData)
        val favoriteAnime = applyFavoriteNewEpisodeBadges(
            favorites = favorites.map { it.toAnimeData() },
            schedule = schedule,
            remoteHistory = emptyList(),
            localHistory = localHistory
        )
        _syncedRows.value = buildList {
            if (recent.isNotEmpty()) add(NamedValue("繼續觀看", recent))
            favoriteAnime
                .takeIf(List<AnimeData>::isNotEmpty)
                ?.let { add(NamedValue("我的收藏", it)) }
        }
        runCatching { authRepository.fetchPublicTheaterItems() }
            .onSuccess { _theaterItems.value = it }
            .onFailure { Log.e("tv-theater-sync", "載入公開劇場版目錄失敗", it) }
    }

    private fun clearSyncedContent() {
        replaceRemoteHeroPreviewHistory(emptyList())
        _recommendations.value = emptyList()
        _syncedRows.value = emptyList()
        _todayUpdates.value = emptyList()
        _theaterItems.value = emptyList()
    }

    private data class PreviewEpisodeSelection(
        val playlistIndex: Int,
        val playlist: List<AnimePlayListEpisode>,
        val playIndex: Int,
        val episode: AnimePlayListEpisode,
        val startPositionMs: Long
    )

    private class PreviewPreparationException(message: String) : Exception(message)

    private fun previewRequestKey(animeId: String, sourceId: String): String =
        "$sourceId:$animeId"

    private fun Double.toPositionMs(): Long =
        takeIf { it.isFinite() && it > 0.0 }
            ?.times(1_000.0)
            ?.coerceAtMost(Long.MAX_VALUE.toDouble())
            ?.toLong()
            ?: 0L

    private companion object {
        const val HERO_DESCRIPTION_PREFETCH_LIMIT = 12
        const val GUEST_CONTINUE_WATCHING_LIMIT = 24
        const val SYNCED_CONTENT_REFRESH_DEBOUNCE_MS = 750L
    }

}

private fun VideoHistoryEntity.toLocalAnimeData(): AnimeData = AnimeData(
    id = animeId,
    url = "",
    title = animeName,
    currentEpisode = lastEpisodeName,
    imageUrl = coverUrl,
    sourceId = sourceId
)
