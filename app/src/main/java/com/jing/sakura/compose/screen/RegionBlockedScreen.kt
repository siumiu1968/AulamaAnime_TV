package com.jing.sakura.compose.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.jing.sakura.compose.common.AulamaActionButton
import com.jing.sakura.compose.common.AulamaAnimeBrandMark
import com.jing.sakura.compose.common.AulamaTvColors
import com.jing.sakura.compose.common.LocalTvLanguage
import com.jing.sakura.compose.common.TvLanguage
import com.jing.sakura.compose.common.aulamaTvBackground

internal data class RegionBlockCopy(
    val eyebrow: String,
    val title: String,
    val message: String,
    val connectionTip: String,
    val retry: String
)

internal fun regionBlockCopy(countryCode: String, language: TvLanguage): RegionBlockCopy {
    val chineseTip = when (language) {
        TvLanguage.Traditional -> "連線提示  若目前經 VPN 或代理連線，請先停用，或改用其他地區節點後再試。"
        TvLanguage.Simplified -> "连接提示  若目前经 VPN 或代理连接，请先停用，或改用其他地区节点后再试。"
    }
    return if (countryCode.equals("JP", ignoreCase = true)) {
        RegionBlockCopy(
            eyebrow = "接続制限 403",
            title = "この地域では\nご利用いただけません",
            message = "現在の接続地域ではサービスをご利用いただけません。接続先を変更して、もう一度お試しください。",
            connectionTip = chineseTip,
            retry = "再試行"
        )
    } else {
        RegionBlockCopy(
            eyebrow = "CONNECTION RESTRICTION 403",
            title = "This service is unavailable\nin your region",
            message = "Aulama Anime is unavailable from your current connection. Change your connection and try again.",
            connectionTip = chineseTip,
            retry = when (language) {
                TvLanguage.Traditional -> "重新嘗試"
                TvLanguage.Simplified -> "重新尝试"
            }
        )
    }
}

@Composable
@OptIn(ExperimentalComposeUiApi::class)
fun RegionBlockedScreen(
    countryCode: String,
    retrying: Boolean,
    onRetry: () -> Unit,
    onExit: () -> Unit,
    canSkip: Boolean = false,
    onSkip: () -> Unit = {}
) {
    val language = LocalTvLanguage.current
    val copy = regionBlockCopy(countryCode, language)
    val retryFocusRequester = remember { FocusRequester() }
    val skipFocusRequester = remember { FocusRequester() }
    var skipFocused by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onExit,
        properties = DialogProperties(
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                val navigation = event.key in setOf(
                    Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight, Key.Tab
                )
                if (navigation && canSkip && !retrying && event.type == KeyEventType.KeyDown &&
                    event.key in setOf(Key.DirectionUp, Key.DirectionDown)
                ) {
                    retryFocusRequester.freeFocus()
                    skipFocusRequester.freeFocus()
                    val target = if (skipFocused) retryFocusRequester else skipFocusRequester
                    target.requestFocus()
                    target.captureFocus()
                }
                navigation
            }
            .aulamaTvBackground()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        AulamaTvColors.Pink.copy(alpha = 0.11f),
                        Color.Transparent
                    ),
                    center = Offset(1_050f, 620f),
                    radius = 980f
                )
            )
            .padding(horizontal = 82.dp, vertical = 54.dp)
    ) {
        AulamaAnimeBrandMark(
            height = 58.dp,
            modifier = Modifier.align(Alignment.TopStart)
        )

        Row(
            modifier = Modifier.align(Alignment.Center),
            horizontalArrangement = Arrangement.spacedBy(54.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(240.dp)
                    .drawBehind {
                        val center = Offset(size.width / 2f, size.height / 2f)
                        drawCircle(
                            color = AulamaTvColors.Cyan.copy(alpha = 0.56f),
                            radius = size.minDimension * 0.43f,
                            center = center,
                            style = Stroke(width = 2.dp.toPx())
                        )
                        drawCircle(
                            color = AulamaTvColors.Pink.copy(alpha = 0.38f),
                            radius = size.minDimension * 0.31f,
                            center = center + Offset(18.dp.toPx(), -8.dp.toPx()),
                            style = Stroke(width = 1.5.dp.toPx())
                        )
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    AulamaTvColors.Cyan.copy(alpha = 0.16f),
                                    Color.Transparent
                                ),
                                center = center,
                                radius = size.minDimension * 0.36f
                            ),
                            radius = size.minDimension * 0.36f,
                            center = center
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "403",
                    color = Color.White.copy(alpha = 0.28f),
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontSize = 92.sp,
                        fontWeight = FontWeight.Black
                    )
                )
            }

            Column(
                modifier = Modifier.widthIn(max = 610.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = copy.eyebrow,
                    color = AulamaTvColors.Cyan,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = copy.title,
                    color = Color.White,
                    style = MaterialTheme.typography.displaySmall.copy(
                        fontSize = 42.sp,
                        lineHeight = 48.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    text = copy.message,
                    color = AulamaTvColors.TextSecondary,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 18.sp,
                        lineHeight = 27.sp
                    )
                )
                Spacer(Modifier.height(18.dp))
                Text(
                    text = copy.connectionTip,
                    color = Color.White.copy(alpha = 0.88f),
                    textAlign = TextAlign.Start,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 16.sp,
                        lineHeight = 23.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    modifier = Modifier
                        .background(
                            color = Color.White.copy(alpha = 0.055f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = 18.dp, vertical = 14.dp)
                )
                Spacer(Modifier.height(18.dp))
                AulamaActionButton(
                    label = if (retrying) "…" else copy.retry,
                    icon = Icons.Default.Refresh,
                    accent = AulamaTvColors.Cyan,
                    onClick = { if (!retrying) onRetry() },
                    modifier = Modifier
                        .width(184.dp)
                        .height(52.dp)
                        .focusRequester(retryFocusRequester)
                        .onFocusChanged { if (it.isFocused) skipFocused = false }
                        .focusProperties {
                            up = FocusRequester.Cancel
                            down = FocusRequester.Cancel
                            left = FocusRequester.Cancel
                            right = FocusRequester.Cancel
                            next = FocusRequester.Cancel
                            previous = FocusRequester.Cancel
                        },
                    centerLabel = true
                )
                if (canSkip) {
                    Spacer(Modifier.height(12.dp))
                    AulamaActionButton(
                        label = when (language) {
                            TvLanguage.Traditional -> "跳過限制"
                            TvLanguage.Simplified -> "跳过限制"
                        },
                        onClick = { if (!retrying) onSkip() },
                        accent = AulamaTvColors.Cyan,
                        modifier = Modifier
                            .width(184.dp)
                            .height(52.dp)
                            .focusRequester(skipFocusRequester)
                            .onFocusChanged { if (it.isFocused) skipFocused = true }
                            .focusProperties {
                                up = FocusRequester.Cancel
                                down = FocusRequester.Cancel
                                left = FocusRequester.Cancel
                                right = FocusRequester.Cancel
                                next = FocusRequester.Cancel
                                previous = FocusRequester.Cancel
                            },
                        centerLabel = true
                    )
                }
            }
        }
    }
    LaunchedEffect(retrying, canSkip) {
        withFrameNanos { }
        retryFocusRequester.freeFocus()
        if (canSkip) skipFocusRequester.freeFocus()
        val target = if (canSkip && skipFocused) skipFocusRequester else retryFocusRequester
        target.requestFocus()
        target.captureFocus()
    }
    }
}
