package com.jing.sakura.player

import android.app.Activity
import android.content.Context
import android.view.KeyEvent
import android.view.View
import androidx.core.content.ContextCompat
import androidx.leanback.media.PlaybackTransportControlGlue
import androidx.leanback.media.PlayerAdapter
import androidx.leanback.widget.PlaybackRowPresenter
import androidx.leanback.widget.PlaybackTransportRowPresenter
import androidx.leanback.widget.Action
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.PlaybackControlsRow.FastForwardAction
import androidx.leanback.widget.PlaybackControlsRow.PlayPauseAction
import androidx.leanback.widget.PlaybackControlsRow.RewindAction
import androidx.leanback.widget.PlaybackControlsRow.SkipNextAction
import androidx.leanback.widget.PlaybackControlsRow.SkipPreviousAction
import com.jing.sakura.R

/** Official Leanback transport controls with only episode-specific extensions. */
internal class ProgressTransportControlGlue<T : PlayerAdapter>(
    context: Context,
    private val activity: Activity,
    impl: T,
    private val onPlayPauseAction: (PlayPauseAction) -> Boolean = { false },
    private val updateProgress: () -> Unit,
    private val chooseEpisode: () -> Unit,
    private val switchSourceFallback: () -> Unit,
    private val playPreviousEpisode: () -> Unit,
    private val playNextEpisode: () -> Unit,
    private val open4kModePicker: () -> Unit,
    private val openSpeedPicker: () -> Unit,
    private val openSegmentEditor: () -> Unit = {}
) : PlaybackTransportControlGlue<T>(context, impl) {

    private val appContext = context
    private var secondaryActionsAdapter: ArrayObjectAdapter? = null
    private val heldSeek = PlaybackHeldSeekController()
    private var heldSeekStepMs: Long? = null
    private var nativeProgressSeekInProgress = false

    private val previousAction = SkipPreviousAction(context)
    private val rewindAction = RewindAction(context)
    private val fastForwardAction = FastForwardAction(context)
    private val nextAction = SkipNextAction(context)
    private val episodeListAction = Action(
        ACTION_EPISODE_LIST,
        context.getString(R.string.player_episode_list),
        null,
        ContextCompat.getDrawable(context, R.drawable.play_list)
    )
    private val fast4kAction = Action(
        ACTION_FAST_4K,
        context.getString(Tv4kMode.OFF.labelRes),
        null,
        ContextCompat.getDrawable(context, R.drawable.ic_player_4k_off)
    )
    private val speedAction = Action(
        13L, "播放速度 · 1×", null,
        ContextCompat.getDrawable(context, R.drawable.ic_player_speed)
    )
    private val sourceFallbackAction = Action(
        ACTION_SOURCE_FALLBACK,
        context.getString(R.string.player_switch_source),
        null,
        ContextCompat.getDrawable(context, R.drawable.play_list)
    )
    private val segmentEditorAction = Action(
        ACTION_SEGMENT_EDITOR, "片頭片尾", null,
        ContextCompat.getDrawable(context, R.drawable.ic_player_segments)
    )

    override fun onCreatePrimaryActions(primaryActionsAdapter: ArrayObjectAdapter) {
        super.onCreatePrimaryActions(primaryActionsAdapter)
        primaryActionsAdapter.add(0, previousAction)
        primaryActionsAdapter.add(1, rewindAction)
        primaryActionsAdapter.add(fastForwardAction)
        primaryActionsAdapter.add(nextAction)
    }

    override fun onCreateSecondaryActions(secondaryActionsAdapter: ArrayObjectAdapter) {
        super.onCreateSecondaryActions(secondaryActionsAdapter)
        this.secondaryActionsAdapter = secondaryActionsAdapter
        secondaryActionsAdapter.add(episodeListAction)
        secondaryActionsAdapter.add(speedAction)
        secondaryActionsAdapter.add(fast4kAction)
    }

    private var transportPresenter: PlaybackTransportRowPresenter? = null

    override fun onCreateRowPresenter(): PlaybackRowPresenter = super.onCreateRowPresenter().also {
        transportPresenter = it as? PlaybackTransportRowPresenter
        updateSeekIncrement()
    }

    private fun updateSeekIncrement() {
        val duration = playerAdapter.duration
        transportPresenter?.defaultSeekIncrement = PlaybackSeekPolicy.fraction(
            duration, heldSeekStepMs ?: PlaybackSeekPolicy.incrementMs(duration))
    }

    /** Returns true only for throttled repeats; accepted events go to Leanback's SeekBar once. */
    fun prepareProgressSeekKeyEvent(event: KeyEvent, progressFocused: Boolean): Boolean {
        if (!progressFocused || !isSeekEnabled || seekProvider != null || playerAdapter.duration <= 0L) {
            resetProgressSeek()
            return false
        }
        val direction = when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> -1
            KeyEvent.KEYCODE_DPAD_RIGHT -> 1
            else -> null
        }
        if (direction == null || event.action != KeyEvent.ACTION_DOWN) {
            resetSeekAcceleration()
            if (event.action == KeyEvent.ACTION_UP && event.keyCode in listOf(
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER)) {
                nativeProgressSeekInProgress = false
            }
            return false
        }
        val step = heldSeek.onKeyDown(direction, event.downTime, event.eventTime,
            event.repeatCount, playerAdapter.duration) ?: return true
        heldSeekStepMs = step
        nativeProgressSeekInProgress = true
        updateSeekIncrement()
        return false
    }

    private fun resetSeekAcceleration() {
        heldSeek.reset()
        heldSeekStepMs = null
        updateSeekIncrement()
    }

    fun resetProgressSeek() {
        nativeProgressSeekInProgress = false
        resetSeekAcceleration()
    }

    override fun onUpdateProgress() {
        updateSeekIncrement()
        super.onUpdateProgress()
        updateProgress()
    }

    override fun onActionClicked(action: Action) {
        when (action) {
            previousAction -> playPreviousEpisode()
            rewindAction -> seekBy(-PlaybackSeekPolicy.incrementMs(playerAdapter.duration))
            fastForwardAction -> seekBy(PlaybackSeekPolicy.incrementMs(playerAdapter.duration))
            nextAction -> playNextEpisode()
            episodeListAction -> chooseEpisode()
            sourceFallbackAction -> switchSourceFallback()
            segmentEditorAction -> openSegmentEditor()
            fast4kAction -> open4kModePicker()
            speedAction -> openSpeedPicker()
            is PlayPauseAction -> if (!onPlayPauseAction(action)) {
                super.onActionClicked(action)
            }
            else -> super.onActionClicked(action)
        }
    }

    override fun onKey(view: View?, keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            resetSeekAcceleration()
            if (nativeProgressSeekInProgress) {
                if (event.action == KeyEvent.ACTION_UP) nativeProgressSeekInProgress = false
                return false
            }
            if (event.action == KeyEvent.ACTION_DOWN) return true
            if (host.isControlsOverlayVisible) {
                host.hideControlsOverlay(true)
            } else {
                activity.finish()
            }
            return true
        }

        if (keyCode == KeyEvent.KEYCODE_MENU) {
            resetProgressSeek()
            if (event.action == KeyEvent.ACTION_DOWN) return true
            chooseEpisode()
            return true
        }

        if (!host.isControlsOverlayVisible) {
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    if (event.action == KeyEvent.ACTION_DOWN) host.showControlsOverlay(true)
                    return super.onKey(view, keyCode, event)
                }
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    if (event.action == KeyEvent.ACTION_DOWN) host.showControlsOverlay(true)
                    return super.onKey(view, keyCode, event)
                }
                KeyEvent.KEYCODE_DPAD_CENTER,
                KeyEvent.KEYCODE_ENTER,
                KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                    if (event.action == KeyEvent.ACTION_UP) {
                        if (playerAdapter.isPlaying) playerAdapter.pause() else playerAdapter.play()
                    }
                    return true
                }
                KeyEvent.KEYCODE_DPAD_UP,
                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    if (event.action == KeyEvent.ACTION_UP) host.showControlsOverlay(true)
                    return true
                }
            }
        }
        return super.onKey(view, keyCode, event)
    }

    private fun seekBy(deltaMs: Long) {
        val target = (playerAdapter.currentPosition + deltaMs).coerceAtLeast(0L)
        val duration = playerAdapter.duration
        playerAdapter.seekTo(if (duration > 0L) target.coerceAtMost(duration) else target)
    }

    fun updateSpeedAction(speed: Float) {
        speedAction.label1 = "播放速度 · ${speed.toString().removeSuffix(".0")}×"
        secondaryActionsAdapter?.let { adapter ->
            val index = adapter.indexOf(speedAction)
            if (index >= 0) adapter.notifyArrayItemRangeChanged(index, 1)
        }
    }

    fun update4kAction(mode: Tv4kMode) {
        fast4kAction.label1 = appContext.getString(mode.labelRes)
        fast4kAction.icon = ContextCompat.getDrawable(
            appContext,
            if (mode.isEnabled) R.drawable.ic_player_4k_on else R.drawable.ic_player_4k_off
        )
        secondaryActionsAdapter?.let { adapter ->
            val index = adapter.indexOf(fast4kAction)
            if (index >= 0) adapter.notifyArrayItemRangeChanged(index, 1)
        }
    }

    fun showSourceFallbackAction(visible: Boolean) {
        val adapter = secondaryActionsAdapter ?: return
        val index = adapter.indexOf(sourceFallbackAction)
        if (visible && index < 0) adapter.add(sourceFallbackAction)
        if (!visible && index >= 0) adapter.remove(sourceFallbackAction)
    }

    fun showSegmentEditorAction(visible: Boolean) {
        val adapter = secondaryActionsAdapter ?: return
        val index = adapter.indexOf(segmentEditorAction)
        if (visible && index < 0) adapter.add(segmentEditorAction)
        if (!visible && index >= 0) adapter.remove(segmentEditorAction)
    }

    companion object {
        private const val ACTION_EPISODE_LIST = 10L
        private const val ACTION_FAST_4K = 11L
        private const val ACTION_SOURCE_FALLBACK = 12L
        private const val ACTION_SEGMENT_EDITOR = 14L
    }
}
