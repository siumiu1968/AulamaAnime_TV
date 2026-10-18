package com.jing.sakura.home

import com.jing.sakura.data.AnimeData
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeStartupReadinessTest {
    private fun anime(index: Int, url: String = "poster-$index") = AnimeData(
        id = index.toString(), url = "anime-$index", title = "Anime $index", imageUrl = url, sourceId = "test"
    )
    private val readyData = HomeStartupDataStatus(coreLoading = false, syncedLoading = false)

    @Test
    fun planIncludesTheWholeRenderedHomeAndDeduplicatesRequests() {
        val items = List(60) { anime(it) }
        val plan = homeStartupPosterPlan(items.take(7), listOf(items, items.take(10)))
        assertEquals(67, plan.targets.size) // Seven hero sizes plus sixty card sizes.
        assertEquals(67, plan.targets.map { Triple(it.imageUrl, it.widthPx, it.heightPx) }.distinct().size)
        assertEquals(0, plan.missingPosters)
    }

    @Test
    fun firstHeroAndFirstEightCardsArePrioritizedWithBoundedMemory() {
        val items = List(60) { anime(it) }
        val plan = homeStartupPosterPlan(items.take(7), listOf(items))
        val critical = plan.targets.filter { it.keepInMemory }
        assertEquals(9, critical.size)
        assertEquals(plan.targets.take(9), critical)
        assertEquals(HomePosterTarget("poster-0", 960, 1_360, true), critical.first())
        assertTrue(critical.drop(1).all { it.widthPx == 420 && it.heightPx == 600 })
    }

    @Test
    fun blankImageUrlsAreReportedAsUnavailableInsteadOfPretendingReady() {
        val plan = homeStartupPosterPlan(listOf(anime(1, "")), listOf(listOf(anime(1, ""))))
        assertEquals(1, plan.missingPosters)
        assertTrue(plan.targets.isEmpty())
        assertEquals(HomeStartupStage.NeedsAttention,
            homeStartupState(readyData, true, plan, 0, 0, false).stage)
    }

    @Test
    fun readinessRequiresDataAndSuccessfulImagesWithoutAMinimumTimer() {
        val plan = HomePosterPlan(listOf(HomePosterTarget("a")))
        assertEquals(HomeStartupStage.LoadingData,
            homeStartupState(HomeStartupDataStatus(), true, plan, 1, 0, false).stage)
        assertEquals(HomeStartupStage.LoadingPosters,
            homeStartupState(readyData, true, plan, 0, 0, false).stage)
        assertEquals(HomeStartupStage.Ready,
            homeStartupState(readyData, true, plan, 1, 0, false).stage)
        assertEquals(HomeStartupStage.NeedsAttention,
            homeStartupState(readyData.copy(coreFailed = true), true, plan, 1, 0, false).stage)
    }

    @Test
    fun posterWorkIsBoundedToFourConcurrentRequestsAndWaitsForCompletion() = runBlocking {
        val background = CoroutineScope(coroutineContext + SupervisorJob())
        try {
            val active = AtomicInteger()
            val maximum = AtomicInteger()
            val fourStarted = CompletableDeferred<Unit>()
            val imagesReady = CompletableDeferred<Unit>()
            val gate = HomeStartupReadiness(background, loadPoster = {
                val count = active.incrementAndGet()
                maximum.updateAndGet { maxOf(it, count) }
                if (count == 4) fourStarted.complete(Unit)
                imagesReady.await()
                active.decrementAndGet()
                true
            })
            gate.update(HomePosterPlan(List(30) { HomePosterTarget("$it") }), readyData, true)
            withTimeout(1_000) { fourStarted.await() }
            assertEquals(4, maximum.get())
            assertEquals(HomeStartupStage.LoadingPosters, gate.state.value.stage)
            imagesReady.complete(Unit)
            val ready = withTimeout(1_000) { gate.state.first { it.stage == HomeStartupStage.Ready } }
            assertEquals(30, ready.completedPosters)
            assertEquals(4, maximum.get())
        } finally { background.cancel() }
    }

    @Test
    fun completedCacheWarmupStillWaitsForTheFinalHomeData() = runBlocking {
        val background = CoroutineScope(coroutineContext + SupervisorJob())
        try {
            val gate = HomeStartupReadiness(background, loadPoster = { true })
            val plan = HomePosterPlan(listOf(HomePosterTarget("cached")))
            gate.update(plan, HomeStartupDataStatus(coreLoading = true, syncedLoading = false), true)
            withTimeout(1_000) { gate.state.first { it.completedPosters == 1 } }
            assertEquals(HomeStartupStage.LoadingData, gate.state.value.stage)
            gate.update(plan, readyData, true)
            assertEquals(HomeStartupStage.Ready, gate.state.value.stage)
        } finally { background.cancel() }
    }

    @Test
    fun failedPostersOfferActionsAndRetryPreservesSuccessfulWork() = runBlocking {
        val background = CoroutineScope(coroutineContext + SupervisorJob())
        try {
            val attempts = mutableMapOf<String, Int>()
            val gate = HomeStartupReadiness(background, loadPoster = { target ->
                val count = (attempts[target.imageUrl] ?: 0) + 1
                attempts[target.imageUrl] = count
                target.imageUrl != "bad" || count > 1
            })
            val plan = HomePosterPlan(listOf(HomePosterTarget("good"), HomePosterTarget("bad")))
            gate.update(plan, readyData, true)
            val failure = withTimeout(1_000) { gate.state.first { it.stage == HomeStartupStage.NeedsAttention } }
            assertTrue(failure.canContinue)
            gate.retry()
            withTimeout(1_000) { gate.state.first { it.stage == HomeStartupStage.Ready } }
            assertEquals(1, attempts["good"])
            assertEquals(2, attempts["bad"])
        } finally { background.cancel() }
    }

    @Test
    fun slowDataReachesAnActionableDeadlineAndOnlyExplicitContinueReleasesIt() = runBlocking {
        val background = CoroutineScope(coroutineContext + SupervisorJob())
        try {
            val gate = HomeStartupReadiness(background, loadPoster = { true }, deadlineMillis = 20)
            gate.update(HomePosterPlan(), HomeStartupDataStatus(), true)
            val failure = withTimeout(1_000) { gate.state.first { it.stage == HomeStartupStage.NeedsAttention } }
            assertTrue(failure.canContinue)
            assertTrue(failure.message.isNotBlank())
            gate.continueToHome()
            assertEquals(HomeStartupStage.Ready, gate.state.value.stage)
        } finally { background.cancel() }
    }

    @Test
    fun noContentCannotBypassTheGateAndBackgroundRefreshDoesNotHideAReadyHome() = runBlocking {
        val background = CoroutineScope(coroutineContext + SupervisorJob())
        try {
            val pending = CompletableDeferred<Boolean>()
            val gate = HomeStartupReadiness(background, loadPoster = { pending.await() })
            gate.update(HomePosterPlan(), readyData, false)
            assertFalse(gate.state.value.canContinue)
            gate.continueToHome()
            assertEquals(HomeStartupStage.NeedsAttention, gate.state.value.stage)
            gate.update(HomePosterPlan(), readyData, true)
            assertEquals(HomeStartupStage.Ready, gate.state.value.stage)
            gate.update(HomePosterPlan(listOf(HomePosterTarget("new"))), HomeStartupDataStatus(), true)
            assertEquals(HomeStartupStage.Ready, gate.state.value.stage)
        } finally { background.cancel() }
    }
}
