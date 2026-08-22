package com.jing.sakura.timeline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.content.Context
import android.util.Log
import com.jing.sakura.SakuraApplication
import com.jing.sakura.data.AnimeData
import com.jing.sakura.data.Resource
import com.jing.sakura.data.UpdateTimeLine
import com.jing.sakura.home.HeroPreviewSpec
import com.jing.sakura.home.HeroPreviewState
import com.jing.sakura.home.preparePreviewWithRetry
import com.jing.sakura.player.NavigateToPlayerArg
import com.jing.sakura.repo.CycaniSource
import com.jing.sakura.repo.WebPageRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.atomic.AtomicLong

class TimelineViewModel(
    private val repository: WebPageRepository,
    val sourceId:String
) : ViewModel() {

    private val _timelines: MutableStateFlow<Resource<UpdateTimeLine>> =
        MutableStateFlow(Resource.Loading)
    val timelines: StateFlow<Resource<UpdateTimeLine>>
        get() = _timelines
    private val _synopses = MutableStateFlow<Map<String, String>>(emptyMap())
    val synopses: StateFlow<Map<String, String>>
        get() = _synopses
    private val _previewState = MutableStateFlow<HeroPreviewState>(HeroPreviewState.Idle)
    val previewState: StateFlow<HeroPreviewState>
        get() = _previewState

    private val synopsisJobs = mutableMapOf<String, Job>()
    private val synopsisSemaphore = Semaphore(2)
    private var synopsisPrefetchJob: Job? = null
    private var previewJob: Job? = null
    private val previewGeneration = AtomicLong(0L)
    private val synopsisCache = TimelineSynopsisCache(
        SakuraApplication.context.getSharedPreferences(
            "timeline_synopsis_cache",
            Context.MODE_PRIVATE
        )
    )

    init {
        loadData()
    }

    fun loadData() {
        synopsisPrefetchJob?.cancel()
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _timelines.emit(Resource.Loading)
                val data = repository.fetchUpdateTimeline(sourceId)
                val initialCandidates = timelinePrefetchAnime(
                    data = data,
                    selectedDayIndex = data.current
                )
                hydrateCachedSynopses(initialCandidates)
                _timelines.emit(Resource.Success(data))
                scheduleSynopsisPrefetch(initialCandidates)
            } catch (ex: Exception) {
                if (ex is CancellationException) {
                    throw ex
                }
                _timelines.emit(Resource.Error("載入失敗：${ex.message}"))
            }
        }
    }

    fun loadSynopsis(anime: AnimeData) {
        hydrateCachedSynopses(listOf(anime))
        requestSynopsis(anime)
    }

    fun prepareDay(dayIndex: Int) {
        val data = (timelines.value as? Resource.Success)?.data ?: return
        val candidates = timelinePrefetchAnime(data, dayIndex)
        hydrateCachedSynopses(candidates)
        scheduleSynopsisPrefetch(candidates)
    }

    fun preparePreview(anime: AnimeData) {
        if (anime.id.isBlank() || anime.sourceId.isBlank()) return
        val requestKey = "${anime.sourceId}:${anime.id}"
        val current = _previewState.value
        if (current is HeroPreviewState.Loading && current.requestKey == requestKey) return
        if (current is HeroPreviewState.Ready &&
            current.spec.navigateToPlayerArg.animeId == anime.id &&
            current.spec.navigateToPlayerArg.sourceId == anime.sourceId
        ) return

        val generation = previewGeneration.incrementAndGet()
        previewJob?.cancel()
        _previewState.value = HeroPreviewState.Loading(requestKey)
        previewJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val spec = preparePreviewWithRetry {
                    val detail = repository.fetchDetailPage(anime.id, anime.sourceId)
                    val playList = detail.playLists
                        .getOrNull(detail.defaultPlayListIndex)
                        ?.takeIf { it.episodeList.isNotEmpty() }
                        ?: detail.playLists.firstOrNull {
                            it.defaultPlayList && it.episodeList.isNotEmpty()
                        }
                        ?: detail.playLists.firstOrNull { it.episodeList.isNotEmpty() }
                        ?: error("未有可預覽集數")
                    val episode = playList.episodeList.first()
                    val response = repository.fetchVideoUrl(
                        episodeId = episode.episodeId,
                        sourceId = anime.sourceId,
                        animeId = anime.id
                    )
                    val video = when (response) {
                        is Resource.Success -> response.data
                        is Resource.Error -> error(response.message)
                        is Resource.Loading -> error("預覽影片尚未準備好")
                    }
                    val playerArg = NavigateToPlayerArg(
                        animeName = detail.animeName.ifBlank { anime.title },
                        animeId = anime.id,
                        coverUrl = detail.imageUrl.ifBlank { anime.imageUrl },
                        playIndex = 0,
                        playlist = playList.episodeList,
                        sourceId = anime.sourceId,
                        playlists = detail.playLists,
                        playlistIndex = detail.playLists.indexOf(playList)
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
                publishPreview(
                    generation,
                    HeroPreviewState.Ready(spec)
                )
            } catch (exception: Exception) {
                if (exception is CancellationException) throw exception
                Log.d("timeline-preview", "時間表預覽準備失敗：$requestKey", exception)
                publishPreview(
                    generation,
                    HeroPreviewState.Error(
                        requestKey = requestKey,
                        message = exception.message ?: "預覽載入失敗"
                    )
                )
            } finally {
                if (previewGeneration.get() == generation) previewJob = null
            }
        }
    }

    fun cancelPreview() {
        previewGeneration.incrementAndGet()
        previewJob?.cancel()
        previewJob = null
        _previewState.value = HeroPreviewState.Idle
    }

    private fun publishPreview(generation: Long, state: HeroPreviewState) {
        if (previewGeneration.get() == generation) _previewState.value = state
    }

    private fun scheduleSynopsisPrefetch(candidates: List<AnimeData>) {
        synopsisPrefetchJob?.cancel()
        synopsisPrefetchJob = viewModelScope.launch(Dispatchers.IO) {
            delay(260)
            candidates.forEach { anime ->
                requestSynopsis(anime)
                delay(36)
            }
        }
    }

    private fun hydrateCachedSynopses(candidates: List<AnimeData>) {
        val cached = candidates.mapNotNull { anime ->
            resolveTimelineSynopsis(
                original = anime.description,
                localized = synopsisCache.get(anime.id)
            ).takeIf(String::isNotBlank)?.let { anime.id to it }
        }.toMap()
        if (cached.isNotEmpty()) {
            _synopses.update { current -> current + cached }
        }
    }

    private fun requestSynopsis(anime: AnimeData) {
        if (anime.id.isBlank() || !_synopses.value[anime.id].isNullOrBlank()) return
        synopsisCache.get(anime.id)?.let { cached ->
            val readySynopsis = resolveTimelineSynopsis(anime.description, cached)
            if (readySynopsis.isNotBlank()) {
                _synopses.update { current -> current + (anime.id to readySynopsis) }
            }
            return
        }
        val source = repository.requireAnimationSource(sourceId) as? CycaniSource ?: return
        val job = synchronized(synopsisJobs) {
            if (synopsisJobs[anime.id]?.isActive == true) return
            viewModelScope.launch(Dispatchers.IO, start = CoroutineStart.LAZY) {
                try {
                    val synopsis = resolveTimelineSynopsis(
                        original = anime.description,
                        localized = synopsisSemaphore.withPermit {
                            source.fetchTimelineSynopsis(anime.id)
                        }
                    )
                    if (synopsis.isNotBlank()) {
                        synopsisCache.put(anime.id, synopsis)
                        _synopses.update { current -> current + (anime.id to synopsis) }
                    }
                } finally {
                    synchronized(synopsisJobs) {
                        synopsisJobs.remove(anime.id)
                    }
                }
            }.also { synopsisJobs[anime.id] = it }
        }
        job.start()
    }
}

private class TimelineSynopsisCache(
    private val preferences: android.content.SharedPreferences
) {
    fun get(animeId: String): String? = preferences.getString(key(animeId), null)
        ?.takeIf(String::isNotBlank)

    @Synchronized
    fun put(animeId: String, synopsis: String) {
        val storageKey = key(animeId)
        val keys = preferences.getString(INDEX_KEY, "")
            .orEmpty()
            .lineSequence()
            .filter(String::isNotBlank)
            .filterNot { it == storageKey }
            .toMutableList()
            .apply { add(storageKey) }
        val expired = keys.take((keys.size - MAX_ENTRIES).coerceAtLeast(0))
        preferences.edit().apply {
            expired.forEach(::remove)
            putString(storageKey, synopsis)
            putString(INDEX_KEY, keys.takeLast(MAX_ENTRIES).joinToString("\n"))
        }.apply()
    }

    private fun key(animeId: String): String = "synopsis_$animeId"

    private companion object {
        const val INDEX_KEY = "cached_keys"
        const val MAX_ENTRIES = 240
    }
}
