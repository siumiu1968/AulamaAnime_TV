package com.jing.sakura.player

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.TextView
import androidx.annotation.OptIn
import androidx.core.graphics.drawable.toDrawable
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
    private var glue: ProgressTransportControlGlue<LeanbackPlayerAdapter>? = null
    private var current4kMode = Tv4kMode.OFF
    private var skipSegmentActions: View? = null
    private var skipSegmentButton: CountdownActionButton? = null
    private var continueOutroButton: TextView? = null
    private var activePlaybackSkip: ActivePlaybackSkip? = null
    private val skipUiState = PlaybackSkipUiStateMachine()
    private var primaryControlsDock: ViewGroup? = null
    private var lastPrimaryControl: View? = null
    private var lastTransportFocus: View? = null
    private var focusBindAttempts = 0
    private var playerHeader: View? = null
    private var playerHeaderTitle: TextView? = null
    private var playerHeaderEpisode: TextView? = null
    private var speedBoostIndicator: TextView? = null
    private val centerKeyController = PlaybackCenterKeyController()
    private var speedBeforeBoost = 1f
    private var speedBoostAnimator: ObjectAnimator? = null
    private var last4kDowngradeAtMs = 0L
    private var handledEndedEpisodeIndex = -1

    private val hideHeaderRunnable = Runnable {
        playerHeader?.animate()
            ?.alpha(0f)
            ?.setDuration(180L)
            ?.withEndAction { playerHeader?.visibility = View.GONE }
            ?.start()
    }

    private val centerLongPressRunnable = Runnable {
        if (glue?.host?.isControlsOverlayVisible == false) {
            applyCenterKeyAction(centerKeyController.onLongPressTimeout(SystemClock.uptimeMillis()))
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
                Player.STATE_READY -> progressBarManager.hide()
                Player.STATE_ENDED -> {
                    progressBarManager.hide()
                    advanceToNextEpisode()
                }
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            snapshotPlaybackPosition()
            if (isPlaying) viewModel.startSaveHistory() else viewModel.stopSaveHistory()
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
            playerHeaderEpisode?.text = getString(R.string.player_retry_hint)
            showPlayerHeader()
            requireContext().showLongToast(
                getString(R.string.player_load_error_template, error.errorCodeName)
            )
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
                skipUiState.onSeek()
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
            }
            setOnFocusChangeListener(::animateSkipActionFocus)
        }
        playerHeader = requireActivity().findViewById(R.id.player_header)
        playerHeaderTitle = requireActivity().findViewById(R.id.player_header_title)
        playerHeaderEpisode = requireActivity().findViewById(R.id.player_header_episode)
        speedBoostIndicator = requireActivity().findViewById(R.id.player_speed_boost)
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
        clearSkipState()
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
                playerHeaderEpisode?.text = getString(R.string.player_loading_episode)
                showPlayerHeader()
            }
            is Resource.Error -> {
                progressBarManager.hide()
                playerHeaderEpisode?.text = getString(R.string.player_retry_hint)
                showPlayerHeader()
                requireContext().showLongToast(
                    getString(R.string.player_load_error_template, resource.message)
                )
            }
            is Resource.Success -> loadEpisode(resource.data)
        }
    }

    private fun loadEpisode(payload: EpisodeUrlAndHistory) {
        val localPlayer = player ?: return
        progressBarManager.show()
        handledEndedEpisodeIndex = -1
        clearSkipState()

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
        val trackSelector = DefaultTrackSelector(requireContext()).apply {
            setParameters(
                buildUponParameters()
                    .setMaxVideoSize(1920, 1080)
                    .setMaxVideoBitrate(8_500_000)
                    .setExceedVideoConstraintsIfNecessary(false)
            )
        }
        return ExoPlayer.Builder(requireContext())
            .setTrackSelector(trackSelector)
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
            playPreviousEpisode = viewModel::playPreviousEpisodeIfExists,
            playNextEpisode = ::advanceToNextEpisode,
            open4kModePicker = ::open4kModePicker
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
        glue = null
        current4kMode = Tv4kMode.OFF
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
        if (mode.isEnabled && !supports4kOutput()) {
            requireContext().showShortToast(getString(R.string.player_fast_4k_requires_4k_output))
            return current4kMode
        }
        return runCatching {
            val effects: List<Effect> = when (mode.strategy) {
                Tv4kEffectStrategy.NONE -> emptyList()
                Tv4kEffectStrategy.MATRIX -> listOf(
                    Presentation.createForWidthAndHeight(
                        mode.targetWidth,
                        mode.targetHeight,
                        Presentation.LAYOUT_SCALE_TO_FIT
                    )
                )
                Tv4kEffectStrategy.LANCZOS -> listOf(
                    LanczosResample.scaleToFit(mode.targetWidth, mode.targetHeight)
                )
            }
            localPlayer.setVideoEffects(effects)
            current4kMode = mode
            glue?.update4kAction(mode)
            if (announce) {
                requireContext().showShortToast(
                    getString(R.string.player_4k_mode_applied, getString(mode.labelRes))
                )
            }
            mode
        }.getOrElse { error ->
            Log.w(TAG, "Unable to apply TV quality mode $mode", error)
            localPlayer.setVideoEffects(emptyList())
            current4kMode = Tv4kMode.OFF
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

    fun handlePlaybackKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            showPlayerHeader()
            cancelAutoNextForRemoteInteraction()
        }

        val primaryFocused = skipSegmentButton?.hasFocus() == true
        val continueFocused = continueOutroButton?.hasFocus() == true
        if (primaryFocused || continueFocused) {
            return handleSkipActionKey(event, primaryFocused, continueFocused)
        }

        if (
            event.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT &&
            event.action == KeyEvent.ACTION_DOWN &&
            skipSegmentActions?.visibility == View.VISIBLE &&
            isLastPrimaryControlFocused()
        ) {
            lastTransportFocus = requireActivity().currentFocus
            skipSegmentButton?.requestFocus()
            return true
        }

        if (isCenterKey(event.keyCode) && glue?.host?.isControlsOverlayVisible == false) {
            return handleHiddenCenterKey(event)
        }

        if (event.action == KeyEvent.ACTION_DOWN && !isCenterKey(event.keyCode)) {
            view?.removeCallbacks(centerLongPressRunnable)
            applyCenterKeyAction(centerKeyController.cancel())
        }
        return glue?.onKey(view, event.keyCode, event) == true
    }

    private fun handleSkipActionKey(
        event: KeyEvent,
        primaryFocused: Boolean,
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
        KeyEvent.KEYCODE_DPAD_RIGHT -> {
            if (
                event.action == KeyEvent.ACTION_DOWN &&
                primaryFocused &&
                continueOutroButton?.visibility == View.VISIBLE
            ) {
                continueOutroButton?.requestFocus()
            }
            true
        }
        KeyEvent.KEYCODE_DPAD_LEFT -> {
            if (event.action == KeyEvent.ACTION_DOWN) {
                if (continueFocused) skipSegmentButton?.requestFocus() else restoreTransportFocus()
            }
            true
        }
        KeyEvent.KEYCODE_BACK,
        KeyEvent.KEYCODE_DPAD_DOWN -> {
            if (event.action == KeyEvent.ACTION_UP) restoreTransportFocus()
            true
        }
        else -> glue?.onKey(view, event.keyCode, event) == true
    }

    private fun handleHiddenCenterKey(event: KeyEvent): Boolean {
        when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                val action = centerKeyController.onKeyDown(event.eventTime, event.repeatCount)
                if (event.repeatCount == 0) {
                    view?.removeCallbacks(centerLongPressRunnable)
                    view?.postDelayed(
                        centerLongPressRunnable,
                        PlaybackCenterKeyController.DEFAULT_LONG_PRESS_THRESHOLD_MS
                    )
                }
                applyCenterKeyAction(action)
            }
            KeyEvent.ACTION_UP -> {
                view?.removeCallbacks(centerLongPressRunnable)
                applyCenterKeyAction(centerKeyController.onKeyUp())
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
                showPlayerHeader()
            }
            CenterKeyAction.START_BOOST -> {
                if (!localPlayer.isPlaying) return
                speedBeforeBoost = localPlayer.playbackParameters.speed
                localPlayer.setPlaybackSpeed(TEMPORARY_BOOST_SPEED)
                showSpeedBoostIndicator()
            }
            CenterKeyAction.STOP_BOOST -> {
                localPlayer.setPlaybackSpeed(speedBeforeBoost.coerceAtLeast(0.1f))
                hideSpeedBoostIndicator()
            }
        }
    }

    private fun showSpeedBoostIndicator() {
        val indicator = speedBoostIndicator ?: return
        speedBoostAnimator?.cancel()
        indicator.animate().cancel()
        indicator.alpha = 0f
        indicator.scaleX = 0.96f
        indicator.scaleY = 0.96f
        indicator.visibility = View.VISIBLE
        indicator.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(160L)
            .start()
        speedBoostAnimator = ObjectAnimator.ofFloat(indicator, View.ALPHA, 1f, 0.72f).apply {
            duration = 520L
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            startDelay = 180L
            start()
        }
    }

    private fun hideSpeedBoostIndicator() {
        speedBoostAnimator?.cancel()
        speedBoostAnimator = null
        speedBoostIndicator?.animate()
            ?.alpha(0f)
            ?.setDuration(120L)
            ?.withEndAction { speedBoostIndicator?.visibility = View.GONE }
            ?.start()
    }

    private fun showPlayerHeader() {
        val header = playerHeader ?: return
        header.removeCallbacks(hideHeaderRunnable)
        header.animate().cancel()
        header.visibility = View.VISIBLE
        header.animate().alpha(1f).setDuration(140L).start()
        header.postDelayed(hideHeaderRunnable, PLAYER_HEADER_VISIBLE_MS)
    }

    private fun bindPlaybackFocusTargets() {
        val root = view ?: return
        val dock = root.findViewById<ViewGroup>(LeanbackR.id.controls_dock)
        primaryControlsDock = dock
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
            finalControl.nextFocusRightId = R.id.player_skip_segment
        } else if (focusBindAttempts < MAX_FOCUS_BIND_ATTEMPTS) {
            focusBindAttempts += 1
            root.postDelayed(::bindPlaybackFocusTargets, FOCUS_BIND_RETRY_MS)
        }
    }

    private fun isLastPrimaryControlFocused(): Boolean {
        val focused = requireActivity().currentFocus ?: return false
        val last = lastPrimaryControl
        return focused === last || (last == null && isDescendantOf(focused, primaryControlsDock))
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

    private fun restoreTransportFocus() {
        skipSegmentButton?.clearFocus()
        continueOutroButton?.clearFocus()
        glue?.host?.showControlsOverlay(true)
        view?.post {
            val target = lastTransportFocus?.takeIf { it.isAttachedToWindow && it.isFocusable }
                ?: lastPrimaryControl?.takeIf { it.isAttachedToWindow && it.isFocusable }
            target?.requestFocus()
        }
    }

    private fun cancelAutoNextForRemoteInteraction() {
        if (skipUiState.onRemoteInteraction()) {
            skipSegmentButton?.cancelCountdown()
        }
    }

    private fun animateSkipActionFocus(target: View, hasFocus: Boolean) {
        target.isSelected = hasFocus
        target.animate().cancel()
        target.animate()
            .scaleX(if (hasFocus) 1.04f else 1f)
            .scaleY(if (hasFocus) 1.04f else 1f)
            .setDuration(140L)
            .start()
        target.invalidate()
    }

    override fun onDestroyView() {
        view?.removeCallbacks(centerLongPressRunnable)
        applyCenterKeyAction(centerKeyController.cancel())
        playerHeader?.removeCallbacks(hideHeaderRunnable)
        speedBoostAnimator?.cancel()
        skipSegmentButton?.setOnClickListener(null)
        skipSegmentButton?.onFocusChangeListener = null
        skipSegmentButton?.cancelCountdown()
        continueOutroButton?.setOnClickListener(null)
        continueOutroButton?.onFocusChangeListener = null
        skipSegmentActions = null
        skipSegmentButton = null
        continueOutroButton = null
        activePlaybackSkip = null
        primaryControlsDock = null
        lastPrimaryControl = null
        lastTransportFocus = null
        focusBindAttempts = 0
        playerHeader = null
        playerHeaderTitle = null
        playerHeaderEpisode = null
        speedBoostIndicator = null
        super.onDestroyView()
    }

    private fun renderSkipSegment(positionMs: Long) {
        val button = skipSegmentButton ?: return
        val durationMs = player?.contentDuration?.coerceAtLeast(0L) ?: 0L
        if (PlaybackSkipPolicy.shouldAutoAdvanceAtEnd(positionMs, durationMs, viewModel.hasNextEpisode())) {
            advanceToNextEpisode()
            return
        }
        val active = PlaybackSkipPolicy.activeSkip(
            viewModel.playbackSegments.value,
            positionMs,
            viewModel.hasNextEpisode()
        )
        val decision = skipUiState.update(active)
        activePlaybackSkip = decision.active
        if (!decision.isVisible || active == null) {
            hideSkipUi()
            return
        }
        button.text = getString(
            when {
                active.type == ActivePlaybackSkip.Type.INTRO -> R.string.player_skip_intro
                active.advancesEpisode -> R.string.player_next_episode
                else -> R.string.player_skip_outro
            }
        )
        continueOutroButton?.visibility = if (active.advancesEpisode) View.VISIBLE else View.GONE
        val actions = skipSegmentActions ?: return
        if (actions.visibility != View.VISIBLE) {
            actions.alpha = 0f
            actions.visibility = View.VISIBLE
            actions.animate().alpha(1f).setDuration(180L).start()
        }
        if (decision.shouldRequestInitialFocus) {
            button.post {
                if (activePlaybackSkip == active && actions.visibility == View.VISIBLE) {
                    lastTransportFocus = requireActivity().currentFocus
                    button.requestFocus()
                }
            }
        }
        if (decision.shouldStartCountdown) {
            button.cancelCountdown()
            button.startCountdown(PlaybackSkipUiStateMachine.AUTO_NEXT_COUNTDOWN_MS) {
                if (activePlaybackSkip == active) advanceToNextEpisode()
            }
        }
    }

    private fun hideSkipUi() {
        skipSegmentButton?.cancelCountdown()
        skipSegmentActions?.apply {
            animate().cancel()
            visibility = View.GONE
        }
        skipSegmentButton?.apply {
            clearFocus()
        }
        continueOutroButton?.apply {
            visibility = View.GONE
            clearFocus()
        }
    }

    private fun clearSkipState() {
        skipUiState.reset()
        activePlaybackSkip = null
        hideSkipUi()
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
        clearSkipState()
        viewModel.completeCurrentEpisode {
            if (!isAdded || viewModel.playIndex != episodeIndex) return@completeCurrentEpisode
            if (hasNextEpisode) {
                viewModel.playNextEpisodeAdjacent()
            } else {
                requireActivity().finish()
            }
        }
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
        private const val TEMPORARY_BOOST_SPEED = 2f
        private const val FOCUS_BIND_RETRY_MS = 120L
        private const val MAX_FOCUS_BIND_ATTEMPTS = 6
    }
}
