package com.jing.sakura.player

internal object PlaybackControlsAutoHidePolicy {
    const val IDLE_TIMEOUT_MS = 4_000L

    fun shouldHide(controlsVisible: Boolean, isPlaying: Boolean): Boolean =
        controlsVisible && isPlaying
}
