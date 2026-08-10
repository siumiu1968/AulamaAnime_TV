package com.jing.sakura.player

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jing.sakura.auth.AulamaAuthRepository
import com.jing.sakura.auth.CloudTimestamp
import com.jing.sakura.auth.PlaybackHistoryPayload
import com.jing.sakura.auth.PlaybackHistorySyncQueue
import com.jing.sakura.auth.PlaybackHistorySyncScheduler
import com.jing.sakura.auth.PlaybackSegments
import com.jing.sakura.data.AnimePlayListEpisode
import com.jing.sakura.data.Resource
import com.jing.sakura.repo.WebPageRepository
import com.jing.sakura.room.VideoHistoryDao
import com.jing.sakura.room.VideoHistoryEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID


class VideoPlayerViewModel(
    val anime: NavigateToPlayerArg,
    private val repository: WebPageRepository,
    private val videoHistoryDao: VideoHistoryDao,
    private val authRepository: AulamaAuthRepository,
    private val historySyncQueue: PlaybackHistorySyncQueue
) : ViewModel() {

    private val TAG = VideoPlayerViewModel::class.java.simpleName

    private var _playList = emptyList<AnimePlayListEpisode>()

    private var _saveHistoryJob: Job? = null

    private var lastRemoteSyncAt = 0L

    private var playSessionId = UUID.randomUUID().toString()

    @Volatile
    private var currentPlayPosition: Long = 0L

    @Volatile
    private var videoDuration: Long = 0L

    val playList: List<AnimePlayListEpisode>
        get() = _playList

    private val _playIndex = MutableStateFlow(-1)
    val playIndex: Int
        get() = _playIndex.value

    private val _playerSubTitle = MutableStateFlow("")

    val playerSubTitle: MutableStateFlow<String>
        get() = _playerSubTitle

    private val _videoUrl = MutableStateFlow<Resource<EpisodeUrlAndHistory>>(Resource.Loading)
    val videoUrl: StateFlow<Resource<EpisodeUrlAndHistory>>
        get() = _videoUrl

    private val _playbackSegments = MutableStateFlow<PlaybackSegments?>(null)
    val playbackSegments: StateFlow<PlaybackSegments?>
        get() = _playbackSegments

    @Volatile
    private var playingEpisode: AnimePlayListEpisode? = null

    @Volatile
    private var forcedCompletedEpisodeId: String? = null

    private var loadVideoJob: Pair<AnimePlayListEpisode, Job>? = null
    private var playbackSegmentsJob: Job? = null

    private var pendingRemoteResumeMs = anime.resumePositionMs


    init {
        viewModelScope.launch {
            _playIndex.collectLatest { index ->
                if (index >= 0) {
                    val episode = _playList[index]
                    _playerSubTitle.emit(episode.episode)
                    _playbackSegments.emit(null)
                    fetchVideoUrl(episode)
                    fetchPlaybackSegments(episode, index)
                }
            }
        }
        init()
    }

    fun changePlayingEpisode(episode: AnimePlayListEpisode) {
        if (playingEpisode?.episodeId != episode.episodeId) {
            playSessionId = UUID.randomUUID().toString()
            lastRemoteSyncAt = 0L
            forcedCompletedEpisodeId = null
        }
        this.playingEpisode = episode
    }

    fun init() {
        viewModelScope.launch {
            this@VideoPlayerViewModel._playList = anime.playlist
            _playIndex.emit(anime.playIndex)
        }
    }

    private fun fetchVideoUrl(episode: AnimePlayListEpisode) {
        val job = loadVideoJob
        if (job != null && job.first == episode) {
            return
        }
        job?.second?.cancel()
        loadVideoJob = episode to viewModelScope.launch(Dispatchers.IO) {
            try {
                _videoUrl.emit(Resource.Loading)
                val resp = repository.fetchVideoUrl(
                    episode.episodeId,
                    animeId = anime.animeId,
                    sourceId = anime.sourceId
                )
                val history = videoHistoryDao.queryHistoryByEpisodeId(
                    animeId = anime.animeId,
                    episodeId = episode.episodeId,
                    sourceId = anime.sourceId
                )
                val remoteResumeMs = pendingRemoteResumeMs
                    .takeIf {
                        it >= 0L &&
                            episode.episodeId == anime.playlist.getOrNull(anime.playIndex)?.episodeId
                    }
                if (remoteResumeMs != null) {
                    pendingRemoteResumeMs = NavigateToPlayerArg.NO_REMOTE_RESUME_POSITION
                }
                when (resp) {
                    is Resource.Error -> _videoUrl.emit(Resource.Error(resp.message))
                    is Resource.Success -> _videoUrl.emit(
                        Resource.Success(
                            EpisodeUrlAndHistory(
                                videoUrl = resp.data.url,
                                videoDuration = history?.videoDuration ?: 0L,
                                lastPlayPosition = remoteResumeMs
                                    ?.coerceAtLeast(0L)
                                    ?: history?.lastPlayTime
                                    ?: 0L,
                                headers = resp.data.headers,
                                episode = episode
                            )
                        )
                    )

                    else -> {}
                }
            } catch (e: Exception) {
                if (e is CancellationException) {
                    throw e
                }
                Log.e(TAG, "fetchVideoUrl: ${e.message}", e)
                _videoUrl.emit(Resource.Error(e.message ?: ""))
            } finally {
                loadVideoJob = null
            }
        }
    }

    private fun fetchPlaybackSegments(episode: AnimePlayListEpisode, index: Int) {
        playbackSegmentsJob?.cancel()
        val episodeId = episode.episodeId
        playbackSegmentsJob = viewModelScope.launch(Dispatchers.IO) {
            var segments: PlaybackSegments? = null
            for (attempt in 1..PlaybackSegmentsRetryPolicy.MAX_ATTEMPTS) {
                val result = runCatching {
                    authRepository.fetchPlaybackSegments(
                        animeId = anime.animeId,
                        episodeId = episodeId,
                        episodeIndex = index
                    )
                }.onFailure { error ->
                    if (error !is CancellationException) {
                        Log.w(TAG, "Playback segments request failed (attempt $attempt/${PlaybackSegmentsRetryPolicy.MAX_ATTEMPTS})", error)
                    }
                }
                result.exceptionOrNull()?.let { error ->
                    if (error is CancellationException) throw error
                }
                segments = result.getOrNull()
                if (segments != null) break

                val retryDelayMs = PlaybackSegmentsRetryPolicy.retryDelayAfter(attempt) ?: break
                Log.w(TAG, "Playback segments unavailable; retrying attempt ${attempt + 1}/${PlaybackSegmentsRetryPolicy.MAX_ATTEMPTS}")
                delay(retryDelayMs)
            }
            val activeEpisodeId = _playList.getOrNull(_playIndex.value)?.episodeId
            if (PlaybackSegmentsRetryPolicy.belongsToActiveEpisode(
                    activeIndex = _playIndex.value,
                    activeEpisodeId = activeEpisodeId,
                    requestIndex = index,
                    requestEpisodeId = episodeId
                )
            ) {
                _playbackSegments.emit(segments)
            } else {
                Log.w(TAG, "Ignoring stale playback segments response")
            }
        }
    }

    fun playEpisodeOfIndex(index: Int) {
        viewModelScope.launch {
            _playIndex.emit(index)
        }
    }

    fun startSaveHistory() {
        _saveHistoryJob?.cancel()
        _saveHistoryJob = viewModelScope.launch(Dispatchers.IO) {
            while (true) {
                if (playingEpisode != null) {
                    saveHistorySnapshot(forceRemoteSync = false)
                    delay(5000L)
                } else {
                    delay(2000L)
                }
            }
        }
    }

    fun stopSaveHistory() {
        _saveHistoryJob?.cancel()
        _saveHistoryJob = null
        val snapshot = buildHistorySnapshot() ?: return
        if (snapshot.accountKey.isNotBlank()) {
            historySyncQueue.enqueue(snapshot.accountKey, snapshot.payload)
            PlaybackHistorySyncScheduler.enqueue(com.jing.sakura.SakuraApplication.context)
        }
        if (playingEpisode != null) {
            viewModelScope.launch(Dispatchers.IO) {
                persistHistorySnapshot(snapshot, forceRemoteSync = true)
            }
        }
    }

    private suspend fun saveHistorySnapshot(forceRemoteSync: Boolean) {
        val snapshot = buildHistorySnapshot() ?: return
        if (snapshot.accountKey.isNotBlank()) {
            historySyncQueue.enqueue(snapshot.accountKey, snapshot.payload)
        }
        persistHistorySnapshot(snapshot, forceRemoteSync)
    }

    private fun buildHistorySnapshot(forceCompleted: Boolean = false): HistorySnapshot? {
        val episode = playingEpisode ?: return null
        val now = System.currentTimeMillis()
        val duration = videoDuration.coerceAtLeast(0L)
        val completed = forceCompleted || forcedCompletedEpisodeId == episode.episodeId
        val position = if (completed) {
            PlaybackCompletionPolicy.completedPosition(duration)
        } else {
            currentPlayPosition.coerceAtLeast(0L)
        }
        val entity = VideoHistoryEntity(
                animeId = anime.animeId,
                animeName = anime.animeName,
                episodeId = episode.episodeId,
                lastEpisodeName = episode.episode,
                updateTime = now,
                lastPlayTime = position,
                coverUrl = anime.coverUrl,
                videoDuration = duration,
                sourceId = anime.sourceId
            )
        val payload = PlaybackHistoryPayload(
            animeId = anime.animeId,
            animeTitle = anime.animeName,
            poster = anime.coverUrl,
            episodeId = episode.episodeId,
            episodeLabel = episode.episode,
            episodeIndex = _playIndex.value.coerceAtLeast(0),
            episodeCount = _playList.size.coerceAtLeast(1),
            currentTimeSeconds = position / 1000.0,
            durationSeconds = duration / 1000.0,
            completed = completed ||
                duration > 0L && position >= (duration - 15_000L).coerceAtLeast(0L),
            sourceTypeId = anime.sourceId,
            playSessionId = playSessionId,
            updatedAt = CloudTimestamp.formatEpochMs(now)
        )
        return HistorySnapshot(
            accountKey = authRepository.session.value?.account?.email.orEmpty(),
            entity = entity,
            payload = payload,
            capturedAt = now
        )
    }

    private suspend fun persistHistorySnapshot(
        snapshot: HistorySnapshot,
        forceRemoteSync: Boolean
    ) {
        videoHistoryDao.saveHistory(snapshot.entity)
        if (snapshot.accountKey.isBlank()) return
        if (!forceRemoteSync && snapshot.capturedAt - lastRemoteSyncAt < REMOTE_SYNC_INTERVAL_MS) return

        PlaybackHistorySyncScheduler.enqueue(com.jing.sakura.SakuraApplication.context)

        val synced = runCatching {
            authRepository.syncPlaybackHistory(snapshot.payload)
        }.getOrDefault(false)
        if (synced) {
            lastRemoteSyncAt = snapshot.capturedAt
            historySyncQueue.removeIfCurrent(snapshot.accountKey, snapshot.payload)
        }
    }

    fun playNextEpisodeIfExists() {
        val nextIndex = findNextEpisodeIndex()
        if (nextIndex == null || _videoUrl.value !is Resource.Success) return
        playEpisodeOfIndex(nextIndex)
    }

    fun playPreviousEpisodeIfExists() {
        val prevIndex = EpisodePlaybackSequencePolicy.previousIndex(_playIndex.value)
        if (prevIndex != null) {
            playEpisodeOfIndex(prevIndex)
        }
    }

    fun playNextEpisodeAdjacent() {
        val nextIndex = EpisodePlaybackSequencePolicy.nextIndex(_playIndex.value, _playList.size)
        if (nextIndex != null) {
            playEpisodeOfIndex(nextIndex)
        }
    }

    fun hasNextEpisode(): Boolean =
        EpisodePlaybackSequencePolicy.nextIndex(_playIndex.value, _playList.size) != null

    /** Persists a durable completed snapshot before the player leaves the current episode. */
    fun completeCurrentEpisode(onCompleted: () -> Unit) {
        forcedCompletedEpisodeId = playingEpisode?.episodeId
        val snapshot = buildHistorySnapshot(forceCompleted = true)
        if (snapshot == null) {
            onCompleted()
            return
        }
        viewModelScope.launch {
            runCatching {
                withTimeoutOrNull(COMPLETION_SYNC_TIMEOUT_MS) {
                    withContext(Dispatchers.IO) {
                        if (snapshot.accountKey.isNotBlank()) {
                            historySyncQueue.enqueue(snapshot.accountKey, snapshot.payload)
                        }
                        persistHistorySnapshot(snapshot, forceRemoteSync = true)
                    }
                }
            }.onFailure { error ->
                Log.w(TAG, "Unable to flush completed playback snapshot", error)
            }
            onCompleted()
        }
    }

    private fun findNextEpisodeIndex(): Int? {
        return EpisodePlaybackSequencePolicy.nextIndex(_playIndex.value, _playList.size)
    }

    fun onPlayPositionChange(currentPosition: Long, duration: Long) {
        this.currentPlayPosition = currentPosition
        this.videoDuration = duration
    }

    fun retryLoadEpisode() {
        fetchVideoUrl(playList[_playIndex.value])
    }

    companion object {
        private const val REMOTE_SYNC_INTERVAL_MS = 15_000L
        private const val COMPLETION_SYNC_TIMEOUT_MS = 2_000L
    }

    private data class HistorySnapshot(
        val accountKey: String,
        val entity: VideoHistoryEntity,
        val payload: PlaybackHistoryPayload,
        val capturedAt: Long
    )
}
