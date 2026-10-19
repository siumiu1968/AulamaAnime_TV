package com.jing.sakura.compose.screen

import android.graphics.Bitmap
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jing.sakura.compose.common.LocalTvLanguage
import com.jing.sakura.compose.common.TvLanguage
import com.jing.sakura.home.HomeStartupStage
import com.jing.sakura.home.HomeStartupState
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class HomeStartupScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun normalStartupShowsOnlyLogoAndProgressAndRetryIsReachableAfterFailure() {
        val state = mutableStateOf(HomeStartupState(HomeStartupStage.LoadingData))
        var retries = 0
        compose.setContent {
            CompositionLocalProvider(LocalTvLanguage provides TvLanguage.Traditional) {
                HomeStartupScreen(state.value, onRetry = { retries++ }, onContinue = {})
            }
        }
        compose.onNodeWithContentDescription("Aulama Anime").assertIsDisplayed()
        compose.onNodeWithTag("home-startup-progress").assertIsDisplayed()
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text)).assertCountEquals(0)
        compose.runOnIdle {
            state.value = HomeStartupState(HomeStartupStage.LoadingPosters, 2, 8, true,
                completedDataLoads = 2)
        }
        compose.onNodeWithContentDescription("Aulama Anime").assertIsDisplayed()
        compose.onNodeWithTag("home-startup-progress").assertIsDisplayed()
            .assert(SemanticsMatcher.expectValue(
                SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo(0.4f, 0f..1f)
            ))
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text)).assertCountEquals(0)
        compose.onNodeWithText("重新嘗試").assertDoesNotExist()
        compose.runOnIdle {
            state.value = state.value.copy(stage = HomeStartupStage.NeedsAttention,
                message = "部分封面暫時未能載入，請重試或先進入首頁。")
        }
        compose.onNodeWithText("部分封面暫時未能載入，請重試或先進入首頁。").assertIsDisplayed()
        compose.onNodeWithTag("home-startup-progress").assertDoesNotExist()
        compose.onNodeWithText("重新嘗試").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle { assertEquals(1, retries) }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val screenshot = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(instrumentation.targetContext.getExternalFilesDir(null), "home-startup-retry.png")
            .outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun readyFinishesTheRealProgressBeforeReleasingTheLogo() {
        compose.mainClock.autoAdvance = false
        val state = mutableStateOf(HomeStartupState(HomeStartupStage.LoadingPosters,
            completedPosters = 3, totalPosters = 4, completedDataLoads = 2))
        var ready = false
        compose.setContent {
            HomeStartupScreen(state.value, onRetry = {}, onContinue = {}, onReady = { ready = true })
        }
        compose.runOnIdle {
            assertFalse(ready)
            state.value = state.value.copy(stage = HomeStartupStage.Ready)
        }
        compose.mainClock.advanceTimeBy(64)
        compose.runOnIdle { assertFalse(ready) }
        compose.mainClock.advanceTimeBy(160)
        compose.waitForIdle()
        compose.runOnIdle { assertTrue(ready) }
        compose.onNodeWithTag("home-startup-progress")
            .assert(SemanticsMatcher.expectValue(
                SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo(1f, 0f..1f)
            ))
    }

    @Test
    fun partialHomeCanContinueExplicitlyButMissingDataCannot() {
        val state = mutableStateOf(HomeStartupState(HomeStartupStage.NeedsAttention,
            canContinue = true, message = "準備首頁需時較長，請重試或先進入已載入內容。"))
        var continues = 0
        compose.setContent {
            CompositionLocalProvider(LocalTvLanguage provides TvLanguage.Traditional) {
                HomeStartupScreen(state.value, onRetry = {}, onContinue = { continues++ })
            }
        }
        compose.onNodeWithText("重新嘗試").performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithText("先進入首頁").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle {
            assertEquals(1, continues)
            state.value = state.value.copy(canContinue = false)
        }
        compose.onNodeWithText("先進入首頁").assertDoesNotExist()
        compose.onNodeWithText("重新嘗試").assertIsDisplayed()
    }
}
