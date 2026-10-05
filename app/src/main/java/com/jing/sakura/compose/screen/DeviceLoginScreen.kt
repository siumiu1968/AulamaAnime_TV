@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.jing.sakura.compose.screen

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.KeyboardReturn
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.NativeKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.imageLoader
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.jing.sakura.auth.AulamaAccount
import com.jing.sakura.update.TvUpdateChannel
import com.jing.sakura.auth.AuthUiState
import com.jing.sakura.auth.DeviceCode
import com.jing.sakura.compose.common.AulamaActionButton
import com.jing.sakura.compose.common.AulamaAccountAvatar
import com.jing.sakura.compose.common.AulamaAnimeBrandMark
import com.jing.sakura.compose.common.AulamaLoadingPulse
import com.jing.sakura.compose.common.AulamaMotion
import com.jing.sakura.compose.common.AulamaTvColors
import com.jing.sakura.compose.common.LocalTvLanguage
import com.jing.sakura.compose.common.TvLanguage
import com.jing.sakura.compose.common.lightweightEntrance
import com.jing.sakura.compose.common.localizedText
import com.jing.sakura.compose.common.aulamaTvBackground
import com.jing.sakura.compose.common.rememberArtworkAccent
import com.jing.sakura.compose.common.rememberPosterImageRequest
import com.jing.sakura.compose.common.rememberReducedMotion
import com.jing.sakura.compose.common.safelyRequestFocus
import com.jing.sakura.data.AnimeData
import kotlinx.coroutines.delay
import kotlin.math.abs

private const val WELCOME_ROTATION_INTERVAL_MS = 8_000L
private const val WELCOME_ACCENT_TRANSITION_MS = 620
private const val WELCOME_ARTWORK_ASPECT = 0.7f
private val WelcomeContentStart = 56.dp
private val WelcomeChoiceWidth = 244.dp
private val WelcomeChoiceGap = 20.dp
private val WelcomeChoiceCorner = 14.dp
private val WelcomeChoiceRestContainer = Color(0xC7141A24)
private val WelcomeChoiceFocusedContainer = Color(0xF21E2633)
private val WelcomeOnAccent = Color(0xFF061014)
private val WelcomeClickKeyCodes = intArrayOf(
    NativeKeyEvent.KEYCODE_DPAD_CENTER,
    NativeKeyEvent.KEYCODE_ENTER,
    NativeKeyEvent.KEYCODE_NUMPAD_ENTER
)

internal data class WelcomeCopy(
    val greeting: String,
    val slogan: String,
    val message: String,
    val eyebrow: String,
    val loginButton: String,
    val loginDescription: String,
    val loginHint: String,
    val recommendedBadge: String,
    val guestButton: String,
    val guestDescription: String,
    val guestHint: String
)

internal data class WelcomeTitleLayout(
    val fontSizeSp: Int,
    val lineHeightSp: Int,
    val maxLines: Int
)

/** Sizes the "now showing" caption so long titles shrink and wrap instead of ellipsizing. */
internal fun welcomeTitleLayout(title: String): WelcomeTitleLayout {
    val visibleLength = title.count { !it.isWhitespace() }
    return when {
        visibleLength <= 12 -> WelcomeTitleLayout(fontSizeSp = 24, lineHeightSp = 30, maxLines = 2)
        visibleLength <= 28 -> WelcomeTitleLayout(fontSizeSp = 20, lineHeightSp = 25, maxLines = 2)
        else -> WelcomeTitleLayout(fontSizeSp = 16, lineHeightSp = 21, maxLines = 3)
    }
}

internal fun welcomeCopy(language: TvLanguage): WelcomeCopy = when (language) {
    TvLanguage.Traditional -> WelcomeCopy(
        greeting = "歡迎來到 Aulama Anime",
        slogan = "讓每一段精彩\n都在大螢幕綻放",
        message = "選擇開始方式，之後隨時可以在用戶卡片切換。",
        eyebrow = "本季焦點",
        loginButton = "使用 Aulama ID 登入",
        loginDescription = "跨裝置同步收藏、觀看進度\n與個人化推薦",
        loginHint = "手機掃碼即可完成登入",
        recommendedBadge = "推薦",
        guestButton = "免登入使用",
        guestDescription = "無需帳號，立即開始觀看\n記錄只會儲存在這部電視",
        guestHint = "之後可隨時登入同步"
    )
    TvLanguage.Simplified -> WelcomeCopy(
        greeting = "欢迎来到 Aulama Anime",
        slogan = "让每一段精彩\n都在大屏幕绽放",
        message = "选择开始方式，之后随时可以在用户卡片切换。",
        eyebrow = "本季焦点",
        loginButton = "使用 Aulama ID 登录",
        loginDescription = "跨设备同步收藏、观看进度\n与个性化推荐",
        loginHint = "手机扫码即可完成登录",
        recommendedBadge = "推荐",
        guestButton = "免登录使用",
        guestDescription = "无需账号，立即开始观看\n记录只会保存在这台电视",
        guestHint = "之后可随时登录同步"
    )
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun DeviceLoginScreen(
    state: AuthUiState,
    onLogin: () -> Unit,
    onGuest: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    welcomeAnime: List<AnimeData>
) {
    BackHandler(
        enabled = state !is AuthUiState.Checking &&
            state !is AuthUiState.Welcome &&
            state !is AuthUiState.Guest,
        onBack = onCancel
    )
    when (state) {
        AuthUiState.Checking -> LoginCheckingScreen(showProgress = false)
        AuthUiState.Welcome -> LoginWelcomeScreen(
            onLogin = onLogin,
            onGuest = onGuest,
            featuredAnime = welcomeAnime
        )
        AuthUiState.Guest -> LoginWelcomeScreen(
            onLogin = onLogin,
            onGuest = onGuest,
            featuredAnime = welcomeAnime
        )
        AuthUiState.RequestingCode -> LoginCheckingScreen(showProgress = true)
        else -> DeviceCodeLoginScreen(state = state, onRetry = onRetry)
    }
}

@Composable
private fun LoginCheckingScreen(showProgress: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .aulamaTvBackground()
    ) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(32.dp)
        ) {
            AulamaAnimeBrandMark(height = 180.dp)
            if (showProgress) LoginProgressIndicator()
        }
    }
}

@Composable
private fun LoginProgressIndicator() {
    AulamaLoadingPulse()
}

/**
 * First-run choice between Aulama ID and guest mode.
 *
 * Every focus target sits at a fixed position: the rotating anime caption is anchored to the
 * bottom-right corner, so changing titles never shift the choice cards under the user's focus.
 * Animated values are read only in draw/graphicsLayer lambdas to keep Android 5/6 boxes smooth.
 */
@Composable
private fun LoginWelcomeScreen(
    onLogin: () -> Unit,
    onGuest: () -> Unit,
    featuredAnime: List<AnimeData>
) {
    val loginFocusRequester = remember { FocusRequester() }
    val language = LocalTvLanguage.current
    val copy = remember(language) { welcomeCopy(language) }
    var featuredIndex by remember { mutableStateOf(0) }
    LaunchedEffect(featuredAnime) {
        featuredIndex = 0
        while (featuredAnime.size > 1) {
            delay(WELCOME_ROTATION_INTERVAL_MS)
            featuredIndex = (featuredIndex + 1) % featuredAnime.size
        }
    }
    val selectedAnime = featuredAnime.getOrNull(featuredIndex)
    val extractedArtworkAccent = rememberArtworkAccent(selectedAnime?.imageUrl.orEmpty())
    val reducedMotion = rememberReducedMotion()
    val artworkAccentState = animateColorAsState(
        targetValue = extractedArtworkAccent,
        animationSpec = tween(
            durationMillis = if (reducedMotion) 0 else WELCOME_ACCENT_TRANSITION_MS,
            easing = AulamaMotion.Standard
        ),
        label = "welcome-artwork-accent"
    )
    val artworkAccent = remember(artworkAccentState) { { artworkAccentState.value } }
    val guestAccent = remember { { Color.White.copy(alpha = 0.9f) } }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .aulamaTvBackground()
    ) {
        WelcomeAnimeBackdrop(
            anime = featuredAnime,
            selectedIndex = featuredIndex,
            accent = artworkAccent,
            eyebrow = copy.eyebrow,
            reducedMotion = reducedMotion
        )
        AulamaAnimeBrandMark(
            height = 48.dp,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = WelcomeContentStart, top = 32.dp)
                .lightweightEntrance(transitionKey = Unit, reducedMotion = reducedMotion)
        )
        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = WelcomeContentStart, top = 48.dp)
                .width(WelcomeChoiceWidth * 2 + WelcomeChoiceGap)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.lightweightEntrance(
                    transitionKey = Unit,
                    reducedMotion = reducedMotion,
                    delayMillis = 60
                )
            ) {
                Spacer(
                    Modifier
                        .size(width = 22.dp, height = 3.dp)
                        .drawBehind {
                            drawRoundRect(
                                color = artworkAccent(),
                                cornerRadius = CornerRadius(size.height / 2f)
                            )
                        }
                )
                Spacer(Modifier.width(9.dp))
                Text(
                    text = copy.greeting,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontSize = 15.sp,
                        lineHeight = 20.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = AulamaTvColors.TextSecondary,
                    maxLines = 1
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = copy.slogan,
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontSize = 34.sp,
                    lineHeight = 40.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = AulamaTvColors.TextPrimary,
                maxLines = 2,
                modifier = Modifier.lightweightEntrance(
                    transitionKey = Unit,
                    reducedMotion = reducedMotion,
                    delayMillis = 100
                )
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = copy.message,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 16.sp,
                    lineHeight = 22.sp
                ),
                color = AulamaTvColors.TextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Clip,
                modifier = Modifier.lightweightEntrance(
                    transitionKey = Unit,
                    reducedMotion = reducedMotion,
                    delayMillis = 140
                )
            )
            Spacer(Modifier.height(28.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(WelcomeChoiceGap),
                modifier = Modifier.height(IntrinsicSize.Min)
            ) {
                WelcomeChoiceCard(
                    title = copy.loginButton,
                    description = copy.loginDescription,
                    hint = copy.loginHint,
                    icon = Icons.AutoMirrored.Filled.Login,
                    hintIcon = Icons.Default.QrCode2,
                    badge = copy.recommendedBadge,
                    accent = artworkAccent,
                    reducedMotion = reducedMotion,
                    onClick = onLogin,
                    modifier = Modifier
                        .width(WelcomeChoiceWidth)
                        .fillMaxHeight()
                        .lightweightEntrance(
                            transitionKey = Unit,
                            reducedMotion = reducedMotion,
                            delayMillis = 180,
                            offsetY = 14.dp
                        )
                        .focusRequester(loginFocusRequester)
                )
                WelcomeChoiceCard(
                    title = copy.guestButton,
                    description = copy.guestDescription,
                    hint = copy.guestHint,
                    icon = Icons.Default.Person,
                    hintIcon = Icons.Default.Sync,
                    accent = guestAccent,
                    reducedMotion = reducedMotion,
                    onClick = onGuest,
                    modifier = Modifier
                        .width(WelcomeChoiceWidth)
                        .fillMaxHeight()
                        .lightweightEntrance(
                            transitionKey = Unit,
                            reducedMotion = reducedMotion,
                            delayMillis = 230,
                            offsetY = 14.dp
                        )
                )
            }
        }
        LaunchedEffect(Unit) { loginFocusRequester.safelyRequestFocus("welcome-login") }
    }
}

@Composable
private fun WelcomeChoiceCard(
    title: String,
    description: String,
    hint: String,
    icon: ImageVector,
    hintIcon: ImageVector,
    accent: () -> Color,
    reducedMotion: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badge: String? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    var pressed by remember { mutableStateOf(false) }
    val currentOnClick by rememberUpdatedState(onClick)
    LaunchedEffect(focused) { if (!focused) pressed = false }
    val focusProgress = animateFloatAsState(
        targetValue = if (focused) 1f else 0f,
        animationSpec = AulamaMotion.focusSpec(focused, reducedMotion),
        label = "welcome-choice-focus"
    )
    val pressScale = animateFloatAsState(
        targetValue = if (pressed) AulamaMotion.PressedScale else 1f,
        animationSpec = tween(if (reducedMotion) 0 else AulamaMotion.PressMillis),
        label = "welcome-choice-press"
    )
    Column(
        modifier = modifier
            .heightIn(min = 168.dp)
            .graphicsLayer {
                val scale = 1f + (AulamaMotion.ChoiceFocusScale - 1f) * focusProgress.value
                scaleX = scale * pressScale.value
                scaleY = scale * pressScale.value
            }
            .drawBehind { drawWelcomeChoiceChrome(focusProgress.value, accent()) }
            .semantics(mergeDescendants = true) {
                role = Role.Button
                this.onClick(action = { currentOnClick(); true })
            }
            // Plain focusable (not clickable): clickable only takes focus in non-touch mode, and
            // some legacy Android TV boxes boot in touch mode, which would leave no default focus.
            .onPreviewKeyEvent { event ->
                if (event.nativeKeyEvent.keyCode !in WelcomeClickKeyCodes) {
                    return@onPreviewKeyEvent false
                }
                when (event.type) {
                    KeyEventType.KeyDown -> pressed = true
                    KeyEventType.KeyUp -> {
                        // Require the matching key-down so a key-up carried over from the
                        // previous screen can never trigger a choice by itself.
                        if (pressed) {
                            pressed = false
                            currentOnClick()
                        }
                    }
                }
                true
            }
            .pointerInput(Unit) { detectTapGestures { currentOnClick() } }
            .focusable(interactionSource = interactionSource)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            WelcomeChoiceIcon(icon = icon, accent = accent, focusProgress = focusProgress)
            Spacer(Modifier.weight(1f))
            if (badge != null) {
                Text(
                    text = badge,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = AulamaTvColors.TextPrimary,
                    maxLines = 1,
                    modifier = Modifier
                        .drawBehind {
                            drawRoundRect(
                                color = accent().copy(alpha = 0.26f),
                                cornerRadius = CornerRadius(size.height / 2f)
                            )
                        }
                        .padding(horizontal = 9.dp, vertical = 3.dp)
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge.copy(
                fontSize = 18.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.Bold
            ),
            color = AulamaTvColors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Clip
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 14.sp,
                lineHeight = 19.sp
            ),
            color = AulamaTvColors.TextSecondary,
            maxLines = 2,
            overflow = TextOverflow.Clip,
            modifier = Modifier.graphicsLayer { alpha = 0.78f + 0.22f * focusProgress.value }
        )
        Spacer(Modifier.weight(1f))
        Spacer(Modifier.height(10.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.graphicsLayer { alpha = 0.62f + 0.38f * focusProgress.value }
        ) {
            Icon(
                imageVector = hintIcon,
                contentDescription = null,
                tint = AulamaTvColors.TextSecondary,
                modifier = Modifier.size(15.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = hint,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontSize = 13.sp,
                    lineHeight = 17.sp
                ),
                color = AulamaTvColors.TextSecondary,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun WelcomeChoiceIcon(
    icon: ImageVector,
    accent: () -> Color,
    focusProgress: State<Float>
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .drawBehind {
                drawCircle(
                    color = lerp(Color.White.copy(alpha = 0.08f), accent(), focusProgress.value)
                )
            },
        contentAlignment = Alignment.Center
    ) {
        // Two pre-tinted icons cross-fade so the tint change never recomposes the card.
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = AulamaTvColors.TextPrimary,
            modifier = Modifier
                .size(20.dp)
                .graphicsLayer { alpha = 1f - focusProgress.value }
        )
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = WelcomeOnAccent,
            modifier = Modifier
                .size(20.dp)
                .graphicsLayer { alpha = focusProgress.value }
        )
    }
}

private fun DrawScope.drawWelcomeChoiceChrome(progress: Float, accent: Color) {
    val radius = WelcomeChoiceCorner.toPx()
    if (progress > 0f) {
        // Layered halo instead of a blurred glow: blur effects need Android 12, shadows look
        // muddy on dark backgrounds, and two translucent rects cost nothing on old GPUs.
        drawHalo(spread = 9.dp.toPx(), radius = radius, color = accent.copy(alpha = 0.07f * progress))
        drawHalo(spread = 4.dp.toPx(), radius = radius, color = accent.copy(alpha = 0.16f * progress))
    }
    drawRoundRect(
        color = lerp(WelcomeChoiceRestContainer, WelcomeChoiceFocusedContainer, progress),
        cornerRadius = CornerRadius(radius)
    )
    val strokeWidth = (1f + progress).dp.toPx()
    val inset = strokeWidth / 2f
    drawRoundRect(
        color = lerp(AulamaTvColors.Outline.copy(alpha = 0.8f), accent, progress),
        topLeft = Offset(inset, inset),
        size = Size(size.width - strokeWidth, size.height - strokeWidth),
        cornerRadius = CornerRadius(radius - inset),
        style = Stroke(width = strokeWidth)
    )
}

private fun DrawScope.drawHalo(spread: Float, radius: Float, color: Color) {
    drawRoundRect(
        color = color,
        topLeft = Offset(-spread, -spread),
        size = Size(size.width + spread * 2f, size.height + spread * 2f),
        cornerRadius = CornerRadius(radius + spread)
    )
}

@Composable
private fun WelcomeAnimeBackdrop(
    anime: List<AnimeData>,
    selectedIndex: Int,
    accent: () -> Color,
    eyebrow: String,
    reducedMotion: Boolean
) {
    val selectedAnime = anime.getOrNull(selectedIndex)
    val context = LocalContext.current
    val transitionDuration = if (reducedMotion) 0 else AulamaMotion.BackdropMillis
    val nextAnime = selectedAnime?.let {
        anime.getOrNull((selectedIndex + 1) % anime.size)
            ?.takeUnless { next -> next.imageUrl == it.imageUrl }
    }
    if (nextAnime != null) {
        val nextRequest = rememberPosterImageRequest(
            imageUrl = nextAnime.imageUrl,
            widthPx = 960,
            heightPx = 1_440
        )
        LaunchedEffect(nextRequest) { context.imageLoader.execute(nextRequest) }
    }
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val artworkWidth = minOf(maxWidth * 0.5f, maxHeight * WELCOME_ARTWORK_ASPECT)
        val artworkStart = ((maxWidth - artworkWidth) / maxWidth).coerceIn(0f, 0.7f)
        if (selectedAnime != null) {
            AnimatedContent(
                targetState = selectedAnime,
                contentKey = { item -> item.imageUrl },
                transitionSpec = {
                    (fadeIn(tween(transitionDuration, easing = AulamaMotion.Standard)) +
                        scaleIn(
                            animationSpec = tween(
                                transitionDuration,
                                easing = AulamaMotion.EmphasizedDecelerate
                            ),
                            initialScale = 1.04f
                        ))
                        .togetherWith(fadeOut(tween(transitionDuration, easing = AulamaMotion.Standard)))
                },
                label = "welcome-anime-backdrop",
                modifier = Modifier.fillMaxSize()
            ) { item ->
                Box(modifier = Modifier.fillMaxSize()) {
                    AsyncImage(
                        model = rememberPosterImageRequest(
                            imageUrl = item.imageUrl,
                            widthPx = 960,
                            heightPx = 1_440
                        ),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .width(artworkWidth)
                            .fillMaxHeight()
                    )
                }
            }
        }
        // Gradient scrims replace the previous offscreen DstIn mask, which allocated a
        // full-height offscreen buffer per poster and dropped frames on Android 6 GPUs.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawWithCache {
                    val background = AulamaTvColors.Background
                    val horizontal = Brush.horizontalGradient(
                        colorStops = arrayOf(
                            0f to background.copy(alpha = 0.6f),
                            artworkStart * 0.7f to background.copy(alpha = 0.9f),
                            artworkStart to background,
                            artworkStart + 0.07f to background.copy(alpha = 0.7f),
                            artworkStart + 0.15f to background.copy(alpha = 0.3f),
                            artworkStart + 0.24f to background.copy(alpha = 0.08f),
                            artworkStart + 0.3f to Color.Transparent
                        )
                    )
                    val vertical = Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to background.copy(alpha = 0.3f),
                            0.22f to Color.Transparent,
                            0.56f to Color.Transparent,
                            1f to background.copy(alpha = 0.94f)
                        )
                    )
                    onDrawBehind {
                        drawRect(horizontal)
                        drawRect(vertical)
                        val glow = accent()
                        drawRect(
                            Brush.radialGradient(
                                colors = listOf(
                                    glow.copy(alpha = 0.1f),
                                    glow.copy(alpha = 0.04f),
                                    Color.Transparent
                                ),
                                center = Offset(size.width * 0.82f, size.height * 0.42f),
                                radius = size.width * 0.55f
                            )
                        )
                    }
                }
        )
        if (selectedAnime != null) {
            AnimatedContent(
                targetState = selectedAnime,
                contentKey = { item -> "${item.sourceId}:${item.id}:${item.imageUrl}" },
                transitionSpec = {
                    (fadeIn(
                        tween(
                            durationMillis = if (reducedMotion) 0 else 420,
                            delayMillis = if (reducedMotion) 0 else 180,
                            easing = AulamaMotion.Standard
                        )
                    ) + slideInVertically(
                        animationSpec = tween(
                            durationMillis = if (reducedMotion) 0 else 520,
                            delayMillis = if (reducedMotion) 0 else 180,
                            easing = AulamaMotion.EmphasizedDecelerate
                        ),
                        initialOffsetY = { height -> height / 6 }
                    )).togetherWith(fadeOut(tween(if (reducedMotion) 0 else 200)))
                },
                label = "welcome-anime-caption",
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 48.dp, bottom = 54.dp)
                    .widthIn(max = 300.dp)
            ) { item ->
                WelcomeAnimeCaption(eyebrow = eyebrow, title = localizedText(item.title))
            }
            if (anime.size > 1) {
                WelcomeRotationIndicator(
                    count = anime.size,
                    selectedIndex = selectedIndex,
                    accent = accent,
                    reducedMotion = reducedMotion,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 48.dp, bottom = 36.dp)
                )
            }
        }
    }
}

@Composable
private fun WelcomeAnimeCaption(eyebrow: String, title: String) {
    val titleLayout = remember(title) { welcomeTitleLayout(title) }
    Column(horizontalAlignment = Alignment.End) {
        Text(
            text = eyebrow,
            style = MaterialTheme.typography.labelLarge.copy(
                fontSize = 14.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.Bold
            ),
            color = AulamaTvColors.TextSecondary,
            maxLines = 1
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall.copy(
                fontSize = titleLayout.fontSizeSp.sp,
                lineHeight = titleLayout.lineHeightSp.sp,
                fontWeight = FontWeight.ExtraBold
            ),
            color = AulamaTvColors.TextPrimary,
            textAlign = TextAlign.End,
            maxLines = titleLayout.maxLines,
            overflow = TextOverflow.Clip
        )
    }
}

@Composable
private fun WelcomeRotationIndicator(
    count: Int,
    selectedIndex: Int,
    accent: () -> Color,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier
) {
    val activePosition = animateFloatAsState(
        targetValue = selectedIndex.toFloat(),
        animationSpec = tween(
            durationMillis = if (reducedMotion) 0 else 480,
            easing = AulamaMotion.Standard
        ),
        label = "welcome-rotation-indicator"
    )
    val dot = 6.dp
    val gap = 6.dp
    val activeExtra = 14.dp
    Canvas(
        modifier = modifier.size(
            width = dot * count + gap * (count - 1) + activeExtra,
            height = 4.dp
        )
    ) {
        val dotPx = dot.toPx()
        val gapPx = gap.toPx()
        val extraPx = activeExtra.toPx()
        val active = activePosition.value
        val activeColor = accent()
        var x = 0f
        for (index in 0 until count) {
            val weight = (1f - abs(active - index)).coerceIn(0f, 1f)
            val width = dotPx + extraPx * weight
            drawRoundRect(
                color = lerp(Color.White.copy(alpha = 0.28f), activeColor, weight),
                topLeft = Offset(x, 0f),
                size = Size(width, size.height),
                cornerRadius = CornerRadius(size.height / 2f)
            )
            x += width + gapPx
        }
    }
}

@Composable
private fun DeviceCodeLoginScreen(
    state: AuthUiState,
    onRetry: () -> Unit
) {
    val loginTitle = welcomeCopy(LocalTvLanguage.current).loginButton
    val reducedMotion = rememberReducedMotion()
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .aulamaTvBackground()
    ) {
        val compactLayout = maxWidth < 1500.dp || maxHeight < 850.dp
        val qrSize = DeviceLoginLayoutPolicy.qrSizeDp(
            availableWidthDp = maxWidth.value,
            availableHeightDp = maxHeight.value
        ).dp
        val horizontalPadding = if (compactLayout) 48.dp else 72.dp
        val verticalPadding = if (compactLayout) 32.dp else 48.dp

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = horizontalPadding, vertical = verticalPadding),
            horizontalArrangement = Arrangement.spacedBy(if (compactLayout) 40.dp else 72.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(0.82f)
                    .lightweightEntrance(transitionKey = Unit, reducedMotion = reducedMotion),
                verticalArrangement = Arrangement.spacedBy(if (compactLayout) 10.dp else 16.dp)
            ) {
                AulamaAnimeBrandMark(height = if (compactLayout) 44.dp else 56.dp)
                Spacer(Modifier.size(if (compactLayout) 2.dp else 6.dp))
                Text(
                    text = loginTitle,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = if (compactLayout) 17.sp else 19.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = AulamaTvColors.Cyan
                )
                Text(
                    text = localizedText("將你的片庫\n帶到大螢幕"),
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontSize = if (compactLayout) 38.sp else 44.sp,
                        lineHeight = if (compactLayout) 44.sp else 50.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = AulamaTvColors.TextPrimary
                )
                Text(
                    text = localizedText("收藏、觀看進度與個人化推薦\n都會自動同步。"),
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = if (compactLayout) 18.sp else 20.sp,
                        lineHeight = if (compactLayout) 25.sp else 28.sp
                    ),
                    color = AulamaTvColors.TextSecondary
                )
                Spacer(Modifier.size(if (compactLayout) 2.dp else 6.dp))
                LoginStep(number = "1", text = "用手機掃描 QR Code", compact = compactLayout)
                LoginStep(number = "2", text = "確認你的 Aulama ID", compact = compactLayout)
                LoginStep(number = "3", text = "電視會自動完成登入", compact = compactLayout)
                Spacer(Modifier.size(if (compactLayout) 4.dp else 8.dp))
                LoginBackHint()
            }

            Box(
                modifier = Modifier
                    .weight(1.18f)
                    .lightweightEntrance(
                        transitionKey = Unit,
                        reducedMotion = reducedMotion,
                        delayMillis = 90,
                        offsetY = 14.dp
                    ),
                contentAlignment = Alignment.Center
            ) {
                LoginStatePanel(
                    state = state,
                    onRetry = onRetry,
                    compact = compactLayout,
                    qrSize = qrSize,
                    modifier = Modifier.widthIn(max = if (compactLayout) 700.dp else 760.dp)
                )
            }
        }
    }
}

@Composable
private fun LoginBackHint() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardReturn,
            contentDescription = null,
            tint = AulamaTvColors.TextSecondary.copy(alpha = 0.72f),
            modifier = Modifier.size(17.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = localizedText("按遙控器「返回」鍵可回到上一頁"),
            color = AulamaTvColors.TextSecondary.copy(alpha = 0.72f),
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, lineHeight = 20.sp),
            maxLines = 1
        )
    }
}

@Composable
private fun LoginStep(number: String, text: String, compact: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(if (compact) 26.dp else 28.dp)
                .background(AulamaTvColors.Cyan.copy(alpha = 0.16f), CircleShape)
                .border(1.dp, AulamaTvColors.Cyan.copy(alpha = 0.42f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = number,
                color = AulamaTvColors.Cyan,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontSize = if (compact) 16.sp else 17.sp,
                    fontWeight = FontWeight.Bold
                )
            )
        }
        Text(
            text = localizedText(text),
            color = AulamaTvColors.TextSecondary,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontSize = if (compact) 17.sp else 19.sp
            )
        )
    }
}

@Composable
private fun LoginStatePanel(
    state: AuthUiState,
    onRetry: () -> Unit,
    compact: Boolean,
    qrSize: Dp,
    modifier: Modifier = Modifier
) {
    val code = when (state) {
        is AuthUiState.Waiting -> state.code
        is AuthUiState.RateLimited -> state.code
        is AuthUiState.Expired -> state.code
        else -> null
    }
    val remaining = when (state) {
        is AuthUiState.Waiting -> state.remainingSeconds
        is AuthUiState.RateLimited -> state.remainingSeconds
        else -> 0L
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(AulamaTvColors.Surface.copy(alpha = 0.92f))
            .border(BorderStroke(1.dp, AulamaTvColors.Outline), RoundedCornerShape(14.dp))
            .padding(
                horizontal = if (compact) 24.dp else 32.dp,
                vertical = if (compact) 22.dp else 30.dp
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(if (compact) 12.dp else 16.dp)
    ) {
        if (code != null) {
            DeviceCodeContent(code, remaining, compact, qrSize)
        }

        val status: String? = when (state) {
            AuthUiState.Checking,
            AuthUiState.Welcome,
            AuthUiState.Guest,
            AuthUiState.RequestingCode -> null
            is AuthUiState.Waiting -> if (state.pending) "等待你確認登入" else "裝置碼已準備好"
            is AuthUiState.RateLimited -> "每小時最多嘗試 3 次，請於 ${state.retryAfterSeconds} 秒後重試"
            is AuthUiState.Expired -> "裝置碼已過期"
            is AuthUiState.Error -> state.message
            is AuthUiState.Authenticated -> "登入成功"
        }
        val statusIsError = state is AuthUiState.Error || state is AuthUiState.Expired
        val reducedMotion = rememberReducedMotion()
        AnimatedContent(
            targetState = Triple(status, statusIsError, state::class),
            // Fade only when the kind of status changes; the rate-limit countdown ticks every
            // second and should update in place rather than flash.
            contentKey = { (displayedStatus, _, kind) -> if (displayedStatus == null) null else kind },
            transitionSpec = {
                fadeIn(tween(if (reducedMotion) 0 else 220, easing = AulamaMotion.Standard))
                    .togetherWith(fadeOut(tween(if (reducedMotion) 0 else 140)))
            },
            label = "device-login-status"
        ) { (displayedStatus, isError, _) ->
            if (displayedStatus != null) {
                Text(
                    text = localizedText(displayedStatus),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isError) AulamaTvColors.Pink else AulamaTvColors.TextSecondary,
                    textAlign = TextAlign.Center
                )
            }
        }

        if (state is AuthUiState.Expired || state is AuthUiState.Error || state is AuthUiState.RateLimited) {
            AnimatedLoginButton(
                label = "重新取得裝置碼",
                onClick = onRetry,
                enabled = state !is AuthUiState.RateLimited
            )
        }
    }
}

@Composable
private fun DeviceCodeContent(
    code: DeviceCode,
    remainingSeconds: Long,
    compact: Boolean,
    qrSize: Dp
) {
    val approvalUrl = remember(code.verificationUri, code.userCode) {
        "${code.verificationUri}?code=${code.userCode}"
    }
    val qrCode = remember(approvalUrl) {
        val matrix = QRCodeWriter().encode(approvalUrl, BarcodeFormat.QR_CODE, 420, 420)
        val width = matrix.width
        val height = matrix.height
        // A single bulk setPixels call; per-pixel setPixel took ~176k JNI calls on old TV CPUs.
        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            val offset = y * width
            for (x in 0 until width) {
                pixels[offset + x] = if (matrix[x, y]) {
                    android.graphics.Color.BLACK
                } else {
                    android.graphics.Color.WHITE
                }
            }
        }
        Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565).also { bitmap ->
            bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(if (compact) 20.dp else 26.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = qrCode,
            contentDescription = localizedText("掃描 QR Code 登入"),
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .size(qrSize)
                .background(Color.White, RoundedCornerShape(10.dp))
                .padding(if (compact) 8.dp else 10.dp)
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = localizedText("掃描或輸入裝置碼"),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = if (compact) 18.sp else 20.sp
                ),
                color = AulamaTvColors.TextSecondary
            )
            Text(
                text = code.userCode,
                fontSize = when {
                    code.userCode.length > 10 -> if (compact) 29.sp else 34.sp
                    code.userCode.length > 8 -> if (compact) 34.sp else 38.sp
                    else -> if (compact) 40.sp else 44.sp
                },
                lineHeight = if (compact) 44.sp else 48.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.sp,
                color = AulamaTvColors.TextPrimary,
                maxLines = 1,
                softWrap = false
            )
            Text(
                text = code.verificationUri,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = if (compact) 16.sp else 18.sp,
                    lineHeight = if (compact) 21.sp else 23.sp
                ),
                color = AulamaTvColors.Cyan,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.tv.material3.Icon(
                    imageVector = Icons.Default.Timer,
                    contentDescription = null,
                    tint = AulamaTvColors.Amber,
                    modifier = Modifier.size(21.dp)
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    text = localizedText(
                        "%d:%02d 後失效".format(remainingSeconds / 60, remainingSeconds % 60)
                    ),
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
                    color = AulamaTvColors.TextPrimary
                )
            }
        }
    }
}

@Composable
private fun AnimatedLoginButton(label: String, onClick: () -> Unit, enabled: Boolean) {
    val focusRequester = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.04f else 1f,
        animationSpec = tween(180),
        label = "login-button-scale"
    )
    val alpha by animateFloatAsState(
        targetValue = if (focused) 1f else 0.9f,
        animationSpec = tween(180),
        label = "login-button-alpha"
    )
    AulamaActionButton(
        label = label,
        icon = Icons.Default.Refresh,
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .heightIn(min = 48.dp)
            .focusRequester(focusRequester)
            .onFocusChanged { focused = it.hasFocus }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
            }
    )
    LaunchedEffect(enabled) {
        if (enabled) focusRequester.requestFocus()
    }
}

@Composable
fun AccountDialog(
    account: AulamaAccount?,
    language: TvLanguage,
    previewEnabled: Boolean,
    updateChannel: TvUpdateChannel,
    isCheckingForUpdate: Boolean,
    currentVersion: String,
    onLanguageChange: (TvLanguage) -> Unit,
    onPreviewEnabledChange: (Boolean) -> Unit,
    onUpdateChannelChange: (TvUpdateChannel) -> Unit,
    onCheckForUpdate: () -> Unit,
    onDismiss: () -> Unit,
    onLogin: () -> Unit,
    onLogout: () -> Unit
) {
    val dismissFocus = remember { FocusRequester() }
    val dialogShape = RoundedCornerShape(20.dp)
    val displayName = account?.name ?: localizedText("遊客模式")
    val accountStatus = when {
        !account?.email.isNullOrBlank() -> account?.email.orEmpty()
        account != null -> account.role.orEmpty()
        else -> localizedText("本機保存 · 登入後可跨裝置同步")
    }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .width(500.dp)
                .clip(dialogShape)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xF2182230),
                            AulamaTvColors.Surface.copy(alpha = 0.90f)
                        )
                    )
                )
                .border(1.dp, Color.White.copy(alpha = 0.18f), dialogShape)
                .padding(horizontal = 22.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(
                modifier = Modifier
                    .size(width = 84.dp, height = 3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                AulamaTvColors.Cyan,
                                AulamaTvColors.Blue,
                                Color.Transparent
                            )
                        )
                    )
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (account != null) {
                    AulamaAccountAvatar(
                        account = account,
                        modifier = Modifier
                            .size(58.dp)
                            .border(2.dp, AulamaTvColors.Cyan.copy(alpha = 0.55f), CircleShape)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(58.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    listOf(
                                        AulamaTvColors.Cyan.copy(alpha = 0.20f),
                                        AulamaTvColors.Surface
                                    )
                                )
                            )
                            .border(2.dp, AulamaTvColors.Cyan.copy(alpha = 0.55f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        androidx.tv.material3.Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = AulamaTvColors.TextPrimary,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontSize = 25.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = AulamaTvColors.TextPrimary,
                        maxLines = 1
                    )
                    Text(
                        text = accountStatus,
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                        color = if (account == null) AulamaTvColors.Cyan else AulamaTvColors.TextSecondary,
                        maxLines = 1
                    )
                }
            }
            AccountSectionLabel(label = "偏好設定", accent = AulamaTvColors.Cyan)
            AulamaActionButton(
                label = "介面語言",
                trailingLabel = if (language == TvLanguage.Traditional) "繁體中文" else "簡體中文",
                icon = Icons.Default.Language,
                accent = if (language == TvLanguage.Traditional) AulamaTvColors.Cyan else AulamaTvColors.Blue,
                onClick = {
                    onLanguageChange(
                        if (language == TvLanguage.Traditional) {
                            TvLanguage.Simplified
                        } else {
                            TvLanguage.Traditional
                        }
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                labelFontSize = 15.sp,
                iconSize = 18.dp,
                contentHeight = 44.dp
            )
            AulamaActionButton(
                label = "自動播放預覽",
                trailingLabel = if (previewEnabled) "開啟" else "關閉",
                icon = Icons.Default.Timer,
                accent = if (previewEnabled) AulamaTvColors.Green else AulamaTvColors.Blue,
                onClick = { onPreviewEnabledChange(!previewEnabled) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                labelFontSize = 15.sp,
                iconSize = 18.dp,
                contentHeight = 44.dp
            )
            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color.White.copy(alpha = 0.10f))
            )
            AccountSectionLabel(
                label = "應用程式更新",
                trailing = "v$currentVersion",
                accent = if (updateChannel == TvUpdateChannel.Preview) {
                    AulamaTvColors.Amber
                } else {
                    AulamaTvColors.Green
                }
            )
            AulamaActionButton(
                label = "更新通道",
                trailingLabel = if (updateChannel == TvUpdateChannel.Preview) "搶先版" else "正式版",
                accent = if (updateChannel == TvUpdateChannel.Preview) {
                    AulamaTvColors.Amber
                } else {
                    AulamaTvColors.Green
                },
                onClick = { onUpdateChannelChange(updateChannel.toggled()) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                labelFontSize = 15.sp,
                contentHeight = 44.dp
            )
            AulamaActionButton(
                label = if (isCheckingForUpdate) "正在檢查更新" else "檢查更新",
                trailingLabel = "v$currentVersion",
                icon = Icons.Default.SystemUpdateAlt,
                enabled = !isCheckingForUpdate,
                accent = AulamaTvColors.Cyan,
                onClick = onCheckForUpdate,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                labelFontSize = 15.sp,
                iconSize = 18.dp,
                contentHeight = 44.dp
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)
            ) {
                AulamaActionButton(
                    label = "返回",
                    onClick = onDismiss,
                    modifier = Modifier.height(46.dp).focusRequester(dismissFocus),
                    contentHeight = 46.dp
                )
                AulamaActionButton(
                    label = if (account == null) "登入並跨裝置同步" else "登出",
                    icon = if (account == null) Icons.AutoMirrored.Filled.Login else Icons.AutoMirrored.Filled.Logout,
                    accent = if (account == null) AulamaTvColors.Cyan else AulamaTvColors.Pink,
                    onClick = if (account == null) onLogin else onLogout,
                    modifier = Modifier.height(46.dp),
                    contentHeight = 46.dp
                )
            }
        }
        LaunchedEffect(Unit) { dismissFocus.requestFocus() }
    }
}

@Composable
private fun AccountSectionLabel(
    label: String,
    accent: Color,
    trailing: String? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(
            modifier = Modifier
                .size(width = 4.dp, height = 18.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(accent)
        )
        Spacer(Modifier.width(9.dp))
        Text(
            text = localizedText(label),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleSmall.copy(
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            ),
            color = AulamaTvColors.TextSecondary
        )
        if (trailing != null) {
            Text(
                text = trailing,
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp),
                color = accent
            )
        }
    }
}
