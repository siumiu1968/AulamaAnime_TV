package com.jing.sakura.compose.common

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.MarqueeAnimationMode
import androidx.compose.foundation.MarqueeSpacing
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import kotlinx.coroutines.delay

/** Scrolls only when the single-line title genuinely overflows its bounded area. */
@OptIn(ExperimentalFoundationApi::class, ExperimentalTvMaterial3Api::class)
@Composable
fun AutoMarqueeText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color,
    style: TextStyle,
    enabled: Boolean = true,
    velocity: Dp = 24.dp
) {
    var overflows by remember(text) { mutableStateOf(false) }
    val scrolls = enabled && overflows
    var marqueeStarted by remember(text, scrolls) { mutableStateOf(false) }
    val edgeFade = 14.dp

    LaunchedEffect(scrolls) {
        marqueeStarted = false
        if (scrolls) {
            // Let basicMarquee leave its initial pause before exposing the edge mask.
            delay(MARQUEE_EDGE_FADE_DELAY_MILLIS)
            marqueeStarted = true
        }
    }

    Box(
        modifier = modifier
            .clipToBounds()
            .graphicsLayer {
                if (shouldDrawMarqueeEdgeFade(scrolls, marqueeStarted)) {
                    compositingStrategy = CompositingStrategy.Offscreen
                }
            }
            .drawWithContent {
                drawContent()
                if (shouldDrawMarqueeEdgeFade(scrolls, marqueeStarted) && size.width > 0f) {
                    val fadeFraction = (edgeFade.toPx() / size.width).coerceIn(0f, 0.16f)
                    drawRect(
                        brush = Brush.horizontalGradient(
                            colorStops = arrayOf(
                                0f to Color.Transparent,
                                fadeFraction to Color.Black,
                                (1f - fadeFraction) to Color.Black,
                                1f to Color.Transparent
                            )
                        ),
                        blendMode = BlendMode.DstIn
                    )
                }
            }
    ) {
        Text(
            text = text,
            color = color,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
            style = style,
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (scrolls) {
                        Modifier.basicMarquee(
                            iterations = Int.MAX_VALUE,
                            animationMode = MarqueeAnimationMode.Immediately,
                            repeatDelayMillis = 1_000,
                            initialDelayMillis = MARQUEE_INITIAL_DELAY_MILLIS.toInt(),
                            spacing = MarqueeSpacing(48.dp),
                            velocity = velocity
                        )
                    } else {
                        Modifier
                    }
                )
        )
        if (enabled) {
            Text(
                text = text,
                color = Color.Transparent,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
                onTextLayout = { overflows = it.hasVisualOverflow },
                style = style,
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { alpha = 0f }
            )
        }
    }
}

private const val MARQUEE_INITIAL_DELAY_MILLIS = 1_500L
private const val MARQUEE_EDGE_FADE_DELAY_MILLIS = MARQUEE_INITIAL_DELAY_MILLIS + 50L
