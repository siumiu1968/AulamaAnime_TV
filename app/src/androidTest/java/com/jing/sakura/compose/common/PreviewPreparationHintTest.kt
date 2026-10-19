package com.jing.sakura.compose.common

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PreviewPreparationHintTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun compactLoadingReplacesTextAndDisappearsWhenPreparationEnds() {
        val visible = mutableStateOf(true)
        compose.setContent {
            PreviewPreparationHint(visible = visible.value, reducedMotion = true)
        }
        compose.onNodeWithTag("preview-loading-dots")
            .assertWidthIsEqualTo(24.dp)
            .assertHeightIsEqualTo(8.dp)
            .assert(SemanticsMatcher.expectValue(
                SemanticsProperties.ProgressBarRangeInfo,
                ProgressBarRangeInfo.Indeterminate
            ))
        compose.onNodeWithText("預覽準備中…").assertDoesNotExist()
        compose.runOnIdle { visible.value = false }
        compose.onNodeWithTag("preview-loading-dots").assertDoesNotExist()
    }

    @Test fun loadingDotsPulseWithoutChangingTheirBounds() {
        verifyMotion(reducedMotion = false)
    }

    @Test fun reducedMotionKeepsDotsStill() {
        verifyMotion(reducedMotion = true)
    }

    private fun verifyMotion(reducedMotion: Boolean) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            Box(Modifier.background(Color.Black)) {
                PreviewPreparationHint(visible = true, reducedMotion = reducedMotion)
            }
        }
        compose.mainClock.advanceTimeByFrame()
        val dots = compose.onNodeWithTag("preview-loading-dots")
        val before = dots.captureToImage()
        compose.mainClock.advanceTimeBy(300)
        val after = dots.captureToImage()
        assertEquals(before.width, after.width)
        assertEquals(before.height, after.height)
        val x = (before.width * 3 / 24).coerceIn(0, before.width - 1)
        val y = before.height / 2
        val oldPixel = before.toPixelMap()[x, y]
        val newPixel = after.toPixelMap()[x, y]
        if (reducedMotion) assertEquals(oldPixel, newPixel)
        else assertNotEquals(oldPixel, newPixel)
    }
}
