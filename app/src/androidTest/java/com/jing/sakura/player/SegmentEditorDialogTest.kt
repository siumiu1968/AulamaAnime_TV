package com.jing.sakura.player

import android.graphics.Bitmap
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jing.sakura.compose.common.LocalTvLanguage
import com.jing.sakura.compose.common.TvLanguage
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class SegmentEditorDialogTest {
    @get:Rule val compose = createComposeRule()
    private var position = 15_300L
    private var playing = false
    private var closed = 0
    private var saved: SegmentDraft? = null
    private var savedAll = false
    private val initial = SegmentDraft().set(SegmentMarker.INTRO_START, 1000).set(SegmentMarker.INTRO_END, 90000)

    private fun open(allowed: Boolean = true, failsOnce: Boolean = false) {
        var writes = 0
        compose.setContent {
            CompositionLocalProvider(LocalTvLanguage provides TvLanguage.Traditional) {
                SegmentEditorDialog("非常長嘅動漫名稱：遙控片頭片尾調節測試",
                    SegmentEditorTarget("cw:3946", "episode", 1, "第02集", "girigiri_cht", "主線路B 繁中"),
                    1_440_000, { position }, { playing }, { position = it }, { playing = !playing },
                    load = { SegmentEditorSnapshot(initial, initial, allowed) },
                    save = { draft, all ->
                        if (failsOnce && writes++ == 0) error("未能完成儲存，請重試")
                        saved = draft; savedAll = all
                        SegmentEditorSnapshot(draft, draft, allowed)
                    }, onClose = { closed++ })
            }
        }
        compose.waitForIdle()
    }

    private fun focus(label: String): SemanticsNodeInteraction = compose.onNodeWithText(label)
        .performScrollTo().performSemanticsAction(SemanticsActions.RequestFocus) { it() }

    @Test fun remoteAdjustsWithoutLosingFocusAndSaveCloseStayReachableAt720p() {
        open()
        val start = compose.onNodeWithTag("introStart")
        start.assertIsFocused().performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithText("開始  00:02.0").assertIsFocused()
        start.performKeyInput { pressKey(Key.DirectionLeft); pressKey(Key.DirectionCenter) }
        compose.onNodeWithText("開始  00:15.3").assertIsFocused()
        start.performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithTag("introEnd").assertIsFocused()
        repeat(4) { compose.onAllNodes(isFocused()).onFirst().performKeyInput { pressKey(Key.DirectionDown) } }
        if (compose.onAllNodes(hasText("關閉") and isFocused()).fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithText("關閉").performKeyInput { pressKey(Key.DirectionLeft) }
        }
        compose.onNodeWithText("儲存至全部線路").assertIsFocused()
        compose.onNodeWithText("儲存至全部線路").performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithText("關閉").assertIsFocused()
        compose.onNodeWithText("關閉").performKeyInput { pressKey(Key.DirectionLeft); pressKey(Key.DirectionCenter) }
        compose.runOnIdle { assertEquals(15_300L, saved!![SegmentMarker.INTRO_START]); assertTrue(savedAll) }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val screenshot = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(instrumentation.targetContext.getExternalFilesDir(null), "segment-editor.png")
            .outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun previewAndScopeDiscardAndBackDoNotSilentlyLoseDraft() {
        open()
        focus("預覽開始").performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle { assertEquals(0L, position); assertTrue(playing) }
        focus("本集沒有片頭").performKeyInput { pressKey(Key.DirectionCenter) }
        focus("目前線路").performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithText("繼續調節").assertIsFocused()
        compose.onNodeWithText("繼續調節").performKeyInput { pressKey(Key.DirectionCenter) }
        Espresso.pressBack()
        compose.onNodeWithText("繼續調節").assertIsFocused()
        compose.runOnIdle { assertEquals(0, closed); assertNull(saved) }
        compose.onNodeWithText("放棄修改").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
            .performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle { assertEquals(1, closed) }
    }

    @Test fun failedSaveKeepsDraftAndCanRetry() {
        open(failsOnce = true)
        focus("儲存至全部線路").performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithText("未能完成儲存，請重試").assertExists()
        compose.runOnIdle { assertNull(saved) }
        focus("儲存至全部線路").performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithText("已儲存至全部線路").assertExists()
        compose.runOnIdle { assertNotNull(saved) }
    }

    @Test fun serverPermissionIsRequiredEvenWhenDialogIsRequested() {
        open(allowed = false)
        compose.onNodeWithText("只有最高管理員可以調節片頭片尾").assertExists()
        compose.onNodeWithTag("introStart").assertDoesNotExist()
        compose.onNodeWithText("儲存至全部線路").assertIsNotEnabled()
        compose.onNodeWithText("關閉").assertIsFocused()
    }
}
