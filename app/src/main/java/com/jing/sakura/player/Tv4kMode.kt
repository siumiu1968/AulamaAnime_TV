package com.jing.sakura.player

import androidx.annotation.StringRes
import com.jing.sakura.R

internal enum class Tv4kEffectStrategy {
    NONE,
    MATRIX,
    LANCZOS
}

internal data class Tv4kEffectPlan(
    val strategy: Tv4kEffectStrategy,
    val targetWidth: Int,
    val targetHeight: Int
) {
    val bypass: Boolean
        get() = strategy == Tv4kEffectStrategy.NONE
}

internal data class Tv4kTrackConstraint(
    val maxWidth: Int,
    val maxHeight: Int,
    val maxBitrate: Int
)

internal enum class Tv4kMode(
    @StringRes val labelRes: Int,
    val targetWidth: Int,
    val targetHeight: Int,
    val strategy: Tv4kEffectStrategy,
    val droppedFrameLimit: Int,
    val trackConstraint: Tv4kTrackConstraint
) {
    OFF(
        labelRes = R.string.player_4k_mode_off,
        targetWidth = 0,
        targetHeight = 0,
        strategy = Tv4kEffectStrategy.NONE,
        droppedFrameLimit = Int.MAX_VALUE,
        trackConstraint = HD_TRACK_CONSTRAINT
    ),
    RESOURCE_SAVER(
        labelRes = R.string.player_4k_mode_resource_saver,
        targetWidth = 2560,
        targetHeight = 1440,
        strategy = Tv4kEffectStrategy.MATRIX,
        droppedFrameLimit = 36,
        trackConstraint = HD_TRACK_CONSTRAINT
    ),
    FAST(
        labelRes = R.string.player_4k_mode_fast,
        targetWidth = 3840,
        targetHeight = 2160,
        strategy = Tv4kEffectStrategy.MATRIX,
        droppedFrameLimit = 24,
        trackConstraint = FAST_4K_TRACK_CONSTRAINT
    ),
    BALANCED(
        labelRes = R.string.player_4k_mode_balanced,
        targetWidth = 3840,
        targetHeight = 2160,
        strategy = Tv4kEffectStrategy.LANCZOS,
        droppedFrameLimit = 12,
        trackConstraint = BALANCED_4K_TRACK_CONSTRAINT
    ),
    QUALITY(
        labelRes = R.string.player_4k_mode_quality,
        targetWidth = 3840,
        targetHeight = 2160,
        strategy = Tv4kEffectStrategy.LANCZOS,
        droppedFrameLimit = 8,
        trackConstraint = QUALITY_4K_TRACK_CONSTRAINT
    );

    val isEnabled: Boolean
        get() = this != OFF

    val effectPlan: Tv4kEffectPlan
        get() = Tv4kEffectPlan(strategy, targetWidth, targetHeight)

    fun fallback(): Tv4kMode = when (this) {
        QUALITY -> BALANCED
        BALANCED -> FAST
        FAST -> RESOURCE_SAVER
        RESOURCE_SAVER, OFF -> OFF
    }
}

private val HD_TRACK_CONSTRAINT = Tv4kTrackConstraint(
    maxWidth = 1920,
    maxHeight = 1080,
    maxBitrate = 8_500_000
)
private val FAST_4K_TRACK_CONSTRAINT = Tv4kTrackConstraint(
    maxWidth = 3840,
    maxHeight = 2160,
    maxBitrate = 22_000_000
)
private val BALANCED_4K_TRACK_CONSTRAINT = Tv4kTrackConstraint(
    maxWidth = 3840,
    maxHeight = 2160,
    maxBitrate = 30_000_000
)
private val QUALITY_4K_TRACK_CONSTRAINT = Tv4kTrackConstraint(
    maxWidth = 3840,
    maxHeight = 2160,
    maxBitrate = 42_000_000
)

internal object Tv4kRuntimePolicy {
    fun effectiveMode(
        requested: Tv4kMode,
        supports4kOutput: Boolean,
        isLowRamDevice: Boolean
    ): Tv4kMode = when {
        requested == Tv4kMode.OFF -> Tv4kMode.OFF
        !supports4kOutput -> Tv4kMode.OFF
        isLowRamDevice && requested == Tv4kMode.QUALITY -> Tv4kMode.BALANCED
        isLowRamDevice && requested == Tv4kMode.BALANCED -> Tv4kMode.FAST
        else -> requested
    }
}
