package com.jing.sakura.compose.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jing.sakura.compose.common.AulamaActionButton
import com.jing.sakura.compose.common.LocalTvLanguage
import com.jing.sakura.compose.common.TvLanguage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RegionBlockedFocusTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun directionalKeysCannotLeaveRetryBeforeDuringOrAfterRetry() {
        val retrying = mutableStateOf(false)
        var retries = 0
        var behindClicks = 0
        compose.setContent {
            CompositionLocalProvider(LocalTvLanguage provides TvLanguage.Traditional) {
                Box {
                    AulamaActionButton(label = "Behind", onClick = { behindClicks++ })
                    RegionBlockedScreen(
                        countryCode = "US",
                        retrying = retrying.value,
                        onRetry = { retries++; retrying.value = true },
                        onExit = {}
                    )
                }
            }
        }
        compose.waitForIdle()
        fun pressDirections(label: String) {
            val button = compose.onNodeWithText(label)
            button.assertIsFocused()
            repeat(10) {
                listOf(Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight, Key.Tab).forEach { key ->
                    button.performKeyInput { pressKey(key) }
                    button.assertIsFocused()
                }
            }
        }
        pressDirections("重新嘗試")
        compose.onNodeWithText("重新嘗試").performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle { assertEquals(1, retries) }
        pressDirections("…")
        compose.onNodeWithText("…").performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle {
            assertEquals(1, retries)
            retrying.value = false
        }
        compose.waitForIdle()
        pressDirections("重新嘗試")
        compose.onNodeWithText("重新嘗試").performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle {
            assertEquals(2, retries)
            assertEquals(0, behindClicks)
        }
    }
}
