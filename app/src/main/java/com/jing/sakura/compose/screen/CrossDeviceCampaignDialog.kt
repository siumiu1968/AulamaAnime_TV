@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.jing.sakura.compose.screen

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Text
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.jing.sakura.R
import com.jing.sakura.compose.common.AulamaActionButton
import com.jing.sakura.compose.common.AulamaTvColors
import com.jing.sakura.compose.common.localizedText
import com.jing.sakura.home.CrossDeviceCampaign

@Composable
internal fun CrossDeviceCampaignDialog(onDismiss: () -> Unit) {
    val dismissFocus = remember { FocusRequester() }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            val compact = maxHeight < 600.dp
            val shape = RoundedCornerShape(22.dp)
            Column(
                modifier = Modifier
                    .widthIn(max = 1_000.dp)
                    .fillMaxWidth()
                    .clip(shape)
                    .background(Brush.linearGradient(listOf(
                        Color(0xFF152D40), AulamaTvColors.Surface, Color(0xFF102D31)
                    )))
                    .border(BorderStroke(1.dp, AulamaTvColors.Cyan.copy(alpha = 0.38f)), shape)
                    .padding(if (compact) 20.dp else 28.dp)
                    .testTag("cross-device-campaign"),
                verticalArrangement = Arrangement.spacedBy(if (compact) 14.dp else 20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        localizedText("大螢幕以外，精彩繼續"),
                        color = AulamaTvColors.TextPrimary,
                        modifier = Modifier.weight(1f).testTag("campaign-title"),
                        fontSize = if (compact) 27.sp else 32.sp,
                        lineHeight = if (compact) 33.sp else 39.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        localizedText("AULAMA ANIME · 跨裝置追番"),
                        color = AulamaTvColors.Cyan,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(if (compact) 20.dp else 28.dp)
                ) {
                    Column(
                        modifier = Modifier.weight(1.1f),
                        verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp)
                    ) {
                        Image(
                            painter = painterResource(R.drawable.cross_device_web_preview),
                            contentDescription = localizedText("Aulama Anime 網站畫面"),
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(if (compact) 206.dp else 260.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(AulamaTvColors.Background)
                                .border(1.dp, AulamaTvColors.Outline, RoundedCornerShape(12.dp))
                                .testTag("campaign-web-preview")
                        )
                        CampaignHeading("WEB · 更多追番功能")
                        CampaignBody("搜尋動漫、查看更新時間表，探索更多內容。", compact,
                            Modifier.testTag("campaign-web-copy"))
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp)
                    ) {
                        CampaignHeading("ANDROID · 手機／平板專用")
                        CampaignBody("為觸控操作與不同螢幕優化，隨時接續你嘅動漫時光。", compact,
                            Modifier.testTag("campaign-android-copy"))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            CampaignQr(
                                url = CrossDeviceCampaign.WEB_URL,
                                label = "開啟網站",
                                tag = "campaign-web-qr",
                                size = if (compact) 136.dp else 164.dp,
                                modifier = Modifier.weight(1f)
                            )
                            CampaignQr(
                                url = CrossDeviceCampaign.ANDROID_DOWNLOAD_URL,
                                label = "下載 Android App",
                                tag = "campaign-android-qr",
                                size = if (compact) 136.dp else 164.dp,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("aulama.org/anime", color = AulamaTvColors.Cyan,
                            fontSize = if (compact) 16.sp else 18.sp, lineHeight = 22.sp,
                            fontWeight = FontWeight.SemiBold)
                        Text(localizedText("用手機掃描 QR Code · 關閉後不再顯示此公告"),
                            color = AulamaTvColors.TextSecondary, fontSize = 13.sp, lineHeight = 18.sp)
                    }
                    AulamaActionButton(
                        label = "關閉公告",
                        onClick = onDismiss,
                        modifier = Modifier.height(46.dp).focusRequester(dismissFocus)
                            .testTag("campaign-dismiss"),
                        contentHeight = 46.dp,
                        accent = AulamaTvColors.Cyan
                    )
                }
            }
        }
        LaunchedEffect(Unit) {
            withFrameNanos { }
            dismissFocus.requestFocus()
        }
    }
}

@Composable
private fun CampaignHeading(text: String) {
    Text(localizedText(text), color = AulamaTvColors.TextPrimary, fontSize = 19.sp,
        lineHeight = 24.sp, fontWeight = FontWeight.Bold)
}

@Composable
private fun CampaignBody(text: String, compact: Boolean, modifier: Modifier) {
    Text(localizedText(text), color = AulamaTvColors.TextSecondary,
        modifier = modifier,
        fontSize = if (compact) 16.sp else 18.sp,
        lineHeight = if (compact) 23.sp else 26.sp, maxLines = 2)
}

@Composable
private fun CampaignQr(url: String, label: String, tag: String, size: Dp, modifier: Modifier) {
    val bitmap = remember(url) {
        val matrix = QRCodeWriter().encode(url, BarcodeFormat.QR_CODE, 384, 384)
        val pixels = IntArray(matrix.width * matrix.height) { index ->
            if (matrix[index % matrix.width, index / matrix.width]) android.graphics.Color.BLACK
            else android.graphics.Color.WHITE
        }
        Bitmap.createBitmap(matrix.width, matrix.height, Bitmap.Config.RGB_565).also {
            it.setPixels(pixels, 0, matrix.width, 0, 0, matrix.width, matrix.height)
        }.asImageBitmap()
    }
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Image(bitmap = bitmap, contentDescription = localizedText(label),
            modifier = Modifier.size(size).clip(RoundedCornerShape(8.dp))
                .background(Color.White).testTag(tag), contentScale = ContentScale.Fit)
        Text(localizedText(label), color = AulamaTvColors.TextPrimary,
            fontSize = 13.sp, lineHeight = 18.sp, textAlign = TextAlign.Center, maxLines = 1)
    }
}
