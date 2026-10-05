package com.jing.sakura.player

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.view.animation.PathInterpolator
import android.widget.TextView
import androidx.annotation.OptIn
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.graphics.drawable.toDrawable
import androidx.core.content.res.ResourcesCompat
import androidx.leanback.R as LeanbackR
import androidx.leanback.app.VideoSupportFragment
import androidx.leanback.app.VideoSupportFragmentGlueHost
import androidx.leanback.widget.PlaybackControlsRow.PlayPauseAction
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.effect.LanczosResample
import androidx.media3.effect.Presentation
import androidx.media3.ui.leanback.LeanbackPlayerAdapter
import com.jing.sakura.R
import com.jing.sakura.SakuraApplication
import com.jing.sakura.auth.favoriteEpisodeNumber
import com.jing.sakura.compose.common.TvLanguage
import com.jing.sakura.compose.common.TvLanguagePreferences
import com.jing.sakura.compose.theme.setAulamaTvContent
import com.jing.sakura.detail.DetailActivity
import com.jing.sakura.home.MainActivity
import com.jing.sakura.data.Resource
import com.jing.sakura.extend.secondsToMinuteAndSecondText
import com.jing.sakura.extend.showLongToast
import com.jing.sakura.extend.showShortToast
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import org.koin.android.ext.android.get
import org.koin.androidx.viewmodel.ext.android.activityViewModel
import org.koin.core.parameter.parametersOf
import org.koin.core.qualifier.qualifier

@OptIn(UnstableApi::class)
class AnimePlayerFragment : VideoSupportFragment() {

    private val viewModel: VideoPlayerViewModel by activityViewModel {
        @Suppress("DEPRECATION")
        val arg = requireActivity().intent.getSerializableExtra("video") as NavigateToPlayerArg
        parametersOf(arg)
    }

    private val okHttpClient: OkHttpClient =
        get(qualifier = qualifier(SakuraApplication.KoinOkHttpClient.MEDIA))

    private var player: ExoPlayer? = null
    private var trackSelector: DefaultTrackSelector? = null
    private var glue: ProgressTransportControlGlue<LeanbackPlayerAdapter>? = null
    private var current4kMode = Tv4kMode.OFF
    private var skipSegmentActions: View? = null
    private var skipActionsExiting = false
    private var skipSegmentButton: CountdownActionButton? = null
    private var continueOutroButton: TextView? = null
    private var activePlaybackSkip: ActivePlaybackSkip? = null
    private val skipUiState = PlaybackSkipUiStateMachine()
    private val skipPromptKeyController = PlaybackSkipPromptKeyController()
    private val skipExitGrace = PlaybackSkipExitGracePolicy()
    private var primaryControlsDock: ViewGroup? = null
    private var playbackProgress: View? = null
    private var lastPrimaryControl: View? = null
    private var lastTransportFocus: View? = null
    private var skipFocusWasAutomatic = false
    private var focusBindAttempts = 0
    private var playerHeader: View? = null
    private var playerHeaderScrim: View? = null
    private var playerHeaderTitle: TextView? = null
    private var playerHeaderEpisode: TextView? = null
    private var speedBoostIndicator: SpeedBoostOverlay? = null
    private val centerKeyController = PlaybackCenterKeyController()
    private var speedBeforeBoost = 1f
    private var wasPlayingBeforeBoost = true
    private var last4kDowngradeAtMs = 0L
    private var handledEndedEpisodeIndex = -1
    private var nearEndAutoAdvanceSuppressed = false
    private var pendingSourceFallback: PlaybackSourceFallback? = null
    private val failedPlaylistIndexes = mutableSetOf<Int>()
    private var fallbackEpisodeLabel = ""
    private var seasonFinaleView: ComposeView? = null
    private var seasonFinaleVisible = false

    private val hideTransientSkipRunnable = Runnable {
        if (skipUiState.onTransientActionTimeout()) {
            hideSkipUi(allowPlayerControlsRestore = false)
        }
    }

    private val hideHeaderRunnable = Runnable {
        listOfNotNull(playerHeader, playerHeaderScrim).forEach { chrome ->
            chrome.animate()
                .alpha(0f)
                .setDuration(HEADER_FADE_OUT_MS)
                .setInterpolator(DecelerateInterpolator())
                .withEndAction { chrome.visibility = View.GONE }
                .start()
        }
    }

    private val hideControlsRunnable = Runnable {
        val localGlue = glue ?: return@Runnable
        if (
            PlaybackControlsAutoHidePolicy.shouldHide(
                controlsVisible = localGlue.host.isControlsOverlayVisible,
                isPlaying = player?.isPlaying == true
            )
        ) {
            localGlue.host.hideControlsOverlay(true)
        }
    }

    private val hideSkipUiAfterGraceRunnable = Runnable {
        if (skipExitGrace.shouldCommitExit(SystemClock.uptimeMillis())) {
            skipExitGrace.clear()
            skipUiState.update(null)
            skipPromptKeyController.reset()
            activePlaybackSkip = null
            hideSkipUi()
        }
    }

    private val centerLongPressRunnable = object : Runnable {
        override fun run() {
            val now = SystemClock.uptimeMillis()
            applyCenterKeyAction(centerKeyController.onReleaseTimeout(now))
            applyCenterKeyAction(centerKeyController.onLongPressTimeout(now))
            speedBoostIndicator?.updateCharge(centerKeyController.charge(now), centerKeyController.isLocked)
            if (centerKeyController.isActive && !centerKeyController.isLocked) view?.postDelayed(this, 32L)
        }
    }

    private val analyticsListener = object : AnalyticsListener {
        override fun onDroppedVideoFrames(
            eventTime: AnalyticsListener.EventTime,
            droppedFrames: Int,
            elapsedMs: Long
        ) {
            val now = SystemClock.elapsedRealtime()
            if (
                current4kMode.isEnabled &&
                droppedFrames >= current4kMode.droppedFrameLimit &&
                now - last4kDowngradeAtMs >= FOUR_K_DOWNGRADE_COOLDOWN_MS
            ) {
                last4kDowngradeAtMs = now
                view?.post {
                    downgrade4kMode()
                }
            }
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            Log.d(TAG, "playbackState=${playbackStateName(playbackState)}")
            when (playbackState) {
                Player.STATE_BUFFERING -> progressBarManager.show()
                Player.STATE_READY -> {
                    progressBarManager.hide()
                    renderSkipSegment(player?.currentPosition?.coerceAtLeast(0L) ?: 0L)
                }
                Player.STATE_ENDED -> {
                    progressBarManager.hide()
                    advanceToNextEpisode()
                }
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            snapshotPlaybackPosition()
            if (isPlaying) {
                viewModel.startSaveHistory()
                skipSegmentButton?.resumeCountdown()
                scheduleControlsAutoHide()
            } else {
                viewModel.stopSaveHistory()
                skipSegmentButton?.pauseCountdown()
                cancelControlsAutoHide()
            }
        }

        override fun onRenderedFirstFrame() {
            progressBarManager.hide()
        }

        override fun onPlayerError(error: PlaybackException) {
            Log.e(TAG, "playerError=${error.errorCodeName}: ${error.message}", error)
            if (current4kMode.isEnabled && error.isVideoEffectFailure()) {
                val resumePosition = player?.currentPosition?.coerceAtLeast(0L) ?: 0L
                apply4kMode(Tv4kMode.OFF, announce = false)
                requireContext().showShortToast(getString(R.string.player_fast_4k_overload))
                player?.apply {
                    prepare()
                    if (resumePosition > 0L) seekTo(resumePosition)
                    play()
                }
                return
            }
            progressBarManager.hide()
            viewModel.stopSaveHistory()
            showPlaybackFailure(error.errorCodeName)
        }

        override fun onPlaybackParametersChanged(playbackParameters: androidx.media3.common.PlaybackParameters) {
            if (com.jing.sakura.BuildConfig.DEBUG) Log.d(TAG, "speed=${playbackParameters.speed}")
        }

        override fun onVideoSizeChanged(videoSize: VideoSize) {
            Log.d(TAG, "videoSize=${videoSize.width}x${videoSize.height}")
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
        ) {
            if (reason == Player.DISCONTINUITY_REASON_SEEK) {
                if (com.jing.sakura.BuildConfig.DEBUG) Log.d(TAG,
                    "seek=${newPosition.positionMs - oldPosition.positionMs} duration=${player?.duration}")
                nearEndAutoAdvanceSuppressed = true
                skipUiState.onSeek()
                skipPromptKeyController.reset()
                activePlaybackSkip = null
                hideSkipUi()
                renderSkipSegment(newPosition.positionMs.coerceAtLeast(0L))
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requireActivity().window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.background = Color.BLACK.toDrawable()
        progressBarManager.setRootView(view as ViewGroup)
        progressBarManager.enableProgressBar()
        progressBarManager.initialDelay = 0L
        skipSegmentActions = requireActivity().findViewById(R.id.player_skip_actions)
        skipSegmentButton = requireActivity().findViewById<CountdownActionButton>(R.id.player_skip_segment).apply {
            setOnClickListener { activateSkipSegment() }
            setOnFocusChangeListener(::animateSkipActionFocus)
        }
        continueOutroButton = requireActivity().findViewById<TextView>(R.id.player_continue_outro).apply {
            setOnClickListener {
                skipUiState.onContinuePlayback()
                skipSegmentButton?.cancelCountdown()
                hideSkipUi()
                glue?.host?.showControlsOverlay(true)
                scheduleControlsAutoHide()
            }
            setOnFocusChangeListener(::animateSkipActionFocus)
        }
        val roundedFont = ResourcesCompat.getFont(
            requireContext(),
            when (TvLanguagePreferences.get(requireContext()).language.value) {
                TvLanguage.Traditional -> R.font.resource_han_rounded_hk_heavy
                TvLanguage.Simplified -> R.font.resource_han_rounded_cn_heavy
            }
        )
        skipSegmentButton?.typeface = roundedFont
        continueOutroButton?.typeface = roundedFont
        setSkipActionsFocusable(false)
        playerHeader = requireActivity().findViewById(R.id.player_header)
        playerHeaderScrim = requireActivity().findViewById(R.id.player_header_scrim)
        playerHeaderTitle = requireActivity().findViewById(R.id.player_header_title)
        playerHeaderEpisode = requireActivity().findViewById(R.id.player_header_episode)
        playerHeaderTitle?.typeface = roundedFont
        speedBoostIndicator = requireActivity().findViewById(R.id.player_speed_boost)
        seasonFinaleView = requireActivity().findViewById<ComposeView>(R.id.player_season_finale).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        }
        view.post {
            bindPlaybackFocusTargets()
            PlayerProgressStyler.apply(view)
            showPlayerHeader()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.playerSubTitle.collectLatest {
                        playerHeaderEpisode?.text = it
                    }
                }
                launch {
                    viewModel.videoUrl.collectLatest(::renderVideoState)
                }
                launch {
                    viewModel.playbackSegments.collectLatest {
                        renderSkipSegment(player?.currentPosition ?: 0L)
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (player == null) player = buildPlayer()
        view?.post {
            bindPlaybackFocusTargets()
            view?.let(PlayerProgressStyler::apply)
        }
    }

    override fun onStop() {
        // The season is over and the sheet is a terminal state: leaving the app closes the player.
        if (seasonFinaleVisible) activity?.finish()
        clearSkipState()
        cancelControlsAutoHide()
        applyCenterKeyAction(centerKeyController.cancel())
        playerHeader?.removeCallbacks(hideHeaderRunnable)
        snapshotPlaybackPosition()
        viewModel.stopSaveHistory()
        destroyPlayer()
        super.onStop()
    }

    private fun renderVideoState(resource: Resource<EpisodeUrlAndHistory>) {
        when (resource) {
            is Resource.Loading -> {
                progressBarManager.show()
                glue?.showSourceFallbackAction(false)
                playerHeaderEpisode?.text = getString(R.string.player_loading_episode)
                showPlayerHeader()
            }
            is Resource.Error -> {
                progressBarManager.hide()
                showPlaybackFailure(resource.message)
            }
            is Resource.Success -> loadEpisode(resource.data)
        }
    }

    private fun loadEpisode(payload: EpisodeUrlAndHistory) {
        val localPlayer = player ?: return
        progressBarManager.show()
        handledEndedEpisodeIndex = -1
        nearEndAutoAdvanceSuppressed = false
        clearSkipState()
        glue?.showSourceFallbackAction(false)
        pendingSourceFallback = null
        if (fallbackEpisodeLabel != payload.episode.episode) {
            fallbackEpisodeLabel = payload.episode.episode
            failedPlaylistIndexes.clear()
        }

        val dataSourceFactory = OkHttpDataSource.Factory { request ->
            okHttpClient.newCall(request)
        }.apply {
            setDefaultRequestProperties(payload.headers)
        }
        val mediaItem = MediaItem.Builder()
            .setUri(payload.videoUrl)
            .apply {
                if (payload.videoUrl.contains("m3u8", ignoreCase = true)) {
                    setMimeType(MimeTypes.APPLICATION_M3U8)
                }
            }
            .build()
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)
        val mediaSource = mediaSourceFactory.createMediaSource(mediaItem)
        val startPositionMs = PlaybackCompletionPolicy.resumePosition(
            payload.lastPlayPosition,
            payload.videoDuration
        )
        if (payload.lastPlayPosition > 0L) {
            if (startPositionMs == 0L) {
                requireContext().showShortToast(getString(R.string.player_finished_restart))
            } else {
                requireContext().showShortToast(
                    getString(
                        R.string.player_resume_template,
                        (startPositionMs / 1000L).secondsToMinuteAndSecondText()
                    )
                )
            }
        }

        viewModel.changePlayingEpisode(payload.episode)
        playerHeaderTitle?.text = viewModel.anime.animeName
        playerHeaderEpisode?.text = payload.episode.episode
        showPlayerHeader()
        glue?.title = ""
        glue?.subtitle = ""
        localPlayer.setMediaSource(mediaSource)
        if (startPositionMs > 0L) localPlayer.seekTo(startPositionMs)
        localPlayer.prepare()
        localPlayer.playWhenReady = true
    }

    private fun buildPlayer(): ExoPlayer {
        val initialConstraint = current4kMode.trackConstraint
        val selector = DefaultTrackSelector(requireContext()).apply {
            setParameters(
                buildUponParameters()
                    .setMaxVideoSize(initialConstraint.maxWidth, initialConstraint.maxHeight)
                    .setMaxVideoBitrate(initialConstraint.maxBitrate)
                    .setExceedVideoConstraintsIfNecessary(false)
            )
        }
        trackSelector = selector
        return ExoPlayer.Builder(requireContext())
            .setTrackSelector(selector)
            .setSeekBackIncrementMs(SEEK_INCREMENT_MS)
            .setSeekForwardIncrementMs(SEEK_INCREMENT_MS)
            .build()
            .apply {
                setSeekParameters(SeekParameters.CLOSEST_SYNC)
                addListener(playerListener)
                addAnalyticsListener(analyticsListener)
                prepareGlue(this)
                playWhenReady = true
            }
    }

    private fun prepareGlue(localPlayer: ExoPlayer) {
        ProgressTransportControlGlue(
            context = requireContext(),
            activity = requireActivity(),
            impl = LeanbackPlayerAdapter(
                requireContext(),
                localPlayer,
                PLAYER_UPDATE_INTERVAL_MILLIS
            ),
            onPlayPauseAction = { action ->
                if (
                    action.index == PlayPauseAction.INDEX_PLAY &&
                    viewModel.videoUrl.value is Resource.Error
                ) {
                    viewModel.retryLoadEpisode()
                    true
                } else {
                    false
                }
            },
            updateProgress = {
                viewModel.onPlayPositionChange(
                    localPlayer.currentPosition.coerceAtLeast(0L),
                    localPlayer.contentDuration.coerceAtLeast(0L)
                )
                renderSkipSegment(localPlayer.currentPosition.coerceAtLeast(0L))
            },
            chooseEpisode = ::openEpisodeChooser,
            switchSourceFallback = ::confirmSourceFallback,
            playPreviousEpisode = viewModel::playPreviousEpisodeIfExists,
            playNextEpisode = ::advanceToNextEpisode,
            open4kModePicker = ::open4kModePicker,
            openSpeedPicker = ::openSpeedPicker
        ).apply {
            title = ""
            subtitle = ""
            isSeekEnabled = true
            isControlsOverlayAutoHideEnabled = true
            host = VideoSupportFragmentGlueHost(this@AnimePlayerFragment)
            update4kAction(current4kMode)
            glue = this
        }
    }

    private fun snapshotPlaybackPosition() {
        player?.let {
            viewModel.onPlayPositionChange(
                it.currentPosition.coerceAtLeast(0L),
                it.contentDuration.coerceAtLeast(0L)
            )
        }
    }

    private fun destroyPlayer() {
        applyCenterKeyAction(centerKeyController.cancel())
        player?.let {
            it.removeListener(playerListener)
            it.removeAnalyticsListener(analyticsListener)
            it.pause()
            it.release()
        }
        player = null
        trackSelector = null
        glue = null
        current4kMode = Tv4kMode.OFF
    }

    private fun openSpeedPicker() {
        applyCenterKeyAction(centerKeyController.cancel())
        val speeds = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)
        ChooseEpisodeDialog(
            title = "播放速度",
            dataList = speeds,
            defaultSelectIndex = speeds.indexOf(player?.playbackParameters?.speed ?: 1f),
            viewWidthDp = QUALITY_PANEL_WIDTH_DP,
            getText = { _, speed -> "${speed.toString().removeSuffix(".0")}×" }
        ) { _, speed ->
            player?.setPlaybackSpeed(speed)
            glue?.updateSpeedAction(speed)
        }.showNow(parentFragmentManager, "playback-speed")
    }

    private fun open4kModePicker() {
        val modes = Tv4kMode.entries
        ChooseEpisodeDialog(
            title = getString(R.string.player_4k_picker_title),
            dataList = modes,
            defaultSelectIndex = current4kMode.ordinal,
            viewWidthDp = QUALITY_PANEL_WIDTH_DP,
            getText = { _, item -> getString(item.labelRes) }
        ) { _, mode ->
            apply4kMode(mode)
        }.showNow(parentFragmentManager, TAG_4K_CHOOSER)
    }

    private fun apply4kMode(mode: Tv4kMode, announce: Boolean = true): Tv4kMode {
        val localPlayer = player ?: return current4kMode
        val supports4kOutput = supports4kOutput()
        val effectiveMode = Tv4kRuntimePolicy.effectiveMode(
            requested = mode,
            supports4kOutput = supports4kOutput,
            isLowRamDevice = isLowRamDevice()
        )
        if (mode.isEnabled && !supports4kOutput) {
            requireContext().showShortToast(getString(R.string.player_fast_4k_requires_4k_output))
        }
        return runCatching {
            val previousMode = current4kMode
            val resumePosition = localPlayer.currentPosition.coerceAtLeast(0L)
            val shouldResumePlayback = localPlayer.playWhenReady
            val plan = effectiveMode.effectPlan
            val effects: List<Effect> = when (plan.strategy) {
                Tv4kEffectStrategy.NONE -> emptyList()
                Tv4kEffectStrategy.MATRIX -> listOf(
                    Presentation.createForWidthAndHeight(
                        plan.targetWidth,
                        plan.targetHeight,
                        Presentation.LAYOUT_SCALE_TO_FIT
                    )
                )
                Tv4kEffectStrategy.LANCZOS -> listOf(
                    LanczosResample.scaleToFit(plan.targetWidth, plan.targetHeight)
                )
            }
            localPlayer.setVideoEffects(effects)
            current4kMode = effectiveMode
            if (previousMode.trackConstraint != effectiveMode.trackConstraint) {
                applyTrackConstraint(localPlayer, effectiveMode.trackConstraint, resumePosition, shouldResumePlayback)
            }
            glue?.update4kAction(effectiveMode)
            Log.i(
                TAG,
                "TV effect mode=$effectiveMode strategy=${plan.strategy} " +
                    "target=${plan.targetWidth}x${plan.targetHeight} bypass=${plan.bypass}"
            )
            if (announce && mode.isEnabled && effectiveMode != mode && supports4kOutput) {
                requireContext().showShortToast(
                    getString(
                        R.string.player_4k_mode_downgraded,
                        getString(effectiveMode.labelRes)
                    )
                )
            } else if (announce && effectiveMode == mode) {
                requireContext().showShortToast(
                    getString(R.string.player_4k_mode_applied, getString(effectiveMode.labelRes))
                )
            }
            effectiveMode
        }.getOrElse { error ->
            Log.w(TAG, "Unable to apply TV quality mode $mode", error)
            localPlayer.setVideoEffects(emptyList())
            current4kMode = Tv4kMode.OFF
            applyTrackConstraint(
                localPlayer,
                Tv4kMode.OFF.trackConstraint,
                localPlayer.currentPosition.coerceAtLeast(0L),
                localPlayer.playWhenReady
            )
            glue?.update4kAction(Tv4kMode.OFF)
            requireContext().showShortToast(getString(R.string.player_fast_4k_unavailable))
            Tv4kMode.OFF
        }
    }

    private fun downgrade4kMode() {
        val fallback = current4kMode.fallback()
        if (!current4kMode.isEnabled) return
        val applied = apply4kMode(fallback, announce = false)
        requireContext().showShortToast(
            getString(R.string.player_4k_mode_downgraded, getString(applied.labelRes))
        )
    }

    private fun supports4kOutput(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return false
        @Suppress("DEPRECATION")
        val mode = requireActivity().windowManager.defaultDisplay.mode
        val longEdge = maxOf(mode.physicalWidth, mode.physicalHeight)
        val shortEdge = minOf(mode.physicalWidth, mode.physicalHeight)
        return longEdge >= FAST_4K_WIDTH && shortEdge >= FAST_4K_HEIGHT
    }

    private fun isLowRamDevice(): Boolean =
        (requireContext().getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)
            ?.isLowRamDevice == true

    private fun applyTrackConstraint(
        localPlayer: ExoPlayer,
        constraint: Tv4kTrackConstraint,
        resumePosition: Long,
        shouldResumePlayback: Boolean
    ) {
        val selector = trackSelector ?: return
        selector.setParameters(
            selector.buildUponParameters()
                .setMaxVideoSize(constraint.maxWidth, constraint.maxHeight)
                .setMaxVideoBitrate(constraint.maxBitrate)
                .setExceedVideoConstraintsIfNecessary(false)
        )
        // Re-select an adaptive HLS variant immediately, without losing progress.
        localPlayer.prepare()
        if (resumePosition > 0L) localPlayer.seekTo(resumePosition)
        localPlayer.playWhenReady = shouldResumePlayback
    }

    private fun PlaybackException.isVideoEffectFailure(): Boolean =
        errorCode == PlaybackException.ERROR_CODE_VIDEO_FRAME_PROCESSOR_INIT_FAILED ||
            errorCode == PlaybackException.ERROR_CODE_VIDEO_FRAME_PROCESSING_FAILED

    private fun openEpisodeChooser() {
        ChooseEpisodeDialog(
            dataList = viewModel.playList,
            defaultSelectIndex = viewModel.playIndex,
            viewWidthDp = EPISODE_PANEL_WIDTH_DP,
            title = getString(R.string.player_episode_picker_title),
            getText = { _, item -> item.episode }
        ) { index, _ ->
            viewModel.playEpisodeOfIndex(index)
        }.showNow(parentFragmentManager, TAG_EPISODE_CHOOSER)
    }

    private fun showPlaybackFailure(message: String) {
        val episodeLabel = viewModel.currentEpisodeLabel()
        if (fallbackEpisodeLabel != episodeLabel) {
            fallbackEpisodeLabel = episodeLabel
            failedPlaylistIndexes.clear()
        }
        failedPlaylistIndexes += viewModel.currentPlaylistIndex()
        val fallback = viewModel.sourceFallbackCandidates(failedPlaylistIndexes).firstOrNull()
        pendingSourceFallback = fallback
        glue?.showSourceFallbackAction(fallback != null)
        playerHeaderEpisode?.text = fallback?.let {
            getString(
                R.string.player_source_fallback_hint,
                it.sourceName.ifBlank { getString(R.string.player_switch_source) }
            )
        } ?: getString(R.string.player_retry_hint)
        showPlayerHeader()
        glue?.host?.showControlsOverlay(true)
        requireContext().showLongToast(
            getString(R.string.player_load_error_template, message)
        )
    }

    private fun confirmSourceFallback() {
        val fallback = pendingSourceFallback ?: return
        val resumePositionMs = player?.currentPosition?.coerceAtLeast(0L) ?: 0L
        snapshotPlaybackPosition()
        pendingSourceFallback = null
        glue?.showSourceFallbackAction(false)
        if (viewModel.switchToSourceFallback(fallback, resumePositionMs)) {
            progressBarManager.show()
            playerHeaderEpisode?.text = getString(R.string.player_loading_episode)
            showPlayerHeader()
        }
    }

    fun handlePlaybackKeyEvent(event: KeyEvent): Boolean {
        if (seasonFinaleVisible) {
            if (event.keyCode == KeyEvent.KEYCODE_BACK) {
                if (event.action == KeyEvent.ACTION_UP) activity?.finish()
                return true
            }
            // Everything else belongs to the sheet's own focus targets.
            return false
        }
        if (
            event.keyCode == KeyEvent.KEYCODE_BACK &&
            event.action == KeyEvent.ACTION_UP &&
            !areTransportControlsVisible() &&
            skipSegmentButton?.hasFocus() != true &&
            continueOutroButton?.hasFocus() != true
        ) {
            // Leaving during the final episode's credits counts as finishing the season.
            val localPlayer = player
            if (
                localPlayer != null &&
                showSeasonFinaleIfEligible(
                    positionMs = localPlayer.currentPosition.coerceAtLeast(0L),
                    durationMs = localPlayer.contentDuration.coerceAtLeast(0L)
                )
            ) {
                return true
            }
        }
        if (isCenterKey(event.keyCode) && centerKeyController.isActive) return handleCenterKey(event)
        if (handleTransientSkipPromptKey(event)) return true
        updateControlsAutoHideFor(event)
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            cancelAutoNextForRemoteInteraction()
            refreshTransientSkipAutoHideForRemoteInteraction()
            if (!isCenterKey(event.keyCode)) showPlayerHeader()
        }

        val primaryFocused = skipSegmentButton?.hasFocus() == true
        val continueFocused = continueOutroButton?.hasFocus() == true
        if (handleSkipFocusDirection(event, primaryFocused, continueFocused)) return true
        if (primaryFocused || continueFocused) {
            return handleSkipActionKey(event, continueFocused)
        }

        if (isCenterKey(event.keyCode)) {
            return when (
                PlaybackCenterKeyRoutingPolicy.route(
                    controlsOverlayVisible = areTransportControlsVisible()
                )
            ) {
                CenterKeyRoute.GLOBAL_PLAYBACK -> handleCenterKey(event)
                CenterKeyRoute.FOCUSED_CONTROL -> {
                    // Let Android dispatch OK to the focused action or progress bar.
                    view?.removeCallbacks(centerLongPressRunnable)
                    applyCenterKeyAction(centerKeyController.cancel())
                    false
                }
            }
        }

        if (event.action == KeyEvent.ACTION_DOWN && !centerKeyController.isLocked) {
            view?.removeCallbacks(centerLongPressRunnable)
            applyCenterKeyAction(centerKeyController.cancel())
        }
        return glue?.onKey(view, event.keyCode, event) == true
    }

    private fun handleTransientSkipPromptKey(event: KeyEvent): Boolean {
        val keyKind = when {
            isCenterKey(event.keyCode) -> PlaybackSkipPromptKeyKind.CONFIRM
            event.keyCode == KeyEvent.KEYCODE_DPAD_UP ||
                event.keyCode == KeyEvent.KEYCODE_DPAD_DOWN ||
                event.keyCode == KeyEvent.KEYCODE_DPAD_LEFT ||
                event.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT -> {
                PlaybackSkipPromptKeyKind.DIRECTION
            }
            else -> return false
        }
        val action = when (event.action) {
            KeyEvent.ACTION_DOWN -> skipPromptKeyController.onKeyDown(
                keyId = event.keyCode,
                keyKind = keyKind,
                promptState = skipUiState.promptState(),
                playerControlsVisible = areTransportControlsVisible(),
                repeatCount = event.repeatCount
            )
            KeyEvent.ACTION_UP -> skipPromptKeyController.onKeyUp(event.keyCode)
            else -> PlaybackSkipPromptKeyAction.PASS_THROUGH
        }
        return when (action) {
            PlaybackSkipPromptKeyAction.PASS_THROUGH -> false
            PlaybackSkipPromptKeyAction.CONSUME -> true
            PlaybackSkipPromptKeyAction.REVEAL_PROMPT -> {
                wakeTransientSkipForRemoteInteraction()
                true
            }
            PlaybackSkipPromptKeyAction.SHOW_PLAYER_CONTROLS -> {
                skipUiState.onPlayerControlsShown()
                glue?.host?.showControlsOverlay(true)
                showPlayerHeader()
                scheduleControlsAutoHide()
                scheduleTransientSkipAutoHide()
                true
            }
            PlaybackSkipPromptKeyAction.ACTIVATE_SKIP -> {
                activateSkipSegment()
                true
            }
        }
    }

    private fun handleSkipActionKey(
        event: KeyEvent,
        continueFocused: Boolean
    ): Boolean = when (event.keyCode) {
        KeyEvent.KEYCODE_DPAD_CENTER,
        KeyEvent.KEYCODE_ENTER,
        KeyEvent.KEYCODE_NUMPAD_ENTER -> {
            if (event.action == KeyEvent.ACTION_UP) {
                if (continueFocused) continueOutroButton?.performClick()
                else activateSkipSegment()
            }
            true
        }
        KeyEvent.KEYCODE_BACK -> {
            if (event.action == KeyEvent.ACTION_UP) restoreTransportFocus()
            true
        }
        else -> glue?.onKey(view, event.keyCode, event) == true
    }

    private fun handleCenterKey(event: KeyEvent): Boolean {
        when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                applyCenterKeyAction(centerKeyController.onKeyDown(event.eventTime, event.repeatCount))
                view?.removeCallbacks(centerLongPressRunnable)
                view?.post(centerLongPressRunnable)
            }
            KeyEvent.ACTION_UP -> {
                applyCenterKeyAction(centerKeyController.onKeyUp(event.eventTime))
                view?.removeCallbacks(centerLongPressRunnable)
                if (centerKeyController.isActive && !centerKeyController.isLocked) view?.post(centerLongPressRunnable)
            }
        }
        return true
    }

    private fun applyCenterKeyAction(action: CenterKeyAction) {
        val localPlayer = player ?: return
        when (action) {
            CenterKeyAction.NONE -> Unit
            CenterKeyAction.SHORT_PRESS -> {
                if (localPlayer.isPlaying) localPlayer.pause() else localPlayer.play()
                glue?.host?.showControlsOverlay(true)
                scheduleControlsAutoHide()
                showPlayerHeader()
            }
            CenterKeyAction.START_BOOST -> {
                speedBeforeBoost = localPlayer.playbackParameters.speed
                wasPlayingBeforeBoost = localPlayer.isPlaying
                if (!wasPlayingBeforeBoost) localPlayer.play()
                localPlayer.setPlaybackSpeed(TEMPORARY_BOOST_SPEED)
                hidePlayerChromeForBoost()
                showSpeedBoostIndicator()
            }
            CenterKeyAction.LOCK_BOOST -> {
                speedBoostIndicator?.updateCharge(1f, true)
            }
            CenterKeyAction.STOP_BOOST -> {
                localPlayer.setPlaybackSpeed(speedBeforeBoost.coerceAtLeast(0.1f))
                if (!wasPlayingBeforeBoost) localPlayer.pause()
                hideSpeedBoostIndicator()
                view?.removeCallbacks(centerLongPressRunnable)
            }
        }
    }

    private fun showSpeedBoostIndicator() {
        speedBoostIndicator?.showBoost()
    }

    private fun hideSpeedBoostIndicator() {
        speedBoostIndicator?.hideBoost()
    }

    private fun hidePlayerChromeForBoost() {
        glue?.host?.hideControlsOverlay(false)
        hidePlayerHeaderNow()
    }

    private fun hidePlayerHeaderNow() {
        playerHeader?.removeCallbacks(hideHeaderRunnable)
        listOfNotNull(playerHeader, playerHeaderScrim).forEach { chrome ->
            chrome.animate().cancel()
            chrome.alpha = 0f
            chrome.visibility = View.GONE
        }
    }

    private fun showPlayerHeader() {
        val header = playerHeader ?: return
        header.removeCallbacks(hideHeaderRunnable)
        listOfNotNull(header, playerHeaderScrim).forEach { chrome ->
            chrome.animate().cancel()
            chrome.visibility = View.VISIBLE
            chrome.animate()
                .alpha(1f)
                .setDuration(HEADER_FADE_IN_MS)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }
        header.postDelayed(hideHeaderRunnable, PLAYER_HEADER_VISIBLE_MS)
    }

    private fun bindPlaybackFocusTargets() {
        val root = view ?: return
        val dock = root.findViewById<ViewGroup>(LeanbackR.id.controls_dock)
        primaryControlsDock = dock
        playbackProgress = root.findViewById(LeanbackR.id.playback_progress)
        val focusables = ArrayList<View>()
        dock?.addFocusables(focusables, View.FOCUS_FORWARD, View.FOCUSABLES_ALL)
        lastPrimaryControl = focusables.maxByOrNull { candidate ->
            val location = IntArray(2)
            candidate.getLocationOnScreen(location)
            location[0] + candidate.width
        }
        val finalControl = lastPrimaryControl
        if (finalControl != null) {
            focusBindAttempts = 0
        } else if (focusBindAttempts < MAX_FOCUS_BIND_ATTEMPTS) {
            focusBindAttempts += 1
            root.postDelayed(::bindPlaybackFocusTargets, FOCUS_BIND_RETRY_MS)
        }
    }

    private fun isTransportControlFocused(): Boolean {
        val focused = requireActivity().currentFocus ?: return false
        return focused === playbackProgress ||
            focused === primaryControlsDock ||
            isDescendantOf(focused, primaryControlsDock)
    }

    private fun handleSkipFocusDirection(
        event: KeyEvent,
        primaryFocused: Boolean,
        continueFocused: Boolean
    ): Boolean {
        val direction = when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> PlaybackSkipDirection.UP
            KeyEvent.KEYCODE_DPAD_DOWN -> PlaybackSkipDirection.DOWN
            KeyEvent.KEYCODE_DPAD_LEFT -> PlaybackSkipDirection.LEFT
            KeyEvent.KEYCODE_DPAD_RIGHT -> PlaybackSkipDirection.RIGHT
            else -> return false
        }
        val zone = when {
            primaryFocused -> PlaybackSkipFocusZone.PRIMARY_ACTION
            continueFocused -> PlaybackSkipFocusZone.SECONDARY_ACTION
            isTransportControlFocused() -> PlaybackSkipFocusZone.TRANSPORT
            else -> return false
        }
        val actionsVisible = areSkipActionsShown()
        if (
            zone == PlaybackSkipFocusZone.TRANSPORT &&
            (!actionsVisible || direction != PlaybackSkipDirection.UP)
        ) {
            return false
        }
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (
                PlaybackSkipFocusPolicy.action(
                    zone = zone,
                    direction = direction,
                    actionsVisible = actionsVisible,
                    secondaryVisible = continueOutroButton?.visibility == View.VISIBLE
                )
            ) {
                PlaybackSkipFocusAction.KEEP_CURRENT -> Unit
                PlaybackSkipFocusAction.ENTER_PRIMARY -> {
                    lastTransportFocus = requireActivity().currentFocus
                    skipFocusWasAutomatic = false
                    setSkipActionsFocusable(true)
                    skipSegmentButton?.requestFocus()
                }
                PlaybackSkipFocusAction.ENTER_SECONDARY -> continueOutroButton?.requestFocus()
                PlaybackSkipFocusAction.RETURN_TO_TRANSPORT -> restoreTransportFocus()
                PlaybackSkipFocusAction.RETURN_TO_PRIMARY_CONTROLS -> {
                    restoreTransportFocus(preferPrimaryControls = true)
                }
            }
        }
        return true
    }

    private fun isDescendantOf(view: View, ancestor: ViewGroup?): Boolean {
        var parent: android.view.ViewParent? = view.parent
        while (parent is View) {
            if (parent === ancestor) return true
            parent = (parent as android.view.ViewParent).parent
        }
        return false
    }

    private fun isCenterKey(keyCode: Int): Boolean =
        keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
            keyCode == KeyEvent.KEYCODE_ENTER ||
            keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER

    private fun areTransportControlsVisible(): Boolean =
        glue?.host?.isControlsOverlayVisible == true

    private fun restoreTransportFocus(
        showControls: Boolean = true,
        preferPrimaryControls: Boolean = false
    ) {
        skipSegmentButton?.clearFocus()
        continueOutroButton?.clearFocus()
        skipFocusWasAutomatic = false
        setSkipActionsFocusable(false)
        if (showControls) {
            glue?.host?.showControlsOverlay(true)
            scheduleControlsAutoHide()
        }
        view?.post {
            val root = view ?: return@post
            val primaryControl = lastPrimaryControl?.takeIf {
                it.isAttachedToWindow && it.isFocusable
            }
            val target = if (preferPrimaryControls) {
                primaryControl
            } else {
                lastTransportFocus?.takeIf { it.isAttachedToWindow && it.isFocusable }
                    ?: primaryControl
            }
            if (target?.requestFocus() != true) root.requestFocus()
        }
    }

    private fun setSkipActionsFocusable(enabled: Boolean) {
        skipSegmentButton?.isFocusable = enabled
        continueOutroButton?.isFocusable =
            enabled && continueOutroButton?.visibility == View.VISIBLE
    }

    private fun updateControlsAutoHideFor(event: KeyEvent) {
        when (event.action) {
            KeyEvent.ACTION_DOWN -> cancelControlsAutoHide()
            KeyEvent.ACTION_UP -> scheduleControlsAutoHide()
        }
    }

    private fun scheduleControlsAutoHide() {
        val root = view ?: return
        root.removeCallbacks(hideControlsRunnable)
        if (player?.isPlaying == true) {
            root.postDelayed(
                hideControlsRunnable,
                PlaybackControlsAutoHidePolicy.IDLE_TIMEOUT_MS
            )
        }
    }

    private fun cancelControlsAutoHide() {
        view?.removeCallbacks(hideControlsRunnable)
    }

    private fun cancelAutoNextForRemoteInteraction() {
        if (skipUiState.onRemoteInteraction()) {
            skipSegmentButton?.cancelCountdown()
        }
    }

    private fun wakeTransientSkipForRemoteInteraction() {
        if (!skipUiState.revealTransientAction()) return
        renderSkipSegment(player?.currentPosition?.coerceAtLeast(0L) ?: 0L)
        scheduleTransientSkipAutoHide()
    }

    private fun refreshTransientSkipAutoHideForRemoteInteraction() {
        when (skipUiState.promptState()) {
            PlaybackSkipPromptState.VISIBLE,
            PlaybackSkipPromptState.REVEALED -> scheduleTransientSkipAutoHide()
            PlaybackSkipPromptState.INACTIVE,
            PlaybackSkipPromptState.HIDDEN -> Unit
        }
    }

    private fun scheduleTransientSkipAutoHide() {
        val root = view ?: return
        root.removeCallbacks(hideTransientSkipRunnable)
        root.postDelayed(
            hideTransientSkipRunnable,
            PlaybackSkipUiStateMachine.TRANSIENT_SKIP_VISIBLE_MS
        )
    }

    private fun cancelTransientSkipAutoHide() {
        view?.removeCallbacks(hideTransientSkipRunnable)
    }

    private fun animateSkipActionFocus(target: View, hasFocus: Boolean) {
        target.isSelected = hasFocus
        target.animate().cancel()
        target.animate()
            .scaleX(if (hasFocus) PlaybackSkipLayoutPolicy.FOCUS_SCALE else 1f)
            .scaleY(if (hasFocus) PlaybackSkipLayoutPolicy.FOCUS_SCALE else 1f)
            .translationZ(if (hasFocus) 6f * resources.displayMetrics.density else 0f)
            // A slight overshoot on the way up reads like the spring focus used elsewhere.
            .setInterpolator(if (hasFocus) OvershootInterpolator(1.6f) else DecelerateInterpolator())
            .setDuration(if (hasFocus) 220L else 160L)
            .start()
        target.invalidate()
    }

    override fun onDestroyView() {
        view?.removeCallbacks(centerLongPressRunnable)
        clearSkipState()
        cancelControlsAutoHide()
        applyCenterKeyAction(centerKeyController.cancel())
        playerHeader?.removeCallbacks(hideHeaderRunnable)
        speedBoostIndicator?.hideBoost()
        skipSegmentButton?.setOnClickListener(null)
        skipSegmentButton?.onFocusChangeListener = null
        skipSegmentButton?.cancelCountdown()
        continueOutroButton?.setOnClickListener(null)
        continueOutroButton?.onFocusChangeListener = null
        skipSegmentActions = null
        skipSegmentButton = null
        continueOutroButton = null
        seasonFinaleView = null
        primaryControlsDock = null
        playbackProgress = null
        lastPrimaryControl = null
        lastTransportFocus = null
        skipFocusWasAutomatic = false
        focusBindAttempts = 0
        playerHeader = null
        playerHeaderScrim = null
        playerHeaderTitle = null
        playerHeaderEpisode = null
        speedBoostIndicator = null
        super.onDestroyView()
    }

    private fun renderSkipSegment(positionMs: Long) {
        val button = skipSegmentButton ?: return
        val durationMs = player?.contentDuration?.coerceAtLeast(0L) ?: 0L
        if (
            !nearEndAutoAdvanceSuppressed &&
            PlaybackSkipPolicy.shouldAutoAdvanceAtEnd(
                positionMs,
                durationMs,
                viewModel.hasNextEpisode()
            )
        ) {
            advanceToNextEpisode()
            return
        }
        val active = PlaybackSkipPolicy.activeSkip(
            viewModel.playbackSegments.value,
            positionMs,
            viewModel.hasNextEpisode(),
            durationMs
        )
        if (active == null) {
            scheduleSkipUiExit()
            return
        }
        cancelScheduledSkipUiExit()
        val decision = skipUiState.update(active)
        activePlaybackSkip = decision.active
        if (!decision.isVisible) {
            hideSkipUi(allowPlayerControlsRestore = false)
            return
        }
        button.text = when {
            active.type == ActivePlaybackSkip.Type.INTRO -> getString(R.string.player_skip_intro)
            active.advancesEpisode -> nextEpisodeLabel()
            else -> getString(R.string.player_skip_outro)
        }
        continueOutroButton?.visibility = if (active.advancesEpisode) View.VISIBLE else View.GONE
        if (skipSegmentButton?.hasFocus() != true && continueOutroButton?.hasFocus() != true) {
            setSkipActionsFocusable(false)
        }
        val actions = skipSegmentActions ?: return
        if (!areSkipActionsShown()) revealSkipActions(actions, button)
        if (decision.shouldRequestInitialFocus) {
            lastTransportFocus = requireActivity().currentFocus
            skipFocusWasAutomatic = true
            setSkipActionsFocusable(true)
            if (!button.requestFocus()) {
                skipFocusWasAutomatic = false
                setSkipActionsFocusable(false)
            }
        }
        if (decision.shouldStartCountdown) {
            button.cancelCountdown()
            button.startCountdown(PlaybackSkipUiStateMachine.AUTO_NEXT_COUNTDOWN_MS) {
                if (activePlaybackSkip == active) advanceToNextEpisode()
            }
            if (player?.isPlaying != true) button.pauseCountdown()
        }
        if (decision.shouldScheduleAutoHide) scheduleTransientSkipAutoHide()
    }

    private fun hideSkipUi(allowPlayerControlsRestore: Boolean = true) {
        cancelTransientSkipAutoHide()
        cancelScheduledSkipUiExit()
        val shouldRestoreTransport = skipSegmentButton?.hasFocus() == true ||
            continueOutroButton?.hasFocus() == true
        val restoreWithoutShowingControls = skipFocusWasAutomatic
        // Keep the filled bar while the prompt fades, so it never flashes back to empty.
        skipSegmentButton?.freezeCountdown()
        skipSegmentButton?.clearFocus()
        continueOutroButton?.clearFocus()
        setSkipActionsFocusable(false)
        dismissSkipActions()
        if (shouldRestoreTransport) {
            restoreTransportFocus(
                showControls = allowPlayerControlsRestore && !restoreWithoutShowingControls
            )
        } else {
            skipFocusWasAutomatic = false
        }
    }

    private fun areSkipActionsShown(): Boolean =
        skipSegmentActions?.visibility == View.VISIBLE && !skipActionsExiting

    /** Rises into place on a decelerating curve, like a tvOS prompt, instead of popping in. */
    private fun revealSkipActions(actions: View, button: CountdownActionButton) {
        actions.animate().cancel()
        button.clearIdleProgress()
        if (actions.visibility != View.VISIBLE) {
            actions.alpha = 0f
            actions.translationY = SKIP_ACTIONS_RISE_DP * resources.displayMetrics.density
        }
        // Interrupting a fade-out simply turns it around from where it is.
        skipActionsExiting = false
        actions.visibility = View.VISIBLE
        actions.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(SKIP_ACTIONS_ENTER_MS)
            .setInterpolator(EmphasizedDecelerateInterpolator)
            .start()
    }

    private fun dismissSkipActions() {
        val actions = skipSegmentActions ?: return
        actions.animate().cancel()
        if (actions.visibility != View.VISIBLE || !actions.isAttachedToWindow) {
            finishSkipActionsExit(actions)
            return
        }
        skipActionsExiting = true
        actions.animate()
            .alpha(0f)
            .translationY(SKIP_ACTIONS_SINK_DP * resources.displayMetrics.density)
            .setDuration(SKIP_ACTIONS_EXIT_MS)
            .setInterpolator(AccelerateInterpolator())
            .withEndAction { if (skipActionsExiting) finishSkipActionsExit(actions) }
            .start()
    }

    private fun finishSkipActionsExit(actions: View) {
        skipActionsExiting = false
        actions.visibility = View.GONE
        actions.alpha = 1f
        actions.translationY = 0f
        continueOutroButton?.visibility = View.GONE
        skipSegmentButton?.cancelCountdown()
    }

    private fun nextEpisodeLabel(): String {
        val next = viewModel.playList.getOrNull(viewModel.playIndex + 1)?.episode.orEmpty()
        val number = favoriteEpisodeNumber(next)
        return if (number > 0) {
            getString(R.string.player_next_episode_numbered, number)
        } else {
            getString(R.string.player_next_episode)
        }
    }

    private fun clearSkipState() {
        cancelTransientSkipAutoHide()
        cancelScheduledSkipUiExit()
        skipUiState.reset()
        skipPromptKeyController.reset()
        activePlaybackSkip = null
        hideSkipUi()
    }

    private fun scheduleSkipUiExit() {
        if (!areSkipActionsShown()) {
            skipExitGrace.clear()
            skipUiState.update(null)
            skipPromptKeyController.reset()
            activePlaybackSkip = null
            hideSkipUi()
            return
        }
        val root = view ?: return
        val delayMs = skipExitGrace.scheduleExit(SystemClock.uptimeMillis())
        root.removeCallbacks(hideSkipUiAfterGraceRunnable)
        root.postDelayed(hideSkipUiAfterGraceRunnable, delayMs)
    }

    private fun cancelScheduledSkipUiExit() {
        view?.removeCallbacks(hideSkipUiAfterGraceRunnable)
        skipExitGrace.onSegmentActive()
    }

    private fun activateSkipSegment() {
        val active = activePlaybackSkip ?: return
        when {
            active.advancesEpisode -> advanceToNextEpisode()
            active.type == ActivePlaybackSkip.Type.OUTRO -> {
                val episodeIndex = viewModel.playIndex
                clearSkipState()
                viewModel.completeCurrentEpisode {
                    if (isAdded && viewModel.playIndex == episodeIndex) {
                        player?.seekTo(active.targetMs)
                    }
                }
            }
            else -> {
                player?.seekTo(active.targetMs)
                clearSkipState()
            }
        }
    }

    private fun advanceToNextEpisode() {
        val episodeIndex = viewModel.playIndex
        if (handledEndedEpisodeIndex == episodeIndex) return
        handledEndedEpisodeIndex = episodeIndex
        val hasNextEpisode = viewModel.hasNextEpisode()
        val positionMs = player?.currentPosition?.coerceAtLeast(0L) ?: 0L
        val durationMs = player?.contentDuration?.coerceAtLeast(0L) ?: 0L
        clearSkipState()
        viewModel.completeCurrentEpisode {
            if (!isAdded || viewModel.playIndex != episodeIndex) return@completeCurrentEpisode
            if (hasNextEpisode) {
                viewModel.playNextEpisodeAdjacent()
            } else if (!showSeasonFinaleIfEligible(positionMs, durationMs)) {
                requireActivity().finish()
            }
        }
    }

    /**
     * Shows the rating and what-to-watch-next sheet over the paused last frame when the season's
     * final episode has reached its credits. Returns false when it does not apply.
     */
    private fun showSeasonFinaleIfEligible(positionMs: Long, durationMs: Long): Boolean {
        if (seasonFinaleVisible) return true
        if (!viewModel.shouldOfferSeasonFinale(positionMs, durationMs)) return false
        val content = viewModel.seasonFinale.value ?: return false
        val host = seasonFinaleView ?: return false
        viewModel.markSeasonFinaleShown()
        seasonFinaleVisible = true
        player?.pause()
        clearSkipState()
        cancelControlsAutoHide()
        view?.removeCallbacks(centerLongPressRunnable)
        applyCenterKeyAction(centerKeyController.cancel())
        glue?.host?.hideControlsOverlay(false)
        hidePlayerHeaderNow()
        host.visibility = View.VISIBLE
        host.setAulamaTvContent {
            SeasonFinaleSheet(
                content = content,
                onSaveRating = viewModel::saveSeasonRating,
                onOpenAnime = { anime ->
                    activity?.let { hostActivity ->
                        DetailActivity.startActivity(hostActivity, anime)
                        hostActivity.finish()
                    }
                },
                onHome = {
                    activity?.let { hostActivity ->
                        hostActivity.startActivity(
                            Intent(hostActivity, MainActivity::class.java).addFlags(
                                Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                            )
                        )
                        hostActivity.finish()
                    }
                }
            )
        }
        host.post { host.requestFocus() }
        return true
    }

    private fun playbackStateName(playbackState: Int): String = when (playbackState) {
        Player.STATE_IDLE -> "IDLE"
        Player.STATE_BUFFERING -> "BUFFERING"
        Player.STATE_READY -> "READY"
        Player.STATE_ENDED -> "ENDED"
        else -> "UNKNOWN($playbackState)"
    }

    companion object {
        private const val TAG = "AnimePlayerFragment"
        private const val TAG_EPISODE_CHOOSER = "episode_chooser"
        private const val TAG_4K_CHOOSER = "4k_chooser"
        private const val PLAYER_UPDATE_INTERVAL_MILLIS = 250
        private const val SEEK_INCREMENT_MS = 10_000L
        private const val EPISODE_PANEL_WIDTH_DP = 420
        private const val QUALITY_PANEL_WIDTH_DP = 400
        private const val FAST_4K_WIDTH = 3840
        private const val FAST_4K_HEIGHT = 2160
        private const val FOUR_K_DOWNGRADE_COOLDOWN_MS = 5_000L
        private const val PLAYER_HEADER_VISIBLE_MS = 4_500L
        private const val HEADER_FADE_IN_MS = 220L
        private const val HEADER_FADE_OUT_MS = 360L
        private const val TEMPORARY_BOOST_SPEED = 2f
        private const val FOCUS_BIND_RETRY_MS = 120L
        private const val MAX_FOCUS_BIND_ATTEMPTS = 6
        private const val SKIP_ACTIONS_ENTER_MS = 320L
        private const val SKIP_ACTIONS_EXIT_MS = 180L
        private const val SKIP_ACTIONS_RISE_DP = 18f
        private const val SKIP_ACTIONS_SINK_DP = 8f
        private val EmphasizedDecelerateInterpolator = PathInterpolator(0.05f, 0.7f, 0.1f, 1f)
    }
}
