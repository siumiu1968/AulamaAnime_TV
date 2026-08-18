@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.jing.sakura.compose.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.jing.sakura.compose.common.AulamaActionButton
import com.jing.sakura.compose.common.AulamaTvColors
import com.jing.sakura.compose.common.localizedText
import kotlinx.coroutines.delay

internal const val AULAMA_TV_GITHUB_REPOSITORY = "siumiu1968 / AulamaAnime_TV"

@Composable
fun FirstLaunchSupportDialog(
    onDismiss: () -> Unit
) {
    val dismissFocus = remember { FocusRequester() }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            modifier = Modifier
                .widthIn(min = 500.dp, max = 600.dp)
                .border(
                    BorderStroke(1.dp, AulamaTvColors.Cyan.copy(alpha = 0.30f)),
                    RoundedCornerShape(20.dp)
                ),
            shape = RoundedCornerShape(20.dp),
            color = Color.Transparent
        ) {
            Column(
                modifier = Modifier
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                AulamaTvColors.SurfaceRaised.copy(alpha = 0.98f),
                                AulamaTvColors.Surface.copy(alpha = 0.98f)
                            )
                        )
                    )
                    .padding(horizontal = 30.dp, vertical = 26.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(AulamaTvColors.Amber.copy(alpha = 0.16f), CircleShape)
                            .border(1.dp, AulamaTvColors.Amber.copy(alpha = 0.52f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        androidx.tv.material3.Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = AulamaTvColors.Amber,
                            modifier = Modifier.size(25.dp)
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            text = localizedText("一起讓大螢幕追番體驗更好"),
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = AulamaTvColors.TextPrimary
                        )
                        Text(
                            text = localizedText("感謝你選擇 Aulama Anime TV"),
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                            color = AulamaTvColors.Cyan
                        )
                    }
                }
                Text(
                    text = localizedText(
                        "Aulama Anime TV 希望讓動漫搜尋、選集與播放在電視上更直覺，並持續改善遙控操作、播放穩定性與中文體驗。"
                    ),
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 17.sp,
                        lineHeight = 25.sp
                    ),
                    color = AulamaTvColors.TextSecondary
                )
                Text(
                    text = localizedText(
                        "如果這個 App 對你有幫助，歡迎在 GitHub 為專案按下 Star。你的支持，是我們持續完善大螢幕追番體驗的重要動力。"
                    ),
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 17.sp,
                        lineHeight = 25.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    color = AulamaTvColors.TextPrimary
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            AulamaTvColors.Background.copy(alpha = 0.58f),
                            RoundedCornerShape(10.dp)
                        )
                        .border(
                            1.dp,
                            AulamaTvColors.Outline.copy(alpha = 0.72f),
                            RoundedCornerShape(10.dp)
                        )
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = localizedText("從第三方取得 APK？請在 GitHub 搜尋"),
                        style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp),
                        color = AulamaTvColors.TextSecondary
                    )
                    Text(
                        text = AULAMA_TV_GITHUB_REPOSITORY,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        ),
                        color = AulamaTvColors.Cyan
                    )
                }
                Spacer(Modifier.height(2.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)
                ) {
                    AulamaActionButton(
                        label = "知道了",
                        onClick = onDismiss,
                        modifier = Modifier
                            .height(46.dp)
                            .focusRequester(dismissFocus),
                        accent = AulamaTvColors.Blue,
                        contentHeight = 46.dp
                    )
                }
                Text(
                    text = localizedText("此提示只會在首次使用時顯示一次。"),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.End,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                    color = AulamaTvColors.TextSecondary.copy(alpha = 0.72f)
                )
            }
        }
    }
    LaunchedEffect(Unit) {
        repeat(5) { attempt ->
            delay(if (attempt == 0) 80 else 90)
            runCatching { dismissFocus.requestFocus() }
        }
    }
}
