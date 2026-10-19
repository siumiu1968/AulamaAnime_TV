package com.jing.sakura.home

import com.jing.sakura.data.AnimeData
import com.jing.sakura.data.HomePageData
import com.jing.sakura.data.NamedValue
import com.jing.sakura.data.Resource
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

enum class HomeStartupStage { LoadingData, LoadingPosters, NeedsAttention, Ready }

data class HomeStartupState(
    val stage: HomeStartupStage = HomeStartupStage.LoadingData,
    val completedPosters: Int = 0,
    val totalPosters: Int = 0,
    val canContinue: Boolean = false,
    val message: String = "",
    val completedDataLoads: Int = 0
) {
    /** The poster total is final only after both home data sources have settled. */
    val progressFraction: Float
        get() = when (stage) {
            HomeStartupStage.Ready -> 1f
            HomeStartupStage.LoadingData -> completedDataLoads.coerceIn(0, 2) * 0.1f
            else -> {
                val posters = if (totalPosters > 0) {
                    completedPosters.coerceIn(0, totalPosters).toFloat() / totalPosters
                } else 0f
                (completedDataLoads.coerceIn(0, 2) * 0.1f + posters * 0.8f).coerceAtMost(0.99f)
            }
        }
}

data class HomeStartupDataStatus(
    val coreLoading: Boolean = true,
    val syncedLoading: Boolean = true,
    val coreFailed: Boolean = false,
    val syncedFailed: Boolean = false
) {
    val isLoading: Boolean get() = coreLoading || syncedLoading
    val hasFailure: Boolean get() = coreFailed || syncedFailed
}

/** Data and its completion flags reach Compose together, so a partial frame cannot release the logo. */
data class HomeStartupContent(
    val dataStatus: HomeStartupDataStatus = HomeStartupDataStatus(),
    val home: Resource<HomePageData> = Resource.Loading,
    val retainedHome: HomePageData? = null,
    val recommendations: List<AnimeData> = emptyList(),
    val syncedRows: List<NamedValue<List<AnimeData>>> = emptyList(),
    val todayUpdates: List<AnimeData> = emptyList(),
    val theaterItems: List<AnimeData> = emptyList()
)

internal data class HomePosterTarget(
    val imageUrl: String,
    val widthPx: Int = 420,
    val heightPx: Int = 600,
    val keepInMemory: Boolean = false
)

internal data class HomePosterPlan(
    val targets: List<HomePosterTarget> = emptyList(),
    val missingPosters: Int = 0
)

/** Only this rendered home payload is visited. The first viewport keeps at most nine decoded images. */
internal fun homeStartupPosterPlan(
    featured: List<AnimeData>,
    rows: List<List<AnimeData>>
): HomePosterPlan {
    val targets = linkedMapOf<Pair<String, Pair<Int, Int>>, HomePosterTarget>()
    fun add(anime: AnimeData, hero: Boolean = false, critical: Boolean = false) {
        val url = anime.imageUrl
        if (url.isBlank()) return
        val target = if (hero) HomePosterTarget(url, 960, 1_360, critical)
            else HomePosterTarget(url, keepInMemory = critical)
        val key = url to (target.widthPx to target.heightPx)
        val previous = targets[key]
        if (previous == null || critical) targets[key] = target
    }
    featured.firstOrNull()?.let { add(it, hero = true, critical = true) }
    rows.firstOrNull { it.isNotEmpty() }?.take(8)?.forEach { add(it, critical = true) }
    featured.forEach { add(it, hero = true) }
    rows.forEach { row -> row.forEach { add(it) } }
    return HomePosterPlan(
        targets = targets.values.toList(),
        missingPosters = (featured.asSequence() + rows.asSequence().flatten())
            .filter { it.imageUrl.isBlank() }
            .distinctBy { it.sourceId to it.id }
            .count()
    )
}

internal fun homeStartupState(
    data: HomeStartupDataStatus,
    hasContent: Boolean,
    plan: HomePosterPlan,
    successful: Int,
    failed: Int,
    deadlineExpired: Boolean
): HomeStartupState {
    val state = HomeStartupState(
        completedPosters = successful,
        totalPosters = plan.targets.size,
        canContinue = hasContent,
        completedDataLoads = (if (!data.coreLoading && !data.coreFailed) 1 else 0) +
            (if (!data.syncedLoading && !data.syncedFailed) 1 else 0)
    )
    return when {
        data.hasFailure -> state.copy(
            stage = HomeStartupStage.NeedsAttention,
            message = if (hasContent) "部分首頁資料暫時未能載入，請重新嘗試。"
                else "首頁資料暫時未能載入，請重新嘗試。"
        )
        failed > 0 || plan.missingPosters > 0 -> state.copy(
            stage = HomeStartupStage.NeedsAttention,
            message = "部分封面暫時未能載入，請重試或先進入首頁。"
        )
        !data.isLoading && hasContent && successful == plan.targets.size ->
            state.copy(stage = HomeStartupStage.Ready)
        deadlineExpired -> state.copy(
            stage = HomeStartupStage.NeedsAttention,
            message = if (hasContent) "準備首頁需時較長，請重試或先進入已載入內容。"
                else "首頁資料仍未準備好，請重新嘗試。"
        )
        data.isLoading -> state.copy(stage = HomeStartupStage.LoadingData)
        !hasContent -> state.copy(
            stage = HomeStartupStage.NeedsAttention,
            message = "暫時未有可顯示嘅首頁內容，請重新嘗試。"
        )
        else -> state.copy(stage = HomeStartupStage.LoadingPosters)
    }
}

/** Readiness has no minimum display time. The wait threshold offers actions while loading continues. */
internal class HomeStartupReadiness(
    private val scope: CoroutineScope,
    private val loadPoster: suspend (HomePosterTarget) -> Boolean,
    private val deadlineMillis: Long = 25_000L,
    private val posterTimeoutMillis: Long = 12_000L,
    private val parallelism: Int = 4
) {
    private val _state = MutableStateFlow(HomeStartupState())
    val state: StateFlow<HomeStartupState> = _state
    private var plan = HomePosterPlan()
    private var data = HomeStartupDataStatus()
    private var hasContent = false
    private var released = false
    private var deadlineExpired = false
    private var deadlineJob: Job? = null
    private var preloadJob: Job? = null
    private val generation = AtomicLong()
    private val results = ConcurrentHashMap<HomePosterTarget, Boolean>()

    @Synchronized
    fun update(nextPlan: HomePosterPlan, nextData: HomeStartupDataStatus, hasRenderableContent: Boolean) {
        data = nextData
        hasContent = hasRenderableContent
        startDeadline()
        if (nextPlan != plan) {
            plan = nextPlan
            results.keys.filterNot(nextPlan.targets::contains).forEach(results::remove)
            startPreload()
        }
        publish()
    }

    @Synchronized
    fun retry() {
        deadlineJob?.cancel()
        deadlineJob = null
        deadlineExpired = false
        results.filterValues { !it }.keys.forEach(results::remove)
        _state.value = HomeStartupState(
            completedPosters = results.count { it.value }, totalPosters = plan.targets.size,
            canContinue = hasContent,
            completedDataLoads = (if (!data.coreLoading && !data.coreFailed) 1 else 0) +
                (if (!data.syncedLoading && !data.syncedFailed) 1 else 0)
        )
        startDeadline()
        startPreload()
    }

    @Synchronized
    fun continueToHome() {
        if (!hasContent) return
        released = true
        deadlineJob?.cancel()
        _state.value = _state.value.copy(stage = HomeStartupStage.Ready)
        if (preloadJob?.isActive != true) startPreload()
    }

    @Synchronized
    fun reset() {
        generation.incrementAndGet()
        deadlineJob?.cancel()
        preloadJob?.cancel()
        deadlineJob = null
        preloadJob = null
        deadlineExpired = false
        released = false
        results.clear()
        plan = HomePosterPlan()
        data = HomeStartupDataStatus()
        hasContent = false
        _state.value = HomeStartupState()
    }

    private fun startDeadline() {
        if (released || deadlineJob != null) return
        deadlineJob = scope.launch {
            delay(deadlineMillis)
            synchronized(this@HomeStartupReadiness) {
                if (!released) {
                    deadlineExpired = true
                    publish()
                }
            }
        }
    }

    private fun startPreload() {
        val requestGeneration = generation.incrementAndGet()
        preloadJob?.cancel()
        val pending = plan.targets.filterNot(results::containsKey)
        if (pending.isEmpty()) return
        preloadJob = scope.launch {
            val next = AtomicInteger()
            coroutineScope {
                repeat(parallelism.coerceAtLeast(1).coerceAtMost(pending.size)) {
                    launch worker@{
                        while (true) {
                            val target = pending.getOrNull(next.getAndIncrement()) ?: break
                            val success = try {
                                withTimeoutOrNull(posterTimeoutMillis) { loadPoster(target) } == true
                            } catch (exception: CancellationException) {
                                throw exception
                            } catch (_: Exception) {
                                false
                            }
                            synchronized(this@HomeStartupReadiness) {
                                if (generation.get() == requestGeneration) {
                                    results[target] = success
                                    publish()
                                }
                            }
                            if (generation.get() != requestGeneration) return@worker
                        }
                    }
                }
            }
        }
    }

    @Synchronized
    private fun publish() {
        if (released) return
        val next = homeStartupState(data, hasContent, plan,
            successful = results.count { it.value }, failed = results.count { !it.value },
            deadlineExpired = deadlineExpired)
        _state.value = next
        if (next.stage == HomeStartupStage.Ready) {
            released = true
            deadlineJob?.cancel()
        }
    }
}
