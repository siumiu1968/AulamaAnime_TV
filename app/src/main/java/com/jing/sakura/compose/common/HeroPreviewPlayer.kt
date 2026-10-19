package com.jing.sakura.compose.common

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.TextureView
import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.jing.sakura.R
import com.jing.sakura.SakuraApplication
import com.jing.sakura.home.HeroPreviewSpec
import com.jing.sakura.home.shouldRetryPreviewLoad
import com.jing.sakura.player.NavigateToPlayerArg
import com.jing.sakura.player.PreviewContinuationClock
import com.jing.sakura.player.PreviewPlaybackHandoff
import com.jing.sakura.player.PreviewHandoffLoadControl
import okhttp3.OkHttpClient
import org.koin.androidx.compose.get
import org.koin.core.qualifier.qualifier

@OptIn(UnstableApi::class)
@Composable
fun HeroPreviewPlayer(
    spec: HeroPreviewSpec,
    modifier: Modifier = Modifier,
    playbackEnabled: Boolean = true,
    volume: () -> Float = { 1f },
    onReady: () -> Unit = {},
    onError: (String) -> Unit = {},
    onEnded: () -> Unit = {},
    onContinuePlayback: ((NavigateToPlayerArg) -> Unit)? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val okHttpClient: OkHttpClient = get(
        qualifier = qualifier(SakuraApplication.KoinOkHttpClient.MEDIA)
    )
    val currentOnReady by rememberUpdatedState(onReady)
    val currentOnError by rememberUpdatedState(onError)
    val currentOnEnded by rememberUpdatedState(onEnded)
    val currentOnContinuePlayback by rememberUpdatedState(onContinuePlayback)
    val currentVolume by rememberUpdatedState(volume)
    var showPoster by remember { mutableStateOf(true) }
    var displayedKey by remember { mutableStateOf<String?>(null) }
    val controller = remember(context, okHttpClient) {
        HeroPreviewController(
            context = context.applicationContext,
            okHttpClient = okHttpClient,
            onReady = {
                showPoster = false
                currentOnReady()
            },
            onError = { message ->
                showPoster = true
                currentOnError(message)
            },
            onEnded = {
                showPoster = true
                currentOnEnded()
            },
            onContinuePlayback = { currentOnContinuePlayback?.invoke(it) },
            canContinuePlayback = { currentOnContinuePlayback != null }
        )
    }

    DisposableEffect(lifecycleOwner, controller) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> controller.start()
                Lifecycle.Event.ON_STOP -> controller.release()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            controller.start()
        }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            controller.release()
        }
    }

    LaunchedEffect(spec.key, playbackEnabled) {
        if (displayedKey != spec.key) {
            displayedKey = spec.key
            showPoster = true
        }
        controller.load(spec)
        controller.setPlaybackEnabled(playbackEnabled)
    }

    LaunchedEffect(onContinuePlayback != null) {
        controller.setContinuationEnabled(onContinuePlayback != null)
    }

    // Audio follows the visual reveal so previews swell in and duck out instead of popping.
    LaunchedEffect(controller) {
        snapshotFlow { currentVolume().coerceIn(0f, 1f) }.collect(controller::setVolume)
    }

    Box(modifier = modifier) {
        AndroidView(
            factory = controller::createPlayerView,
            update = {},
            modifier = Modifier.fillMaxSize()
        )
        if (showPoster && spec.posterUrl.isNotBlank()) {
            AsyncImage(
                model = spec.posterUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@UnstableApi
internal class HeroPreviewController(
    private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val onReady: () -> Unit,
    private val onError: (String) -> Unit,
    private val onEnded: () -> Unit,
    private val onContinuePlayback: (NavigateToPlayerArg) -> Unit = {},
    private val canContinuePlayback: () -> Boolean = { true }
) {
    private var playerView: PlayerView? = null
    private var player: ExoPlayer? = null
    private var trackSelector: DefaultTrackSelector? = null
    private var loadControl: PreviewHandoffLoadControl? = null
    private var pendingSpec: HeroPreviewSpec? = null
    private var loadedKey: String? = null
    private var started = false
    private var firstFrameDispatched = false
    private var playbackRetryCount = 0
    private var loadGeneration = 0L
    private var volume = 1f
    private var playbackEnabled = true
    private var continuationEnabled = false
    private var handedOff = false
    private val continuationClock = PreviewContinuationClock()
    private val handler = Handler(Looper.getMainLooper())
    private val continuationTicker = object : Runnable {
        override fun run() {
            updateContinuation()
            if (started && !handedOff && continuationEnabled && player?.isPlaying == true) {
                handler.postDelayed(this, CONTINUATION_TICK_MS)
            }
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onRenderedFirstFrame() {
            if (firstFrameDispatched) return
            firstFrameDispatched = true
            onReady()
            updateContinuation()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            handler.removeCallbacks(continuationTicker)
            updateContinuation()
            if (isPlaying && continuationEnabled && !handedOff) handler.post(continuationTicker)
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) {
                continuationClock.reset()
                handler.removeCallbacks(continuationTicker)
                onEnded()
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            continuationClock.reset()
            handler.removeCallbacks(continuationTicker)
            val failedKey = loadedKey
            val retrySpec = pendingSpec
            if (shouldRetryPreviewLoad(
                    failedKey = failedKey,
                    currentKey = retrySpec?.key,
                    retryCount = playbackRetryCount
                )
            ) {
                playbackRetryCount += 1
                loadedKey = null
                firstFrameDispatched = false
                val retryGeneration = loadGeneration
                playerView?.postDelayed({
                    if (started && loadGeneration == retryGeneration && pendingSpec?.key == failedKey) {
                        retrySpec?.let { loadIfNeeded(it, resetRetryCount = false) }
                    }
                }, PLAYBACK_RETRY_DELAY_MS)
                return
            }
            onError(error.message ?: error.errorCodeName)
        }
    }

    fun createPlayerView(viewContext: Context): PlayerView {
        playerView?.let { return it }
        val view = LayoutInflater.from(viewContext)
            .inflate(R.layout.view_hero_preview_player, null, false) as PlayerView
        view.apply {
            useController = false
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            keepScreenOn = true
            findViewById<View>(androidx.media3.ui.R.id.exo_shutter)?.visibility = View.INVISIBLE
        }
        check(view.videoSurfaceView is TextureView) {
            "Hero preview requires a TextureView surface"
        }
        playerView = view
        if (started) {
            ensurePlayer()
            pendingSpec?.let(::loadIfNeeded)
        }
        return view
    }

    fun start() {
        if (started) return
        started = true
        if (playerView != null) {
            ensurePlayer()
            pendingSpec?.let(::loadIfNeeded)
        }
    }

    fun setVolume(value: Float) {
        volume = value
        player?.volume = value
    }

    fun setPlaybackEnabled(enabled: Boolean) {
        if (!enabled) continuationClock.update(SystemClock.elapsedRealtime(), playing = false)
        playbackEnabled = enabled
        player?.playWhenReady = enabled
        if (enabled) updateContinuation()
    }

    fun setContinuationEnabled(enabled: Boolean) {
        continuationEnabled = enabled
        if (!enabled) {
            continuationClock.reset()
            handler.removeCallbacks(continuationTicker)
        } else if (player?.isPlaying == true && !handedOff) {
            handler.removeCallbacks(continuationTicker)
            handler.post(continuationTicker)
        }
    }

    fun load(spec: HeroPreviewSpec) {
        if (handedOff && pendingSpec?.key == spec.key) return
        pendingSpec = spec
        if (started && playerView != null) {
            ensurePlayer()
            loadIfNeeded(spec)
        }
    }

    fun release() {
        handler.removeCallbacks(continuationTicker)
        continuationClock.reset()
        started = false
        loadedKey = null
        firstFrameDispatched = false
        playbackRetryCount = 0
        loadGeneration += 1
        val currentPlayer = player
        player = null
        playerView?.player = null
        trackSelector = null
        loadControl = null
        if (currentPlayer == null) return
        currentPlayer.removeListener(playerListener)
        currentPlayer.stop()
        currentPlayer.clearMediaItems()
        currentPlayer.release()
    }

    private fun ensurePlayer(): ExoPlayer {
        player?.let { return it }
        val trackSelector = DefaultTrackSelector(context.applicationContext).apply {
            setParameters(
                buildUponParameters()
                    .setMaxVideoSize(MAX_VIDEO_WIDTH, MAX_VIDEO_HEIGHT)
                    .setMaxVideoBitrate(MAX_VIDEO_BITRATE)
                    // Some sources expose a single stream above the preview target.
                    // Keep the target for adaptive streams, but never drop video entirely.
                    .setExceedVideoConstraintsIfNecessary(true)
            )
        }
        val loadControl = PreviewHandoffLoadControl.create()
        this.loadControl = loadControl
        this.trackSelector = trackSelector
        return ExoPlayer.Builder(context.applicationContext)
            .setTrackSelector(trackSelector)
            .setLoadControl(loadControl)
            .build()
            .apply {
                volume = this@HeroPreviewController.volume
                repeatMode = Player.REPEAT_MODE_OFF
                addListener(playerListener)
                playerView?.player = this
            }
            .also { player = it }
    }

    private fun loadIfNeeded(spec: HeroPreviewSpec, resetRetryCount: Boolean = true) {
        if (loadedKey == spec.key) return
        loadedKey = spec.key
        handedOff = false
        continuationClock.reset()
        handler.removeCallbacks(continuationTicker)
        firstFrameDispatched = false
        if (resetRetryCount) {
            playbackRetryCount = 0
            loadGeneration += 1
        }
        val dataSourceFactory = OkHttpDataSource.Factory { request ->
            okHttpClient.newCall(request)
        }.apply {
            setDefaultRequestProperties(spec.headers)
        }
        val mediaItem = MediaItem.Builder()
            .setUri(spec.url)
            .apply {
                if (spec.url.contains("m3u8", ignoreCase = true)) {
                    setMimeType(MimeTypes.APPLICATION_M3U8)
                }
            }
            .build()
        ensurePlayer().apply {
            setMediaSource(DefaultMediaSourceFactory(dataSourceFactory).createMediaSource(mediaItem))
            seekTo(spec.startPositionMs.coerceAtLeast(0L))
            prepare()
            playWhenReady = playbackEnabled
        }
    }

    private fun updateContinuation() {
        val current = player ?: return
        val shouldContinue = continuationClock.update(SystemClock.elapsedRealtime(),
            started && continuationEnabled && playbackEnabled && firstFrameDispatched &&
                current.isPlaying && current.playerError == null)
        if (shouldContinue) continuePlayback()
    }

    /** Keeps the same buffered player; only its output surface changes in the playback activity. */
    internal fun continuePlayback() {
        val current = player ?: return
        val spec = pendingSpec ?: return
        val selector = trackSelector ?: return
        if (!started || handedOff || !continuationEnabled || !canContinuePlayback() || !playbackEnabled || !firstFrameDispatched ||
            !current.isPlaying || current.playerError != null || loadedKey != spec.key) return
        handedOff = true
        handler.removeCallbacks(continuationTicker)
        current.removeListener(playerListener)
        val previewView = playerView
        val surface = previewView?.videoSurfaceView as? TextureView
        val snapshot = runCatching {
            surface?.takeIf { it.isAvailable }?.let {
                val scale = minOf(1f, 1280f / it.width.coerceAtLeast(1), 720f / it.height.coerceAtLeast(1))
                it.getBitmap((it.width * scale).toInt().coerceAtLeast(1),
                    (it.height * scale).toInt().coerceAtLeast(1))
            }
        }.getOrNull()
        val continuationLoadControl = loadControl
        val arg = PreviewPlaybackHandoff.register(spec.navigateToPlayerArg, spec.url, spec.headers,
            current, selector, snapshot, promoteBuffer = { continuationLoadControl?.promote() }) { previewView?.player = null }
        player = null
        trackSelector = null
        loadControl = null
        try {
            onContinuePlayback(arg)
        } catch (error: RuntimeException) {
            PreviewPlaybackHandoff.expire(arg.previewHandoffId)
            onError(error.message ?: "播放器開啟失敗")
        }
    }

    private companion object {
        const val MAX_VIDEO_WIDTH = 1280
        const val MAX_VIDEO_HEIGHT = 720
        const val MAX_VIDEO_BITRATE = 2_500_000
        const val PLAYBACK_RETRY_DELAY_MS = 450L
        const val CONTINUATION_TICK_MS = 200L
    }
}
