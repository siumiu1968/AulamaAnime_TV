package com.jing.sakura.compose.screen

import android.graphics.Bitmap
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jing.sakura.compose.common.ArtworkLoading
import com.jing.sakura.compose.common.LocalTvLanguage
import com.jing.sakura.compose.common.TvLanguage
import com.jing.sakura.detail.DetailRelatedLoadState
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class, androidx.tv.material3.ExperimentalTvMaterial3Api::class)
class ArtworkLoadingTipsTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun tipsFitBelowMainLoadingAndRotateWithoutDelayingContent() {
        val loading = mutableStateOf(true)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CompositionLocalProvider(LocalTvLanguage provides TvLanguage.Traditional) {
                if (loading.value) {
                    ArtworkLoading(
                        title = "春夏秋冬代行者 春之舞",
                        imageUrl = "",
                        tags = "奇幻、校園",
                        episodeInfo = "更新至第 12 集",
                        resumeEpisode = "第 3 集"
                    )
                } else {
                    androidx.tv.material3.Text("內容已載入")
                }
            }
        }
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        val tipNode = compose.onNodeWithTag("loading-tip-text")
        val firstTip = tipNode.fetchSemanticsNode().config[SemanticsProperties.Text].joinToString()
        val layoutResults = mutableListOf<TextLayoutResult>()
        tipNode.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layoutResults) }
        assertTrue(layoutResults.isNotEmpty())
        assertTrue(layoutResults.single().lineCount <= 2)
        assertFalse(layoutResults.single().hasVisualOverflow)
        assertTrue(
            tipNode.fetchSemanticsNode().boundsInRoot.top >
                compose.onNodeWithTag("artwork-loading-main").fetchSemanticsNode().boundsInRoot.bottom
        )
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val screenshot = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(instrumentation.targetContext.getExternalFilesDir(null), "loading-tips.png")
            .outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) }
        compose.mainClock.advanceTimeBy(7_000)
        compose.waitForIdle()
        val nextTip = tipNode.fetchSemanticsNode().config[SemanticsProperties.Text].joinToString()
        assertNotEquals(firstTip, nextTip)
        compose.runOnIdle { loading.value = false }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        compose.onNodeWithText("內容已載入").assertIsDisplayed()
        tipNode.assertDoesNotExist()
    }

    @Test
    fun failedRelatedSectionCanRetryWithTheRemoteAndShowsRealLoadingState() {
        val state = mutableStateOf<DetailRelatedLoadState>(DetailRelatedLoadState.Error())
        var retries = 0
        compose.setContent {
            CompositionLocalProvider(LocalTvLanguage provides TvLanguage.Traditional) {
                val requester = remember { FocusRequester() }
                DetailRelatedStatus(
                    state = state.value,
                    hasExistingItems = false,
                    onRetry = {
                        retries++
                        state.value = DetailRelatedLoadState.Loading
                    },
                    modifier = Modifier.focusRequester(requester)
                )
                LaunchedEffect(Unit) { requester.requestFocus() }
            }
        }
        compose.onNodeWithText("相關動漫暫時未能載入").assertIsDisplayed()
        compose.onNodeWithText("重新載入").performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle { assertEquals(1, retries) }
        compose.onNodeWithText("相關動漫載入中…").assertIsDisplayed()
        compose.onNodeWithText("重新載入").assertDoesNotExist()
        compose.runOnIdle { state.value = DetailRelatedLoadState.Complete }
        compose.onNodeWithText("相關動漫載入中…").assertDoesNotExist()
    }
}
