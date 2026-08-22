package com.jing.sakura.detail

import android.util.Log
import androidx.annotation.MainThread
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jing.sakura.auth.AulamaAuthRepository
import com.jing.sakura.auth.FavoritePayload
import com.jing.sakura.auth.GuestLibraryStore
import com.jing.sakura.auth.TvHistoryItem
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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Collections

class DetailPageViewModel constructor(
    private val animeId: String,
    private val repository: WebPageRepository,
    private val videoHistoryDao: VideoHistoryDao,
    private val authRepository: AulamaAuthRepository,
    private val guestLibraryStore: GuestLibraryStore,
    val sourceId: String
) : ViewModel() {

    private var _detailPageData = MutableStateFlow<Resource<AnimeDetailPageData>>(Resource.Loading)

    val detailPageData: StateFlow<Resource<AnimeDetailPageData>>
        get() = _detailPageData

    private var _latestProgress = MutableStateFlow<Resource<VideoHistoryEntity>>(Resource.Loading)

    val latestProgress: StateFlow<Resource<VideoHistoryEntity>>
        get() = _latestProgress

    private var loadDataJob: Job? = null

    private val _favoriteUiState = MutableStateFlow(FavoriteUiState())
    val favoriteUiState: StateFlow<FavoriteUiState> = _favoriteUiState

    private val favoriteErrorChannel = Channel<String>(Channel.BUFFERED)
    val favoriteErrors = favoriteErrorChannel.receiveAsFlow()

    private var favoriteJob: Job? = null

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
    }

    fun loadData() {
        loadDataJob?.cancel()
        loadDataJob = viewModelScope.launch(Dispatchers.IO) {
            _detailPageData.emit(Resource.Loading)
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
                        runCatching { authRepository.fetchTvLibrary() }
                            .getOrNull()
                            ?.historyItems
                            ?.firstOrNull { item ->
                                (item.animeId == animeId || item.anime.id == animeId) &&
                                    (item.sourceTypeId.isBlank() || item.sourceTypeId == sourceId)
                            }
                    }
                }
                val cloudDetailJob = async {
                    if (authRepository.session.value == null) {
                        null
                    } else {
                        runCatching { authRepository.fetchTvAnimeDetail(animeId) }.getOrNull()
                    }
                }
                val sourceData = repository.fetchDetailPage(animeId, sourceId)
                val cloudDetail = cloudDetailJob.await()
                val data = sourceData.copy(
                    otherAnimeList = mergeRelatedAnime(
                        cloud = cloudDetail?.related.orEmpty(),
                        source = sourceData.otherAnimeList,
                        fallbackSourceId = sourceId
                    )
                )
                val localHistory = localHistoryJob.await()
                val remoteHistory = cloudHistoryJob.await()?.toLocalHistory(data)
                val history = listOfNotNull(localHistory, remoteHistory)
                    .maxByOrNull(VideoHistoryEntity::updateTime)
                if (remoteHistory != null &&
                    (localHistory == null || remoteHistory.updateTime > localHistory.updateTime)
                ) {
                    videoHistoryDao.saveHistory(remoteHistory)
                }
                history?.let { _latestProgress.emit(Resource.Success(it)) }
                _detailPageData.emit(
                    Resource.Success(
                        data.copy(lastPlayEpisodePosition = data.positionFor(history?.episodeId))
                    )
                )
            } catch (ex: Exception) {
                if (ex is CancellationException) {
                    throw ex
                }
                Log.e("homepage", "请求数据失败", ex)

                val message = "請求資料失敗：" + ex.message
                _detailPageData.emit(Resource.Error(message))
            }
        }
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
            val description = selectNonJapaneseSynopsis(
                runCatching {
                    repository.fetchDetailPage(anime.id, relatedSourceId).description
                }.getOrNull()
            )

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
        viewModelScope.launch(Dispatchers.Default) {
            videoHistoryDao.queryLastHistoryOfAnimeId(animeId, sourceId)?.let {
                _latestProgress.emit(Resource.Success(it))
            }
        }
    }

    fun toggleFavorite(detail: AnimeDetailPageData) {
        val current = _favoriteUiState.value
        if (current.isLoading || current.isUpdating) return

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
        favoriteJob = viewModelScope.launch(Dispatchers.IO) {
            if (authRepository.session.value == null) {
                _favoriteUiState.value = FavoriteUiState(
                    isFavorite = guestLibraryStore.contains(animeId, sourceId),
                    isLoading = false
                )
                return@launch
            }
            _favoriteUiState.value = FavoriteUiState(isLoading = true)
            runCatching { authRepository.fetchFavorites() }
                .onSuccess { favorites ->
                    _favoriteUiState.value = FavoriteUiState(
                        isFavorite = favorites.any { it.id == animeId },
                        isLoading = false
                    )
                }
                .onFailure {
                    _favoriteUiState.value = FavoriteUiState(isLoading = false)
                    favoriteErrorChannel.send("未能讀取收藏狀態，請稍後再試")
                }
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

data class FavoriteUiState(
    val isFavorite: Boolean = false,
    val isLoading: Boolean = true,
    val isUpdating: Boolean = false
)
