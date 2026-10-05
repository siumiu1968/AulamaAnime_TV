package com.jing.sakura.compose.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.NativeKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val SpringButtonClickKeys = intArrayOf(
    NativeKeyEvent.KEYCODE_DPAD_CENTER,
    NativeKeyEvent.KEYCODE_ENTER,
    NativeKeyEvent.KEYCODE_NUMPAD_ENTER
)

private val SpringButtonOnAccent = Color(0xFF061014)

/**
 * Button whose focus feedback behaves like tvOS: it lifts on a spring, its fill and outline
 * blend rather than snap, and it gives under the finger on OK. Fill, outline and scale are read
 * in the draw phase so only the label recomposes while it animates.
 *
 * It stays focusable while [enabled] is false (only clicks are blocked), so a button that is
 * briefly disabled, e.g. while a favourite request is in flight, keeps the user's focus.
 *
 * @param prominent the screen's primary action: drawn as a bright, filled button at rest.
 */
@Composable
fun SpringFocusButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accent: Color = AulamaTvColors.Cyan,
    prominent: Boolean = false,
    cornerRadius: Dp = 8.dp,
    focusedScale: Float = 1.06f,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp),
    content: @Composable RowScope.() -> Unit
) {
    val reducedMotion = rememberReducedMotion()
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    var pressed by remember { mutableStateOf(false) }
    val currentOnClick by rememberUpdatedState(onClick)
    val currentEnabled by rememberUpdatedState(enabled)
    LaunchedEffect(focused) { if (!focused) pressed = false }
    val focus = animateFloatAsState(
        targetValue = if (focused) 1f else 0f,
        animationSpec = AulamaMotion.focusSpring(reducedMotion),
        label = "spring-button-focus"
    )
    val press = animateFloatAsState(
        targetValue = if (pressed) AulamaMotion.PressedScale else 1f,
        animationSpec = tween(if (reducedMotion) 0 else AulamaMotion.PressMillis),
        label = "spring-button-press"
    )
    val restContainer = if (prominent) Color.White.copy(alpha = 0.92f) else AulamaTvColors.SurfaceRaised
    val restContent = if (prominent) SpringButtonOnAccent else AulamaTvColors.TextPrimary
    val contentColor by animateColorAsState(
        targetValue = when {
            focused -> SpringButtonOnAccent
            else -> restContent
        }.copy(alpha = if (enabled) 1f else 0.5f),
        animationSpec = tween(if (reducedMotion) 0 else 160, easing = AulamaMotion.EaseInOut),
        label = "spring-button-content"
    )
    Row(
        modifier = modifier
            .graphicsLayer {
                val scale = 1f + (focusedScale - 1f) * focus.value
                scaleX = scale * press.value
                scaleY = scale * press.value
            }
            .drawBehind {
                val progress = AulamaMotion.unit(focus.value)
                val radius = cornerRadius.toPx()
                drawRoundRect(
                    color = lerp(restContainer, accent, progress)
                        .let { if (currentEnabled) it else it.copy(alpha = it.alpha * 0.6f) },
                    cornerRadius = CornerRadius(radius)
                )
                val stroke = (1f + progress).dp.toPx()
                val inset = stroke / 2f
                val restBorder = if (prominent) Color.Transparent else AulamaTvColors.Outline
                drawRoundRect(
                    color = lerp(restBorder, AulamaTvColors.FocusBorder, progress),
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - stroke, size.height - stroke),
                    cornerRadius = CornerRadius(radius - inset),
                    style = Stroke(stroke)
                )
            }
            .semantics(mergeDescendants = true) {
                role = Role.Button
                if (!enabled) disabled()
                this.onClick(action = {
                    if (currentEnabled) currentOnClick()
                    currentEnabled
                })
            }
            .onPreviewKeyEvent { event ->
                if (event.nativeKeyEvent.keyCode !in SpringButtonClickKeys) {
                    return@onPreviewKeyEvent false
                }
                when (event.type) {
                    KeyEventType.KeyDown -> if (currentEnabled) pressed = true
                    KeyEventType.KeyUp -> {
                        // Only a key-up that follows our own key-down counts as a click.
                        if (pressed) {
                            pressed = false
                            if (currentEnabled) currentOnClick()
                        }
                    }
                }
                true
            }
            .pointerInput(Unit) {
                detectTapGestures { if (currentEnabled) currentOnClick() }
            }
            .focusable(interactionSource = interactionSource)
            .padding(contentPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        CompositionLocalProvider(
            androidx.tv.material3.LocalContentColor provides contentColor,
            androidx.compose.material3.LocalContentColor provides contentColor
        ) {
            content()
        }
    }
}
