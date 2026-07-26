@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.jing.sakura.compose.screen

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
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
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.jing.sakura.auth.AulamaAccount
import com.jing.sakura.auth.AuthUiState
import com.jing.sakura.auth.DeviceCode
import com.jing.sakura.compose.common.AulamaActionButton
import com.jing.sakura.compose.common.AulamaAccountAvatar
import com.jing.sakura.compose.common.AulamaAnimeBrandMark
import com.jing.sakura.compose.common.AulamaCardShape
import com.jing.sakura.compose.common.AulamaLoadingPulse
import com.jing.sakura.compose.common.AulamaTvColors
import com.jing.sakura.compose.common.TvLanguage
import com.jing.sakura.compose.common.localizedText
import com.jing.sakura.compose.common.aulamaTvBackground
import com.jing.sakura.compose.common.rememberArtworkAccent
import com.jing.sakura.compose.common.rememberPosterImageRequest
import com.jing.sakura.data.AnimeData
import kotlinx.coroutines.delay

private const val WELCOME_ROTATION_INTERVAL_MS = 5_000L
private const val WELCOME_TRANSITION_MS = 700

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun DeviceLoginScreen(
    state: AuthUiState,
    onLogin: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    welcomeAnime: List<AnimeData>
) {
    BackHandler(
        enabled = state !is AuthUiState.Checking && state !is AuthUiState.Welcome,
        onBack = onCancel
    )
    when (state) {
        AuthUiState.Checking -> LoginCheckingScreen(showProgress = false)
        AuthUiState.Welcome -> LoginWelcomeScreen(
            onLogin = onLogin,
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
    featuredAnime: List<AnimeData>
) {
    val loginFocusRequester = remember { FocusRequester() }
    var featuredIndex by remember { mutableStateOf(0) }
    LaunchedEffect(featuredAnime) {
        featuredIndex = 0
        while (featuredAnime.size > 1) {
            delay(WELCOME_ROTATION_INTERVAL_MS)
            featuredIndex = (featuredIndex + 1) % featuredAnime.size
        }
    }
    val selectedAnime = featuredAnime.getOrNull(featuredIndex)
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .aulamaTvBackground()
    ) {
        val compactLayout = maxWidth < 1_300.dp || maxHeight < 760.dp
        WelcomeAnimeBackdrop(
            anime = featuredAnime,
            selectedIndex = featuredIndex
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
            Crossfade(
                targetState = selectedAnime,
                animationSpec = tween(durationMillis = WELCOME_TRANSITION_MS),
                label = "welcome-anime-title"
            ) { anime ->
                if (anime != null) {
                    val artworkAccent = rememberArtworkAccent(anime.imageUrl)
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(
                            text = "最新焦點",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontSize = if (compactLayout) 17.sp else 19.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = AulamaTvColors.TextSecondary
                        )
                        Text(
                            text = localizedText(anime.title),
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontSize = if (compactLayout) 31.sp else 37.sp,
                                lineHeight = if (compactLayout) 37.sp else 43.sp,
                                fontWeight = FontWeight.ExtraBold
                            ),
                            color = artworkAccent,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            Text(
                text = "下一集，喺大螢幕繼續。",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontSize = if (compactLayout) 38.sp else 46.sp,
                    lineHeight = if (compactLayout) 44.sp else 52.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = AulamaTvColors.TextPrimary,
                textAlign = TextAlign.Start
            )
            Text(
                text = "登入 Aulama ID，把收藏、觀看進度同個人化推薦帶返嚟。",
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = if (compactLayout) 17.sp else 20.sp,
                    lineHeight = if (compactLayout) 24.sp else 28.sp
                ),
                color = AulamaTvColors.TextSecondary,
                textAlign = TextAlign.Start
            )
            Spacer(Modifier.height(if (compactLayout) 4.dp else 8.dp))
            AulamaActionButton(
                label = "使用 Aulama ID 登入",
                icon = Icons.AutoMirrored.Filled.Login,
                onClick = onLogin,
                modifier = Modifier
                    .width(if (compactLayout) 380.dp else 420.dp)
                    .height(70.dp)
                    .focusRequester(loginFocusRequester),
                centerLabel = true,
                labelFontSize = 20.sp,
                labelLineHeight = 25.sp,
                iconSize = 25.dp,
                contentHeight = 64.dp
            )
        }
        LaunchedEffect(Unit) { loginFocusRequester.requestFocus() }
    }
}

@Composable
private fun WelcomeAnimeBackdrop(anime: List<AnimeData>, selectedIndex: Int) {
    if (anime.isEmpty()) return
    anime.forEachIndexed { index, item ->
        val artworkAlpha by animateFloatAsState(
            targetValue = if (index == selectedIndex) 0.9f else 0f,
            animationSpec = tween(durationMillis = WELCOME_TRANSITION_MS),
            label = "welcome-anime-backdrop-$index"
        )
        AsyncImage(
            model = rememberPosterImageRequest(
                imageUrl = item.imageUrl,
                widthPx = 960,
                heightPx = 1_360
            ),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            alignment = Alignment.TopEnd,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = artworkAlpha
                    scaleX = 1.42f
                    scaleY = 1.42f
                    transformOrigin = TransformOrigin(1f, 0f)
                }
        )
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.horizontalGradient(
                    colorStops = arrayOf(
                        0f to AulamaTvColors.Background,
                        0.42f to AulamaTvColors.Background,
                        0.52f to AulamaTvColors.Background.copy(alpha = 0.96f),
                        0.62f to AulamaTvColors.Background.copy(alpha = 0.72f),
                        0.74f to AulamaTvColors.Background.copy(alpha = 0.30f),
                        0.86f to AulamaTvColors.Background.copy(alpha = 0.06f),
                        1f to Color.Transparent
                    )
                )
            )
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0f to AulamaTvColors.Background.copy(alpha = 0.24f),
                        0.56f to Color.Transparent,
                        1f to AulamaTvColors.Background.copy(alpha = 0.94f)
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
                    text = "使用 Aulama ID 登入",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = if (compactLayout) 17.sp else 19.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = AulamaTvColors.Cyan
                )
                Text(
                    text = "將你嘅片庫\n帶到大螢幕",
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontSize = if (compactLayout) 38.sp else 44.sp,
                        lineHeight = if (compactLayout) 44.sp else 50.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = AulamaTvColors.TextPrimary
                )
                Text(
                    text = "收藏、觀看進度同個人化推薦會自動同步。",
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = if (compactLayout) 18.sp else 20.sp,
                        lineHeight = if (compactLayout) 25.sp else 28.sp
                    ),
                    color = AulamaTvColors.TextSecondary
                )
                Spacer(Modifier.size(if (compactLayout) 2.dp else 6.dp))
                LoginStep(number = "1", text = "用手機掃描 QR Code", compact = compactLayout)
                LoginStep(number = "2", text = "確認你嘅 Aulama ID", compact = compactLayout)
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
            text = text,
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
            AuthUiState.RequestingCode -> null
            is AuthUiState.Waiting -> if (state.pending) "等待你確認登入" else "裝置碼已準備好"
            is AuthUiState.RateLimited -> "每小時最多嘗試 3 次，請於 ${state.retryAfterSeconds} 秒後重試"
            is AuthUiState.Expired -> "裝置碼已過期"
            is AuthUiState.Error -> state.message
            is AuthUiState.Authenticated -> "登入成功"
        }
        status?.let {
            Text(
                text = it,
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
            contentDescription = "掃描 QR Code 登入",
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
                text = "掃描或輸入裝置碼",
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
                    text = "%d:%02d 後失效".format(remainingSeconds / 60, remainingSeconds % 60),
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
    account: AulamaAccount,
    language: TvLanguage,
    isCheckingForUpdate: Boolean,
    currentVersion: String,
    onLanguageChange: (TvLanguage) -> Unit,
    onCheckForUpdate: () -> Unit,
    onDismiss: () -> Unit,
    onLogout: () -> Unit
) {
    val dismissFocus = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .width(520.dp)
                .clip(AulamaCardShape)
                .background(AulamaTvColors.SurfaceRaised)
                .border(1.dp, AulamaTvColors.Outline, AulamaCardShape)
                .padding(30.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                AulamaAccountAvatar(
                    account = account,
                    modifier = Modifier
                        .size(72.dp)
                        .border(2.dp, AulamaTvColors.Cyan.copy(alpha = 0.55f), CircleShape)
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = account.name,
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontSize = 27.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = AulamaTvColors.TextPrimary,
                        maxLines = 1
                    )
                    if (account.email.isNotBlank()) {
                        Text(
                            text = account.email,
                            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp),
                            color = AulamaTvColors.TextSecondary,
                            maxLines = 1
                        )
                    }
                    Text(
                        text = account.role,
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
                        modifier = Modifier.weight(1f).height(52.dp)
                    )
                    AulamaActionButton(
                        label = "简体中文",
                        icon = Icons.Default.Check.takeIf { language == TvLanguage.Simplified },
                        accent = AulamaTvColors.Blue,
                        onClick = { onLanguageChange(TvLanguage.Simplified) },
                        modifier = Modifier.weight(1f).height(52.dp)
                    )
                }
            }
            AulamaActionButton(
                label = if (isCheckingForUpdate) {
                    "正在檢查更新"
                } else {
                    "檢查更新 · v$currentVersion"
                },
                icon = Icons.Default.SystemUpdateAlt,
                enabled = !isCheckingForUpdate,
                accent = AulamaTvColors.Green,
                onClick = onCheckForUpdate,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)
            ) {
                AulamaActionButton(
                    label = "返回",
                    onClick = onDismiss,
                    modifier = Modifier.height(52.dp).focusRequester(dismissFocus)
                )
                AulamaActionButton(
                    label = "登出",
                    icon = Icons.Default.Logout,
                    accent = AulamaTvColors.Pink,
                    onClick = onLogout,
                    modifier = Modifier.height(52.dp)
                )
            }
        }
        LaunchedEffect(Unit) { dismissFocus.requestFocus() }
    }
}
