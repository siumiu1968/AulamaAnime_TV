@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.jing.sakura.compose.screen

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
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
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
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
import com.jing.sakura.compose.common.AulamaCardShape
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
private const val WELCOME_TRANSITION_MS = 480

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
        eyebrow = "最新焦點",
        slogan = "下一集，喺大螢幕繼續。",
        message = "登入 Aulama ID，把收藏、觀看進度同個人化推薦帶返嚟。",
        loginButton = "使用 Aulama ID 登入",
        guestButton = "免登入使用",
        guestNote = "遊客資料只會保留喺呢部電視；登入後可跨裝置同步。"
    )
    TvLanguage.Simplified -> WelcomeCopy(
        eyebrow = "最新焦点",
        slogan = "下一集，在大屏幕继续。",
        message = "登录 Aulama ID，同步收藏、观看进度和个性化推荐。",
        loginButton = "使用 Aulama ID 登录",
        guestButton = "免登录使用",
        guestNote = "游客数据只保存在这台电视；登录后可跨设备同步。"
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
    val artworkAccent = rememberArtworkAccent(selectedAnime?.imageUrl.orEmpty())
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
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = posterRequest,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                alignment = Alignment.TopEnd,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = 0.96f
                        scaleX = 1.42f
                        scaleY = 1.42f
                        transformOrigin = TransformOrigin(1f, 0f)
                        compositingStrategy = CompositingStrategy.Offscreen
                    }
                    .drawWithContent {
                        drawContent()
                        drawRect(
                            brush = Brush.horizontalGradient(
                                colorStops = arrayOf(
                                    0f to Color.Transparent,
                                    0.55f to Color.Transparent,
                                    0.60f to Color.Black.copy(alpha = 0.24f),
                                    0.66f to Color.Black.copy(alpha = 0.82f),
                                    0.72f to Color.Black,
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
            .background(
                Brush.horizontalGradient(
                    colorStops = arrayOf(
                        0f to AulamaTvColors.Background,
                        0.18f to AulamaTvColors.Background,
                        0.34f to AulamaTvColors.Background.copy(alpha = 0.98f),
                        0.47f to AulamaTvColors.Background.copy(alpha = 0.82f),
                        0.60f to AulamaTvColors.Background.copy(alpha = 0.46f),
                        0.73f to AulamaTvColors.Background.copy(alpha = 0.10f),
                        0.86f to Color.Transparent,
                        1f to Color.Transparent
                    )
                )
            )
            .background(
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0f to AulamaTvColors.Background.copy(alpha = 0.12f),
                        0.10f to Color.Transparent,
                        0.86f to Color.Transparent,
                        0.91f to AulamaTvColors.Background.copy(alpha = 0.12f),
                        0.96f to AulamaTvColors.Background.copy(alpha = 0.46f),
                        1f to AulamaTvColors.Background
                    )
                )
            )
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.horizontalGradient(
                    colorStops = arrayOf(
                        0f to Color.Transparent,
                        0.28f to Color.Transparent,
                        0.42f to accent.copy(alpha = 0.07f),
                        0.52f to accent.copy(alpha = 0.16f),
                        0.62f to accent.copy(alpha = 0.10f),
                        0.74f to accent.copy(alpha = 0.03f),
                        0.84f to Color.Transparent,
                        1f to Color.Transparent
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
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .width(520.dp)
                .clip(AulamaCardShape)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            AulamaTvColors.SurfaceRaised.copy(alpha = 0.90f),
                            AulamaTvColors.Surface.copy(alpha = 0.76f)
                        )
                    )
                )
                .border(1.dp, Color.White.copy(alpha = 0.16f), AulamaCardShape)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                if (account != null) {
                    AulamaAccountAvatar(
                        account = account,
                        modifier = Modifier
                            .size(64.dp)
                            .border(2.dp, AulamaTvColors.Cyan.copy(alpha = 0.55f), CircleShape)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(AulamaTvColors.Surface)
                            .border(2.dp, AulamaTvColors.Cyan.copy(alpha = 0.55f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        androidx.tv.material3.Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = AulamaTvColors.TextPrimary,
                            modifier = Modifier.size(34.dp)
                        )
                    }
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = account?.name ?: localizedText("遊客模式"),
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontSize = 27.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = AulamaTvColors.TextPrimary,
                        maxLines = 1
                    )
                    if (!account?.email.isNullOrBlank()) {
                        Text(
                            text = account?.email.orEmpty(),
                            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp),
                            color = AulamaTvColors.TextSecondary,
                            maxLines = 1
                        )
                    }
                    Text(
                        text = account?.role ?: localizedText("紀錄與收藏只保留喺呢部電視"),
                        style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp),
                        color = AulamaTvColors.Cyan,
                        maxLines = 1
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = localizedText("介面語言"),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = AulamaTvColors.TextPrimary
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AulamaActionButton(
                        label = "繁體中文",
                        icon = Icons.Default.Check.takeIf { language == TvLanguage.Traditional },
                        accent = AulamaTvColors.Cyan,
                        onClick = { onLanguageChange(TvLanguage.Traditional) },
                        modifier = Modifier.weight(1f).height(48.dp)
                    )
                    AulamaActionButton(
                        label = "简体中文",
                        icon = Icons.Default.Check.takeIf { language == TvLanguage.Simplified },
                        accent = AulamaTvColors.Blue,
                        onClick = { onLanguageChange(TvLanguage.Simplified) },
                        modifier = Modifier.weight(1f).height(48.dp)
                    )
                }
            }
            AulamaActionButton(
                label = "自動播放預覽 · ${if (previewEnabled) "開啟" else "關閉"}",
                icon = Icons.Default.Check.takeIf { previewEnabled },
                accent = AulamaTvColors.Cyan,
                onClick = { onPreviewEnabledChange(!previewEnabled) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(AulamaCardShape)
                    .background(AulamaTvColors.Surface.copy(alpha = 0.66f))
                    .border(1.dp, AulamaTvColors.Outline, AulamaCardShape)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = localizedText("應用程式更新"),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = AulamaTvColors.TextPrimary
                    )
                    Text(
                        text = "v$currentVersion",
                        style = MaterialTheme.typography.labelLarge,
                        color = AulamaTvColors.TextSecondary
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AulamaActionButton(
                        label = "正式版",
                        icon = Icons.Default.Check.takeIf { updateChannel == TvUpdateChannel.Stable },
                        accent = AulamaTvColors.Green,
                        onClick = { onUpdateChannelChange(TvUpdateChannel.Stable) },
                        modifier = Modifier.weight(1f).height(46.dp)
                    )
                    AulamaActionButton(
                        label = "搶先版",
                        icon = Icons.Default.Check.takeIf { updateChannel == TvUpdateChannel.Preview },
                        accent = AulamaTvColors.Amber,
                        onClick = { onUpdateChannelChange(TvUpdateChannel.Preview) },
                        modifier = Modifier.weight(1f).height(46.dp)
                    )
                }
                Text(
                    text = localizedText(
                        if (updateChannel == TvUpdateChannel.Preview) {
                            "較早收到測試功能，適合協助試用新版本"
                        } else {
                            "只接收完成測試嘅穩定版本"
                        }
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = AulamaTvColors.TextSecondary
                )
                AulamaActionButton(
                    label = if (isCheckingForUpdate) {
                        "正在檢查更新"
                    } else {
                        "檢查更新"
                    },
                    icon = Icons.Default.SystemUpdateAlt,
                    enabled = !isCheckingForUpdate,
                    accent = AulamaTvColors.Cyan,
                    onClick = onCheckForUpdate,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)
            ) {
                AulamaActionButton(
                    label = "返回",
                    onClick = onDismiss,
                    modifier = Modifier.height(48.dp).focusRequester(dismissFocus)
                )
                AulamaActionButton(
                    label = if (account == null) "登入並跨裝置同步" else "登出",
                    icon = if (account == null) Icons.AutoMirrored.Filled.Login else Icons.Default.Logout,
                    accent = if (account == null) AulamaTvColors.Cyan else AulamaTvColors.Pink,
                    onClick = if (account == null) onLogin else onLogout,
                    modifier = Modifier.height(48.dp)
                )
            }
        }
        LaunchedEffect(Unit) { dismissFocus.requestFocus() }
    }
}
