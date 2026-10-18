package com.jing.sakura.detail

import android.util.Log
import androidx.annotation.MainThread
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jing.sakura.auth.AnimeRatingPayload
import com.jing.sakura.auth.AulamaAuthRepository
import com.jing.sakura.auth.FavoritePayload
import com.jing.sakura.auth.GuestLibraryStore
import com.jing.sakura.auth.TvHistoryItem
import com.jing.sakura.auth.TvAnimeDetailPayload
import com.jing.sakura.data.AnimeData
import com.jing.sakura.data.AnimeDetailPageData
import com.jing.sakura.data.Resource
import com.jing.sakura.home.HeroPreviewSpec
import com.jing.sakura.home.HeroPreviewState
import com.jing.sakura.player.NavigateToPlayerArg
import com.jing.sakura.player.PlaybackCompletionPolicy
import com.jing.sakura.repo.selectNonJapaneseSynopsis
import com.jing.sakura.repo.shouldFetchSynopsisEnrichment
import com.jing.sakura.repo.WebPageRepository
import com.jing.sakura.room.VideoHistoryDao
import com.jing.sakura.room.VideoHistoryEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Collections

class DetailPageViewModel constructor(
    private val animeId: String,
    private val repository: WebPageRepository,
    private val videoHistoryDao: VideoHistoryDao,
    private val authRepository: AulamaAuthRepository,
    private val guestLibraryStore: GuestLibraryStore,
    val sourceId: String,
    private val detailCache: DetailPageCache,
    private val expectedEpisodes: Int = 0
) : ViewModel() {

    private var _detailPageData = MutableStateFlow<Resource<AnimeDetailPageData>>(
        detailCache.peek(sourceId, animeId)?.takeUnless { it.isBehind(expectedEpisodes) }
            ?.let { Resource.Success(it.detail) } ?: Resource.Loading
    )

    val detailPageData: StateFlow<Resource<AnimeDetailPageData>>
        get() = _detailPageData

    private var _latestProgress = MutableStateFlow<Resource<VideoHistoryEntity>>(Resource.Loading)

    val latestProgress: StateFlow<Resource<VideoHistoryEntity>>
        get() = _latestProgress

    private var loadDataJob: Job? = null
    private var relatedRetryJob: Job? = null
    private var historyRefreshJob: Job? = null

    private val _relatedLoadState = MutableStateFlow<DetailRelatedLoadState>(
        if (authRepository.session.value != null &&
            detailCache.peek(sourceId, animeId)?.needsEnrichment(System.currentTimeMillis()) != false
        ) DetailRelatedLoadState.Loading else DetailRelatedLoadState.Complete
    )
    val relatedLoadState: StateFlow<DetailRelatedLoadState> = _relatedLoadState

    private val _episodeProgress = MutableStateFlow<Map<Int, Float>>(emptyMap())
    /** Watched fraction per episode number, for the progress fill on each episode tile. */
    val episodeProgress: StateFlow<Map<Int, Float>> = _episodeProgress
    @Volatile
    private var remoteHistoryItem: TvHistoryItem? = null

    private val _ratingState = MutableStateFlow(DetailRatingState())
    val ratingState: StateFlow<DetailRatingState> = _ratingState

    private val _favoriteUiState = MutableStateFlow(FavoriteUiState())
    val favoriteUiState: StateFlow<FavoriteUiState> = _favoriteUiState

    private val favoriteErrorChannel = Channel<String>(Channel.BUFFERED)
    val favoriteErrors = favoriteErrorChannel.receiveAsFlow()

    private var favoriteJob: Job? = null
    private var ratingJob: Job? = null

    private val _relatedDescriptions = MutableStateFlow<Map<String, String>>(emptyMap())
    val relatedDescriptions: StateFlow<Map<String, String>> = _relatedDescriptions
    private val requestedRelatedDescriptions =
        Collections.synchronizedSet(mutableSetOf<String>())

    private val _relatedPreviewState = MutableStateFlow<HeroPreviewState>(HeroPreviewState.Idle)
    val relatedPreviewState: StateFlow<HeroPreviewState> = _relatedPreviewState
    private var relatedPreviewJob: Job? = null
    private var relatedPreviewRequestKey: String? = null
    private val relatedPreviewOwnership = DetailRelatedPreviewRequestOwnership()

    init {
        loadData()
        loadFavoriteState()
        loadRating()
    }

    fun loadData() {
        loadDataJob?.cancel()
        relatedRetryJob?.cancel()
        loadDataJob = viewModelScope.launch(Dispatchers.IO) {
            val cached = detailCache.get(sourceId, animeId)
            val needsCoreRefresh = cached == null || cached.isBehind(expectedEpisodes)
            val needsRelatedRefresh = authRepository.session.value != null &&
                cached?.needsEnrichment(System.currentTimeMillis()) != false
            _relatedLoadState.value = if (needsRelatedRefresh) DetailRelatedLoadState.Loading
                else DetailRelatedLoadState.Complete
            var visibleData = if (cached?.isBehind(expectedEpisodes) == true) null
                else cached?.detail ?: _detailPageData.value.getOrNull()
            if (visibleData != null) _detailPageData.emit(Resource.Success(visibleData))
            else _detailPageData.emit(Resource.Loading)
            _latestProgress.emit(Resource.Loading)
            try {
                val localHistoryJob = async {
                    videoHistoryDao.queryLastHistoryOfAnimeId(animeId, sourceId)
                }
                launch {
                    localHistoryJob.await()?.let {
                        _latestProgress.emit(Resource.Success(it))
                    }
                }
                val cloudHistoryJob = async {
                    if (authRepository.session.value == null) {
                        null
                    } else {
                        runCatching { authRepository.fetchTvHistoryItem(animeId) }
                            .onFailure { if (it is CancellationException) throw it }
                            .getOrNull()
                    }
                }
                val cloudDetailJob = async(start = CoroutineStart.LAZY) {
                    if (needsRelatedRefresh) fetchDetailSection {
                        authRepository.fetchTvAnimeDetail(animeId)
                            .takeIf { it.catalogItem != null }
                    } else null
                }
                val freshSourceData = if (needsCoreRefresh) repository.fetchDetailPage(animeId, sourceId)
                    else checkNotNull(cached).detail
                val coreCheckedAt = if (needsCoreRefresh) System.currentTimeMillis() else checkNotNull(cached).savedAt
                val sourceData = cached?.mergeCore(freshSourceData, sourceId) ?: freshSourceData
                val localHistory = localHistoryJob.await()
                val coreRelatedReady = needsCoreRefresh && freshSourceData.relatedReady
                val relatedCheckedAt = if (coreRelatedReady) System.currentTimeMillis()
                    else cached?.takeUnless { it.relatedVersion == 0 }?.enrichedAt ?: 0L
                if (needsCoreRefresh) detailCache.put(sourceId, sourceData, relatedCheckedAt, coreCheckedAt)
                if (coreRelatedReady) {
                    _relatedLoadState.value = DetailRelatedLoadState.Complete
                    cloudDetailJob.cancel()
                } else cloudDetailJob.start()
                visibleData = sourceData
                _detailPageData.emit(
                    Resource.Success(
                        sourceData.copy(lastPlayEpisodePosition = sourceData.positionFor(localHistory?.episodeId))
                    )
                )
                refreshEpisodeProgress()
                // Watch state is independent of the slower public recommendation enrichment.
                launch {
                    val history = applyCloudHistory(sourceData, cloudHistoryJob.await())
                    _detailPageData.update { current ->
                        current.getOrNull()?.let { data ->
                            Resource.Success(data.copy(lastPlayEpisodePosition = data.positionFor(history?.episodeId)))
                        } ?: current
                    }
                }
                if (!coreRelatedReady) cloudDetailJob.await()?.let { result ->
                    applyRelatedEnrichment(
                        result = result,
                        fallbackRelated = if (needsCoreRefresh) freshSourceData.otherAnimeList else emptyList(),
                        coreCheckedAt = coreCheckedAt
                    )
                }
            } catch (ex: Exception) {
                if (ex is CancellationException) {
                    throw ex
                }
                Log.e("homepage", "请求数据失败", ex)

                val message = "請求資料失敗：" + ex.message
                if (visibleData == null) _detailPageData.emit(Resource.Error(message))
                if (_relatedLoadState.value is DetailRelatedLoadState.Loading) {
                    _relatedLoadState.value = DetailRelatedLoadState.Error()
                }
            }
        }
    }

    fun retryRelated() {
        if (_relatedLoadState.value !is DetailRelatedLoadState.Error || relatedRetryJob?.isActive == true) return
        val detail = _detailPageData.value.getOrNull() ?: return
        _relatedLoadState.value = DetailRelatedLoadState.Loading
        relatedRetryJob = viewModelScope.launch(Dispatchers.IO) {
            val cached = detailCache.get(sourceId, animeId)
            applyRelatedEnrichment(
                result = fetchDetailSection {
                    authRepository.fetchTvAnimeDetail(animeId).takeIf { it.catalogItem != null }
                },
                fallbackRelated = detail.otherAnimeList,
                coreCheckedAt = cached?.savedAt ?: System.currentTimeMillis()
            )
        }
    }

    private fun applyRelatedEnrichment(
        result: Result<TvAnimeDetailPayload>,
        fallbackRelated: List<AnimeData>,
        coreCheckedAt: Long
    ) {
        val cloudDetail = result.getOrNull()
        if (cloudDetail == null) {
            _relatedLoadState.value = DetailRelatedLoadState.Error()
            return
        }
        _detailPageData.update { current ->
            current.getOrNull()?.let { data ->
                Resource.Success(data.copy(otherAnimeList = mergeRelatedAnime(
                    cloud = cloudDetail.related,
                    source = fallbackRelated,
                    fallbackSourceId = sourceId
                ), relatedReady = true))
            } ?: current
        }
        _detailPageData.value.getOrNull()?.let { data ->
            detailCache.put(sourceId, data, System.currentTimeMillis(), coreCheckedAt)
        }
        _relatedLoadState.value = DetailRelatedLoadState.Complete
    }

    fun loadRelatedDescription(anime: AnimeData) {
        val key = relatedAnimeKey(anime, sourceId)
        if (
            !shouldFetchSynopsisEnrichment(anime.description) ||
            !_relatedDescriptions.value[key].isNullOrBlank() ||
            !requestedRelatedDescriptions.add(key)
        ) {
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            val relatedSourceId = anime.sourceId.ifBlank { sourceId }
            val description = selectNonJapaneseSynopsis(runCatching {
                detailCache.get(relatedSourceId, anime.id)?.detail?.description
                    ?: repository.fetchDetailPage(anime.id, relatedSourceId).also {
                        detailCache.put(relatedSourceId, it)
                    }.description
            }.onFailure { if (it is CancellationException) throw it }.getOrNull())

            if (description.isNotBlank()) {
                _relatedDescriptions.value = _relatedDescriptions.value + (key to description)
            } else {
                requestedRelatedDescriptions.remove(key)
            }
        }
    }

    @MainThread
    fun prepareRelatedPreview(anime: AnimeData) {
        val relatedSourceId = anime.sourceId.ifBlank { sourceId }
        if (anime.id.isBlank() || relatedSourceId.isBlank()) {
            cancelRelatedPreview()
            return
        }
        val requestKey = "$relatedSourceId:${anime.id}"
        val currentState = _relatedPreviewState.value
        if (relatedPreviewRequestKey == requestKey && relatedPreviewJob?.isActive == true) return
        if (
            currentState is HeroPreviewState.Ready &&
            currentState.spec.navigateToPlayerArg.animeId == anime.id &&
            currentState.spec.navigateToPlayerArg.sourceId == relatedSourceId
        ) {
            return
        }

        val generation = relatedPreviewOwnership.startRequest()
        relatedPreviewJob?.cancel()
        relatedPreviewRequestKey = requestKey
        _relatedPreviewState.value = HeroPreviewState.Loading(requestKey)
        relatedPreviewJob = viewModelScope.launch {
            try {
                val spec = buildRelatedPreviewSpec(
                    anime = anime,
                    relatedSourceId = relatedSourceId,
                    requestKey = requestKey,
                    generation = generation
                )
                publishRelatedPreview(
                    generation = generation,
                    state = HeroPreviewState.Ready(spec)
                )
            } catch (exception: Exception) {
                if (exception is CancellationException) throw exception
                Log.e("related-preview", "準備相關動漫預覽失敗", exception)
                publishRelatedPreview(
                    generation = generation,
                    state = HeroPreviewState.Error(
                        requestKey = requestKey,
                        message = exception.message ?: "預覽影片載入失敗"
                    )
                )
            } finally {
                if (relatedPreviewOwnership.owns(generation)) {
                    relatedPreviewJob = null
                    relatedPreviewRequestKey = null
                }
            }
        }
    }

    @MainThread
    fun cancelRelatedPreview() {
        relatedPreviewOwnership.invalidate()
        relatedPreviewJob?.cancel()
        relatedPreviewJob = null
        relatedPreviewRequestKey = null
        _relatedPreviewState.value = HeroPreviewState.Idle
    }

    private fun publishRelatedPreview(generation: Long, state: HeroPreviewState) {
        if (relatedPreviewOwnership.owns(generation)) {
            _relatedPreviewState.value = state
        }
    }

    private suspend fun buildRelatedPreviewSpec(
        anime: AnimeData,
        relatedSourceId: String,
        requestKey: String,
        generation: Long
    ): HeroPreviewSpec = withContext(Dispatchers.IO) {
        val detail = repository.fetchDetailPage(anime.id, relatedSourceId)
        val playlist = detail.playLists
            .getOrNull(detail.defaultPlayListIndex)
            ?.takeIf { it.episodeList.isNotEmpty() }
            ?: detail.playLists.firstOrNull {
                it.defaultPlayList && it.episodeList.isNotEmpty()
            }
            ?: detail.playLists.firstOrNull { it.episodeList.isNotEmpty() }
            ?: throw RelatedPreviewPreparationException("未有可播放集數")
        val playlistIndex = detail.playLists.indexOf(playlist)
        val episode = playlist.episodeList.first()
        val response = repository.fetchVideoUrl(
            episodeId = episode.episodeId,
            sourceId = relatedSourceId,
            animeId = anime.id
        )
        val video = when (response) {
            is Resource.Success -> response.data
            is Resource.Error -> throw RelatedPreviewPreparationException(response.message)
            is Resource.Loading -> throw RelatedPreviewPreparationException("預覽影片尚未準備好")
        }
        val playerArg = NavigateToPlayerArg(
            animeName = detail.animeName.ifBlank { anime.title },
            animeId = anime.id,
            coverUrl = detail.imageUrl.ifBlank { anime.imageUrl },
            playIndex = 0,
            playlist = playlist.episodeList,
            sourceId = relatedSourceId,
            playlists = detail.playLists,
            playlistIndex = playlistIndex
        )
        HeroPreviewSpec(
            key = "$requestKey:${episode.episodeId}:$generation",
            url = video.url,
            headers = video.headers,
            startPositionMs = 0L,
            posterUrl = playerArg.coverUrl,
            navigateToPlayerArg = playerArg
        )
    }

    fun fetchHistory() {
        historyRefreshJob?.cancel()
        historyRefreshJob = viewModelScope.launch(Dispatchers.IO) {
            videoHistoryDao.queryLastHistoryOfAnimeId(animeId, sourceId)?.let {
                _latestProgress.emit(Resource.Success(it))
            }
            refreshEpisodeProgress()
            // Initial loading already has a parallel history request in flight.
            if (loadDataJob?.isActive == true || authRepository.session.value == null) return@launch
            val detail = _detailPageData.value.getOrNull() ?: return@launch
            val remote = runCatching { authRepository.fetchTvHistoryItem(animeId) }
                .onFailure { if (it is CancellationException) throw it }
                .getOrNull()
            applyCloudHistory(detail, remote)
        }
    }

    private fun applyCloudHistory(detail: AnimeDetailPageData, remote: TvHistoryItem?): VideoHistoryEntity? {
        if (remote != null) remoteHistoryItem = remote
        val local = videoHistoryDao.queryLastHistoryOfAnimeId(animeId, sourceId)
        val mapped = remote?.toLocalHistory(detail)
        if (mapped != null && (local == null || mapped.updateTime > local.updateTime)) {
            videoHistoryDao.saveHistory(mapped)
        }
        val latest = listOfNotNull(local, mapped).maxByOrNull(VideoHistoryEntity::updateTime)
        latest?.let { _latestProgress.value = Resource.Success(it) }
        refreshEpisodeProgress()
        return latest
    }

    private fun refreshEpisodeProgress() {
        _episodeProgress.value = buildEpisodeProgress(
            localHistory = videoHistoryDao.queryHistoryOfAnime(animeId, sourceId),
            remoteHistory = remoteHistoryItem
        )
    }

    fun loadRating() {
        if (authRepository.session.value == null || ratingJob?.isActive == true) return
        _ratingState.value = _ratingState.value.copy(available = true, loading = true, loadFailed = false)
        ratingJob = viewModelScope.launch(Dispatchers.IO) {
            val result = fetchDetailSection { authRepository.fetchMyRating(animeId) }
            _ratingState.update { current ->
                current.copy(
                    score = result.getOrNull() ?: current.score,
                    loading = false,
                    loadFailed = result.isFailure
                )
            }
        }
    }

    /** Saves the viewer's stars to the same Aulama ID store the web and finale sheet use. */
    suspend fun saveRating(detail: AnimeDetailPageData, score: Int): Boolean {
        if (!_ratingState.value.available || _ratingState.value.loading || _ratingState.value.loadFailed) return false
        _ratingState.value = _ratingState.value.copy(saving = true)
        val favorite = detail.toFavoritePayload()
        val saved = withContext(Dispatchers.IO) {
            runCatching {
                authRepository.saveRating(
                    AnimeRatingPayload(
                        animeId = detail.animeId.ifBlank { animeId },
                        animeTitle = detail.animeName,
                        poster = detail.imageUrl,
                        rating = score,
                        tags = favorite.tags,
                        year = favorite.year,
                        sourceRating = favorite.providerRating,
                        sourceTypeId = sourceId,
                        summary = detail.description
                    )
                )
            }.onFailure { if (it is CancellationException) throw it }.getOrDefault(false)
        }
        _ratingState.value = _ratingState.value.copy(
            score = if (saved) score else _ratingState.value.score,
            saving = false
        )
        return saved
    }

    fun toggleFavorite(detail: AnimeDetailPageData) {
        val current = _favoriteUiState.value
        if (current.isLoading || current.isUpdating) return
        if (current.loadFailed) {
            loadFavoriteState()
            return
        }

        val shouldFavorite = !current.isFavorite
        _favoriteUiState.value = current.copy(isUpdating = true)
        favoriteJob = viewModelScope.launch(Dispatchers.IO) {
            val result = runCatching {
                if (authRepository.session.value == null) {
                    if (shouldFavorite) {
                        guestLibraryStore.save(detail.toFavoritePayload())
                    } else {
                        guestLibraryStore.delete(detail.animeId, sourceId)
                    }
                } else {
                    if (shouldFavorite) {
                        authRepository.saveFavorite(detail.toFavoritePayload())
                    } else {
                        authRepository.deleteFavorite(detail.animeId)
                    }
                }
            }
            if (result.getOrDefault(false)) {
                _favoriteUiState.value = FavoriteUiState(
                    isFavorite = shouldFavorite,
                    isLoading = false,
                    isUpdating = false
                )
            } else {
                _favoriteUiState.value = current.copy(isUpdating = false)
                favoriteErrorChannel.send(
                    if (authRepository.session.value == null) {
                        "未能將收藏儲存到此裝置，請稍後再試"
                    } else {
                        "收藏更新失敗，請稍後再試"
                    }
                )
            }
        }
    }

    private fun loadFavoriteState() {
        favoriteJob?.cancel()
        _favoriteUiState.value = _favoriteUiState.value.copy(isLoading = true, loadFailed = false)
        favoriteJob = viewModelScope.launch(Dispatchers.IO) {
            val result = fetchDetailSection {
                if (authRepository.session.value == null) guestLibraryStore.contains(animeId, sourceId)
                else authRepository.fetchFavorites().any { it.id == animeId }
            }
            _favoriteUiState.update { current ->
                current.copy(
                    isFavorite = result.getOrNull() ?: current.isFavorite,
                    isLoading = false,
                    loadFailed = result.isFailure
                )
            }
            if (result.isFailure) favoriteErrorChannel.send("未能讀取收藏狀態，請重新載入")
        }
    }

    private fun AnimeDetailPageData.toFavoritePayload(): FavoritePayload {
        val tags = infoValue("類型", "类型")
            .split('、', ',', '，', '/', '|')
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinct()
        val providerRating = infoValue("評分", "评分")
            .let { RATING_PATTERN.find(it)?.value?.toDoubleOrNull() }
            ?.coerceIn(0.0, 10.0)
            ?: 0.0
        return FavoritePayload(
            id = animeId,
            title = animeName,
            poster = imageUrl,
            tags = tags,
            year = infoValue("年份", "年"),
            summary = description,
            sourceTypeId = sourceId,
            providerRating = providerRating
        )
    }

    private fun AnimeDetailPageData.infoValue(vararg labels: String): String {
        val entry = infoList.firstOrNull { info ->
            labels.any { label ->
                info.trim().startsWith("$label：") || info.trim().startsWith("$label:")
            }
        }.orEmpty()
        return entry.substringAfter('：', entry.substringAfter(':', "")).trim()
    }

    private fun TvHistoryItem.toLocalHistory(detail: AnimeDetailPageData): VideoHistoryEntity? {
        val indexedPlaylist = detail.playLists
            .getOrNull(detail.defaultPlayListIndex)
            ?.takeIf { it.episodeList.isNotEmpty() }
            ?: detail.playLists.firstOrNull { it.defaultPlayList && it.episodeList.isNotEmpty() }
            ?: detail.playLists.firstOrNull { it.episodeList.isNotEmpty() }
            ?: return null
        val exactEpisode = detail.playLists
            .asSequence()
            .flatMap { it.episodeList.asSequence() }
            .firstOrNull { it.episodeId == episodeId }
        val mappedEpisode = exactEpisode ?: indexedPlaylist.episodeList.getOrNull(
            episodeIndex.coerceIn(0, indexedPlaylist.episodeList.lastIndex)
        ) ?: return null
        return VideoHistoryEntity(
            animeId = animeId.ifBlank { anime.id },
            animeName = anime.title.ifBlank { detail.animeName },
            episodeId = mappedEpisode.episodeId,
            lastEpisodeName = episodeLabel.ifBlank { mappedEpisode.episode },
            updateTime = updatedAtEpochMs.coerceAtLeast(1L),
            lastPlayTime = PlaybackCompletionPolicy.mappedCloudPosition(
                currentTimeMs = currentTimeSeconds.toPositionMs(),
                durationMs = durationSeconds.toPositionMs(),
                completed = completed
            ),
            coverUrl = anime.imageUrl.ifBlank { detail.imageUrl },
            videoDuration = durationSeconds.toPositionMs(),
            sourceId = sourceId
        )
    }

    private fun AnimeDetailPageData.positionFor(episodeId: String?): Pair<Int, Int> {
        if (episodeId.isNullOrBlank()) return lastPlayEpisodePosition
        playLists.forEachIndexed { playlistIndex, playlist ->
            val episodeIndex = playlist.episodeList.indexOfFirst { it.episodeId == episodeId }
            if (episodeIndex >= 0) return playlistIndex to episodeIndex
        }
        return lastPlayEpisodePosition
    }

    private fun Double.toPositionMs(): Long =
        takeIf { it.isFinite() && it > 0.0 }
            ?.times(1_000.0)
            ?.coerceAtMost(Long.MAX_VALUE.toDouble())
            ?.toLong()
            ?: 0L

    private class RelatedPreviewPreparationException(message: String) : Exception(message)

    companion object {
        private val RATING_PATTERN = Regex("""\d+(?:\.\d+)?""")
    }

}

internal class DetailRelatedPreviewRequestOwnership {
    private var generation = 0L

    fun startRequest(): Long = ++generation

    fun invalidate() {
        generation += 1L
    }

    fun owns(requestGeneration: Long): Boolean = requestGeneration == generation
}

/** The viewer's own star rating; only available with an Aulama ID. */
data class DetailRatingState(
    val available: Boolean = false,
    val score: Int = 0,
    val saving: Boolean = false,
    val loading: Boolean = false,
    val loadFailed: Boolean = false
)

data class FavoriteUiState(
    val isFavorite: Boolean = false,
    val isLoading: Boolean = true,
    val isUpdating: Boolean = false,
    val loadFailed: Boolean = false
)
