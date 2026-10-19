@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.jing.sakura.compose.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.jing.sakura.compose.common.AulamaAnimeBrandMark
import com.jing.sakura.compose.common.AulamaLoadingPulse
import com.jing.sakura.compose.common.AulamaTvColors
import com.jing.sakura.compose.common.SpringFocusButton
import com.jing.sakura.compose.common.aulamaTvBackground
import com.jing.sakura.compose.common.localizedText
import com.jing.sakura.home.HomeStartupStage
import com.jing.sakura.home.HomeStartupState

@Composable
internal fun HomeStartupScreen(
    state: HomeStartupState,
    onRetry: () -> Unit,
    onContinue: () -> Unit
) {
    val attention = state.stage == HomeStartupStage.NeedsAttention
    val retryFocus = remember { FocusRequester() }
    LaunchedEffect(attention) {
        if (attention) {
            withFrameNanos { }
            runCatching { retryFocus.requestFocus() }
        }
    }
    Box(Modifier.fillMaxSize().aulamaTvBackground()) {
        Column(
            modifier = Modifier.align(Alignment.Center).widthIn(max = 650.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            AulamaAnimeBrandMark(height = if (attention) 96.dp else 140.dp)
            if (attention) {
                Text(
                    text = localizedText(state.message),
                    color = AulamaTvColors.TextSecondary,
                    fontSize = 18.sp,
                    lineHeight = 25.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 2
                )
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    SpringFocusButton(
                        onClick = onRetry,
                        prominent = true,
                        modifier = Modifier.focusRequester(retryFocus).height(46.dp),
                        contentPadding = PaddingValues(horizontal = 22.dp)
                    ) { Text(localizedText("重新嘗試"), fontSize = 17.sp) }
                    if (state.canContinue) {
                        SpringFocusButton(
                            onClick = onContinue,
                            modifier = Modifier.height(46.dp),
                            contentPadding = PaddingValues(horizontal = 22.dp)
                        ) { Text(localizedText("先進入首頁"), fontSize = 17.sp) }
                    }
                }
            } else {
                AulamaLoadingPulse(Modifier.testTag("home-startup-progress"))
                Spacer(Modifier.height(2.dp))
            }
        }
    }
}
