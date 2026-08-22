package com.jing.sakura.compose.common

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import kotlinx.coroutines.delay

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun VideoCard(
    modifier: Modifier = Modifier,
    imageUrl: String,
    title: String,
    subTitle: String = "",
    sourceName: String = "",
    focusScale: Float = AulamaFocusScale,
    isFocusable: Boolean = true,
    externallyFocused: Boolean = false,
    showFocusFrame: Boolean = true,
    focusAccent: Color? = null,
    posterWidthPx: Int = 420,
    posterHeightPx: Int = 600,
    onKeyEvent: ((KeyEvent) -> Boolean)? = null,
    onLongClick: (() -> Unit)? = null,
    onFocused: (() -> Unit)? = null,
    onClick: () -> Unit = {},
) {
    val displayTitle = localizedText(title)
    val displaySubtitle = localizedText(subTitle)
    val displaySourceName = localizedText(sourceName.toDisplayLineName())
    var internallyFocused by remember {
        mutableStateOf(false)
    }
    val focused = internallyFocused || externallyFocused
    val focusFrameActive = focused && showFocusFrame
    var focusSettled by remember { mutableStateOf(false) }
    var focusedAccent by remember(imageUrl) { mutableStateOf<Color?>(null) }
    val posterRequest = rememberPosterImageRequest(
        imageUrl = imageUrl,
        widthPx = posterWidthPx,
        heightPx = posterHeightPx
    )
    val extractedArtworkAccent = rememberArtworkAccent(
        imageUrl,
        enabled = focusSettled && focusAccent == null
    )
    val artworkAccent = focusAccent ?: if (focused) {
        focusedAccent ?: extractedArtworkAccent
    } else {
        extractedArtworkAccent
    }
    val cardScale = if (focusFrameActive) focusScale else 1f
    LaunchedEffect(focused) {
        focusSettled = false
        if (focused) {
            focusedAccent = focusAccent ?: extractedArtworkAccent
            onFocused?.invoke()
            delay(220)
            focusSettled = true
        } else {
            focusedAccent = null
        }
    }
    val focusLayerModifier = if (focusFrameActive) {
        Modifier.graphicsLayer {
                scaleX = cardScale
                scaleY = cardScale
                shadowElevation = if (focusFrameActive) 8.dp.toPx() else 0f
                shape = AulamaCardShape
                clip = false
                ambientShadowColor = artworkAccent.copy(alpha = 0.54f)
                spotShadowColor = artworkAccent.copy(alpha = 0.82f)
            }
    } else {
        Modifier
    }
    Box(
        modifier = modifier
            .then(focusLayerModifier)
            .onFocusChanged {
                internallyFocused = it.isFocused || it.hasFocus
            }
            .focusable(enabled = isFocusable)
            .customClick(onClick = onClick, onLongClick = onLongClick, onKeyEvent = onKeyEvent)
            .border(
                border = BorderStroke(
                    width = if (focusFrameActive) 2.5.dp else 1.dp,
                    color = if (focusFrameActive) artworkAccent
                    else AulamaTvColors.Outline.copy(alpha = 0.72f)
                ),
                shape = AulamaCardShape
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(AulamaCardShape)
        ) {
            AsyncImage(
                model = posterRequest,
                contentDescription = displayTitle,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Transparent,
                                Color(0x18000000),
                                Color(0x66000000),
                                Color(0xF2050810)
                            )
                        )
                    )
            )
            if (displaySubtitle.isNotEmpty()) {
                Text(
                    text = displaySubtitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(9.dp)
                        .wrapContentWidth()
                        .clip(RoundedCornerShape(5.dp))
                        .background(Color(0xCC080B12))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, bottom = 11.dp)
            ) {
                if (displaySourceName.isNotEmpty()) {
                    Text(
                        text = displaySourceName,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelSmall,
                        color = artworkAccent
                    )
                }
                AutoMarqueeText(
                    text = displayTitle,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontSize = 16.sp,
                        lineHeight = 19.sp,
                        fontWeight = FontWeight.Black
                    ),
                    color = AulamaTvColors.TextPrimary,
                    enabled = focused,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
