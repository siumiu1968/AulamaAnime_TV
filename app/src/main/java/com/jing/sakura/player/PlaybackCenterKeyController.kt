package com.jing.sakura.player

internal enum class CenterKeyAction {
    NONE,
    SHORT_PRESS,
    START_BOOST,
    STOP_BOOST,
    LOCK_BOOST
}

internal enum class CenterKeyRoute {
    GLOBAL_PLAYBACK,
    FOCUSED_CONTROL
}

internal object PlaybackCenterKeyRoutingPolicy {
    fun route(controlsOverlayVisible: Boolean): CenterKeyRoute =
        if (controlsOverlayVisible) {
            CenterKeyRoute.FOCUSED_CONTROL
        } else {
            CenterKeyRoute.GLOBAL_PLAYBACK
        }
}

/** Remote input state, including brief key-up gaps from repeating remotes. */
internal class PlaybackCenterKeyController(
    private val longPressThresholdMs: Long = DEFAULT_LONG_PRESS_THRESHOLD_MS
) {
    private var downAtMs: Long? = null
    private var releasedAtMs: Long? = null
    private var boosting = false
    var isLocked = false
        private set
    private var unlockOnRelease = false
    val isActive: Boolean get() = downAtMs != null || boosting

    fun charge(nowMs: Long): Float = if (isLocked) 1f else
        downAtMs?.let { ((nowMs - it).toFloat() / LOCK_THRESHOLD_MS).coerceIn(0f, 1f) } ?: 0f

    fun onKeyDown(eventTimeMs: Long, repeatCount: Int): CenterKeyAction {
        releasedAtMs = null
        if (isLocked) {
            if (downAtMs == null && repeatCount == 0) unlockOnRelease = true
            downAtMs = downAtMs ?: eventTimeMs
            return CenterKeyAction.NONE
        }
        if (downAtMs == null) downAtMs = eventTimeMs
        return onLongPressTimeout(eventTimeMs)
    }

    fun onLongPressTimeout(nowMs: Long): CenterKeyAction {
        val startedAt = downAtMs ?: return CenterKeyAction.NONE
        if (releasedAtMs != null || isLocked) return CenterKeyAction.NONE
        if (!boosting && nowMs - startedAt >= longPressThresholdMs) {
            boosting = true
            return CenterKeyAction.START_BOOST
        }
        if (boosting && nowMs - startedAt >= LOCK_THRESHOLD_MS) {
            isLocked = true
            return CenterKeyAction.LOCK_BOOST
        }
        return CenterKeyAction.NONE
    }

    fun onKeyUp(eventTimeMs: Long): CenterKeyAction {
        if (downAtMs == null) return CenterKeyAction.NONE
        if (isLocked) {
            downAtMs = null
            return if (unlockOnRelease) cancel() else CenterKeyAction.NONE
        }
        if (boosting) {
            releasedAtMs = eventTimeMs
            return CenterKeyAction.NONE
        }
        downAtMs = null
        return CenterKeyAction.SHORT_PRESS
    }

    fun onReleaseTimeout(nowMs: Long): CenterKeyAction {
        val released = releasedAtMs ?: return CenterKeyAction.NONE
        return if (nowMs - released >= RELEASE_GRACE_MS) cancel() else CenterKeyAction.NONE
    }

    fun cancel(): CenterKeyAction {
        downAtMs = null
        releasedAtMs = null
        isLocked = false
        unlockOnRelease = false
        val action = if (boosting) CenterKeyAction.STOP_BOOST else CenterKeyAction.NONE
        boosting = false
        return action
    }

    companion object {
        const val DEFAULT_LONG_PRESS_THRESHOLD_MS = 500L
        const val LOCK_THRESHOLD_MS = 3_000L
        const val RELEASE_GRACE_MS = 220L
    }
}
