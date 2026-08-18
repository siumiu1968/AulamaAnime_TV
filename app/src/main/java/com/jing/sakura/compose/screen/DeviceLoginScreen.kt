@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.jing.sakura.compose.screen

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.ExperimentalTvMaterial3Api
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
import com.jing.sakura.compose.common.AulamaTvColors
import com.jing.sakura.compose.common.LocalTvLanguage
import com.jing.sakura.compose.common.TvLanguage
import com.jing.sakura.compose.common.localizedText
import com.jing.sakura.compose.common.aulamaTvBackground
import com.jing.sakura.compose.common.rememberArtworkAccent
import com.jing.sakura.compose.common.rememberPosterImageRequest
import com.jing.sakura.compose.common.rememberReducedMotion
import com.jing.sakura.data.AnimeData
import kotlinx.coroutines.delay

private const val WELCOME_ROTATION_INTERVAL_MS = 8_000L
private const val WELCOME_TRANSITION_MS = 620

internal data class WelcomeCopy(
    val eyebrow: String,
    val slogan: String,
    val message: String,
    val loginButton: String,
    val guestButton: String,
    val guestNote: String
)

internal data class WelcomeTitleLayout(
    val fontSizeSp: Int,
    val lineHeightSp: Int,
    val maxLines: Int
)

internal fun welcomeTitleLayout(title: String, compact: Boolean): WelcomeTitleLayout {
    val visibleLength = title.count { !it.isWhitespace() }
    return when {
        visibleLength <= 22 -> WelcomeTitleLayout(
            fontSizeSp = if (compact) 31 else 37,
            lineHeightSp = if (compact) 37 else 43,
            maxLines = 2
        )
        visibleLength <= 48 -> WelcomeTitleLayout(
            fontSizeSp = if (compact) 24 else 29,
            lineHeightSp = if (compact) 29 else 35,
            maxLines = 3
        )
        else -> WelcomeTitleLayout(
            fontSizeSp = if (compact) 20 else 24,
            lineHeightSp = if (compact) 24 else 29,
            maxLines = 4
        )
    }
}

internal fun welcomeCopy(language: TvLanguage): WelcomeCopy = when (language) {
    TvLanguage.Traditional -> WelcomeCopy(
        eyebrow = "本季焦點",
        slogan = "讓每一段精彩，都在大螢幕綻放。",
        message = "登入 Aulama ID，即可跨裝置同步收藏、觀看進度與個人化推薦。",
        loginButton = "使用 Aulama ID 登入",
        guestButton = "免登入使用",
        guestNote = "遊客資料只會儲存在這部電視；登入後即可跨裝置同步。"
    )
    TvLanguage.Simplified -> WelcomeCopy(
        eyebrow = "本季焦点",
        slogan = "让每一段精彩，都在大屏幕绽放。",
        message = "登录 Aulama ID，即可跨设备同步收藏、观看进度与个性化推荐。",
        loginButton = "使用 Aulama ID 登录",
        guestButton = "免登录使用",
        guestNote = "游客数据只会保存在这台电视；登录后即可跨设备同步。"
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
    val artworkAccent by animateColorAsState(
        targetValue = extractedArtworkAccent,
        animationSpec = tween(
            durationMillis = if (reducedMotion) 0 else WELCOME_TRANSITION_MS,
            easing = FastOutSlowInEasing
        ),
        label = "welcome-artwork-accent"
    )
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .aulamaTvBackground()
    ) {
        val compactLayout = maxWidth < 1_300.dp || maxHeight < 760.dp
        WelcomeAnimeBackdrop(
            anime = featuredAnime,
            selectedIndex = featuredIndex,
            accent = artworkAccent
        )
        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .widthIn(max = if (compactLayout) 560.dp else 640.dp)
                .padding(start = if (compactLayout) 56.dp else 76.dp, end = 24.dp),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(if (compactLayout) 18.dp else 24.dp)
        ) {
            AulamaAnimeBrandMark(height = if (compactLayout) 104.dp else 128.dp)
            selectedAnime?.let { anime ->
                val localizedTitle = localizedText(anime.title)
                val titleLayout = remember(localizedTitle, compactLayout) {
                    welcomeTitleLayout(localizedTitle, compactLayout)
                }
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(
                        text = copy.eyebrow,
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontSize = if (compactLayout) 17.sp else 19.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = AulamaTvColors.TextSecondary
                    )
                    Text(
                        text = localizedTitle,
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontSize = titleLayout.fontSizeSp.sp,
                            lineHeight = titleLayout.lineHeightSp.sp,
                            fontWeight = FontWeight.ExtraBold
                        ),
                        color = artworkAccent,
                        maxLines = titleLayout.maxLines,
                        overflow = TextOverflow.Clip
                    )
                }
            }
            Text(
                text = copy.slogan,
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontSize = if (compactLayout) 38.sp else 46.sp,
                    lineHeight = if (compactLayout) 44.sp else 52.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = AulamaTvColors.TextPrimary,
                textAlign = TextAlign.Start
            )
            Text(
                text = copy.message,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = if (compactLayout) 17.sp else 20.sp,
                    lineHeight = if (compactLayout) 24.sp else 28.sp
                ),
                color = AulamaTvColors.TextSecondary,
                textAlign = TextAlign.Start
            )
            Spacer(Modifier.height(if (compactLayout) 4.dp else 8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AulamaActionButton(
                    label = copy.loginButton,
                    icon = Icons.AutoMirrored.Filled.Login,
                    accent = artworkAccent,
                    focusedBorderColor = Color.White.copy(alpha = 0.82f),
                    onClick = onLogin,
                    modifier = Modifier
                        .width(if (compactLayout) 250.dp else 280.dp)
                        .height(50.dp)
                        .focusRequester(loginFocusRequester),
                    centerLabel = true,
                    labelFontSize = 16.sp,
                    labelLineHeight = 20.sp,
                    iconSize = 19.dp,
                    contentHeight = 44.dp
                )
                AulamaActionButton(
                    label = copy.guestButton,
                    icon = Icons.Default.Person,
                    accent = Color.White.copy(alpha = 0.84f),
                    focusedBorderColor = Color.White.copy(alpha = 0.82f),
                    onClick = onGuest,
                    modifier = Modifier
                        .width(if (compactLayout) 168.dp else 184.dp)
                        .height(50.dp),
                    centerLabel = true,
                    labelFontSize = 15.sp,
                    labelLineHeight = 19.sp,
                    iconSize = 18.dp,
                    contentHeight = 44.dp
                )
            }
            Text(
                text = copy.guestNote,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = if (compactLayout) 13.sp else 15.sp,
                    lineHeight = if (compactLayout) 18.sp else 20.sp
                ),
                color = AulamaTvColors.TextSecondary.copy(alpha = 0.82f),
                maxLines = 2
            )
        }
        LaunchedEffect(Unit) { loginFocusRequester.requestFocus() }
    }
}

@Composable
private fun WelcomeAnimeBackdrop(anime: List<AnimeData>, selectedIndex: Int, accent: Color) {
    val selectedAnime = anime.getOrNull(selectedIndex) ?: return
    val context = LocalContext.current
    val reducedMotion = rememberReducedMotion()
    val transitionDuration = if (reducedMotion) 0 else WELCOME_TRANSITION_MS
    val nextAnime = anime.getOrNull((selectedIndex + 1) % anime.size)
        ?.takeUnless { it.imageUrl == selectedAnime.imageUrl }
    if (nextAnime != null) {
        val nextRequest = rememberPosterImageRequest(
            imageUrl = nextAnime.imageUrl,
            widthPx = 960,
            heightPx = 1_360
        )
        LaunchedEffect(nextRequest) { context.imageLoader.execute(nextRequest) }
    }
    Crossfade(
        targetState = selectedAnime,
        animationSpec = tween(
            durationMillis = transitionDuration,
            easing = FastOutSlowInEasing
        ),
        label = "welcome-anime-backdrop"
    ) { item ->
        val posterRequest = rememberPosterImageRequest(
            imageUrl = item.imageUrl,
            widthPx = 960,
            heightPx = 1_360
        )
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val artworkWidth = minOf(maxWidth * 0.52f, maxHeight * (2f / 3f))
            AsyncImage(
                model = posterRequest,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                alignment = Alignment.TopEnd,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .fillMaxHeight()
                    .width(artworkWidth)
                    .aspectRatio(2f / 3f, matchHeightConstraintsFirst = true)
                    .graphicsLayer {
                        alpha = 0.96f
                        compositingStrategy = CompositingStrategy.Offscreen
                    }
                    .drawWithContent {
                        drawContent()
                        drawRect(
                            brush = Brush.horizontalGradient(
                                colorStops = arrayOf(
                                    0f to Color.Transparent,
                                    0.025f to Color.Black.copy(alpha = 0.06f),
                                    0.05f to Color.Black.copy(alpha = 0.22f),
                                    0.08f to Color.Black.copy(alpha = 0.58f),
                                    0.115f to Color.Black.copy(alpha = 0.86f),
                                    0.15f to Color.Black,
                                    1f to Color.Black
                                )
                            ),
                            blendMode = BlendMode.DstIn
                        )
                    }
            )
        }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(
                    brush = Brush.horizontalGradient(
                        colorStops = arrayOf(
                            0f to Color.Transparent,
                            0.18f to accent.copy(alpha = 0.012f),
                            0.38f to accent.copy(alpha = 0.032f),
                            0.58f to accent.copy(alpha = 0.065f),
                            0.76f to accent.copy(alpha = 0.095f),
                            0.90f to accent.copy(alpha = 0.065f),
                            1f to accent.copy(alpha = 0.02f)
                        )
                    )
                )
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            accent.copy(alpha = 0.12f),
                            accent.copy(alpha = 0.092f),
                            accent.copy(alpha = 0.058f),
                            accent.copy(alpha = 0.026f),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.76f, size.height * 0.46f),
                        radius = size.width * 0.80f
                    )
                )
            }
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.horizontalGradient(
                    colorStops = arrayOf(
                        0f to AulamaTvColors.Background,
                        0.46f to AulamaTvColors.Background,
                        0.51f to AulamaTvColors.Background.copy(alpha = 0.94f),
                        0.57f to AulamaTvColors.Background.copy(alpha = 0.68f),
                        0.63f to AulamaTvColors.Background.copy(alpha = 0.34f),
                        0.69f to AulamaTvColors.Background.copy(alpha = 0.12f),
                        0.74f to AulamaTvColors.Background.copy(alpha = 0.03f),
                        0.78f to Color.Transparent,
                        1f to Color.Transparent
                    )
                )
            )
            .background(
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0f to AulamaTvColors.Background.copy(alpha = 0.16f),
                        0.58f to Color.Transparent,
                        1f to AulamaTvColors.Background
                    )
                )
            )
    )
}

@Composable
private fun DeviceCodeLoginScreen(
    state: AuthUiState,
    onRetry: () -> Unit
) {
    val loginTitle = welcomeCopy(LocalTvLanguage.current).loginButton
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
                modifier = Modifier.weight(0.82f),
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
                    text = localizedText("收藏、觀看進度與個人化推薦會自動同步。"),
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
            }

            Box(
                modifier = Modifier.weight(1.18f),
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
        status?.let {
            Text(
                text = localizedText(it),
                style = MaterialTheme.typography.titleMedium,
                color = if (state is AuthUiState.Error || state is AuthUiState.Expired) {
                    AulamaTvColors.Pink
                } else {
                    AulamaTvColors.TextSecondary
                },
                textAlign = TextAlign.Center
            )
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
        Bitmap.createBitmap(matrix.width, matrix.height, Bitmap.Config.RGB_565).also { bitmap ->
            for (x in 0 until matrix.width) {
                for (y in 0 until matrix.height) {
                    bitmap.setPixel(
                        x,
                        y,
                        if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE
                    )
                }
            }
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
