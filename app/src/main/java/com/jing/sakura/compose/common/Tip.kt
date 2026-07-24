@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.jing.sakura.compose.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.delay

@Composable
fun LoadingOverlay(
    visible: Boolean,
    text: String = ""
) {
    val reducedMotion = rememberReducedMotion()
    var retained by remember { mutableStateOf(visible) }
    LaunchedEffect(visible) {
        if (visible) {
            retained = true
        } else if (retained) {
            if (!reducedMotion) delay(120L)
            retained = false
        }
    }
    if (retained) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(if (reducedMotion) 0 else 180)),
            exit = fadeOut(tween(if (reducedMotion) 0 else 120))
        ) {
            Loading(text = text)
        }
    }
}

@Composable
fun Loading(text: String = "") {
    val reducedMotion = rememberReducedMotion()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(100f)
            .aulamaTvBackground(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.lightweightEntrance(
                transitionKey = Unit,
                reducedMotion = reducedMotion,
                durationMillis = 200,
                offsetY = 0.dp
            ),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AulamaAnimeBrandMark(height = 54.dp)
            Spacer(Modifier.height(16.dp))
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = AulamaTvColors.Cyan,
                trackColor = AulamaTvColors.Outline.copy(alpha = 0.5f),
                strokeWidth = 2.5.dp
            )
            if (text.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = localizedText(text),
                    color = AulamaTvColors.TextSecondary,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontSize = 15.sp,
                        lineHeight = 19.sp,
                        fontWeight = FontWeight.Black
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun ErrorTip(message: String, retry: () -> Unit = {}) {
    val focusRequester = remember { FocusRequester() }
    val reducedMotion = rememberReducedMotion()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .aulamaTvBackground(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .lightweightEntrance(
                    transitionKey = message,
                    reducedMotion = reducedMotion,
                    durationMillis = 200,
                    offsetY = 0.dp
                ),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AulamaAnimeBrandMark(height = 48.dp)
            Spacer(Modifier.height(20.dp))
            Text(
                text = localizedText("暫時未能載入"),
                color = AulamaTvColors.TextPrimary,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = 24.sp,
                    lineHeight = 29.sp,
                    fontWeight = FontWeight.Black
                )
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = localizedText(message),
                color = AulamaTvColors.TextSecondary,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontSize = 15.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.Black
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(18.dp))
            AulamaActionButton(
                label = localizedText("重試"),
                onClick = retry,
                modifier = Modifier.focusRequester(focusRequester),
                accent = AulamaTvColors.Cyan
            )
        }
    }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}
