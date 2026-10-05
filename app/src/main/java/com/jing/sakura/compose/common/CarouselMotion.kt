package com.jing.sakura.compose.common

import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.exp

/**
 * Velocity-preserving scroller for fixed-stride poster carousels.
 *
 * Back-to-back `animateScrollToItem` calls each start from rest, so holding the D-pad makes a
 * row lurch card by card. Even retargeting an Animatable loses a frame or two per key press
 * (the old animation is cancelled before the new one draws its first frame). Instead, one frame
 * loop integrates a critically damped spring towards a target that key presses simply move, so
 * a held key sweeps through the row in one continuous glide and eases out on the final card.
 */
@Stable
class CarouselGlide internal constructor(
    private val state: LazyListState,
    private val scope: CoroutineScope,
    private val stridePx: Int
) {
    // Positions are relative to the item that was first visible when the glide started, so
    // rows with huge virtual indices (endless carousels) never lose float precision.
    private var anchorIndex = 0
    private var anchorOffsetPx = 0
    private var positionPx = 0f
    private var velocityPxPerSecond = 0f
    private var targetPx = 0f
    private var job: Job? = null

    fun glideTo(index: Int, reducedMotion: Boolean) {
        if (job?.isActive != true) {
            anchorIndex = state.firstVisibleItemIndex
            anchorOffsetPx = state.firstVisibleItemScrollOffset
            positionPx = 0f
            velocityPxPerSecond = 0f
        }
        targetPx = (index.toLong() - anchorIndex).toFloat() * stridePx - anchorOffsetPx
        if (reducedMotion) {
            job?.cancel()
            state.dispatchRawDelta(targetPx - positionPx)
            positionPx = targetPx
            velocityPxPerSecond = 0f
            return
        }
        if (job?.isActive == true) return
        job = scope.launch {
            var lastFrameNanos = -1L
            while (true) {
                val frameNanos = withFrameNanos { it }
                val dt = if (lastFrameNanos < 0) {
                    NominalFrameSeconds
                } else {
                    ((frameNanos - lastFrameNanos) / 1_000_000_000f).coerceIn(0f, MaxFrameSeconds)
                }
                lastFrameNanos = frameNanos
                // Closed-form critically damped step: exact for any dt, so slow frames on old
                // TV boxes never overshoot or wobble.
                val omega = AulamaMotion.GlideOmega
                val displacement = positionPx - targetPx
                val drive = velocityPxPerSecond + omega * displacement
                val decay = exp(-omega * dt)
                val nextDisplacement = (displacement + drive * dt) * decay
                velocityPxPerSecond = (velocityPxPerSecond - omega * drive * dt) * decay
                val next = targetPx + nextDisplacement
                state.dispatchRawDelta(next - positionPx)
                positionPx = next
                if (abs(nextDisplacement) < SettlePx && abs(velocityPxPerSecond) < SettleVelocity) {
                    state.dispatchRawDelta(targetPx - positionPx)
                    positionPx = targetPx
                    velocityPxPerSecond = 0f
                    break
                }
            }
        }
    }

    /** Lets the current glide land, then jumps without animation (used to recenter loops). */
    suspend fun jumpTo(index: Int) {
        job?.join()
        state.scrollToItem(index)
    }

    private companion object {
        const val NominalFrameSeconds = 1f / 60f
        const val MaxFrameSeconds = 0.05f
        const val SettlePx = 0.5f
        const val SettleVelocity = 8f
    }
}

@Composable
fun rememberCarouselGlide(state: LazyListState, itemWidth: Dp, spacing: Dp): CarouselGlide {
    val scope = rememberCoroutineScope()
    val stridePx = with(LocalDensity.current) { itemWidth.roundToPx() + spacing.roundToPx() }
    return remember(state, scope, stridePx) { CarouselGlide(state, scope, stridePx) }
}

/**
 * Scrolls so [index] sits [scrollOffset] px past the top of the list, on the glide spring when
 * the item is already laid out (exact distance known) and with Compose's estimating animation
 * otherwise. Ends with an exact snap so later "already there?" checks match to the pixel.
 */
suspend fun LazyListState.springScrollToItem(
    index: Int,
    scrollOffset: Int = 0,
    reducedMotion: Boolean = false
) {
    if (reducedMotion) {
        scrollToItem(index, scrollOffset)
        return
    }
    val item = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
    if (item == null) {
        animateScrollToItem(index, scrollOffset)
    } else {
        val distance = (item.offset + scrollOffset).toFloat()
        if (distance != 0f) animateScrollBy(distance, AulamaMotion.glideSpring())
    }
    scrollToItem(index, scrollOffset)
}
