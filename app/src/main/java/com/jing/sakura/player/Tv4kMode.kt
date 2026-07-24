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

internal enum class Tv4kMode(
    @StringRes val labelRes: Int,
    val targetWidth: Int,
    val targetHeight: Int,
    val strategy: Tv4kEffectStrategy,
    val droppedFrameLimit: Int
) {
    OFF(
        labelRes = R.string.player_4k_mode_off,
        targetWidth = 0,
        targetHeight = 0,
        strategy = Tv4kEffectStrategy.NONE,
        droppedFrameLimit = Int.MAX_VALUE
    ),
    RESOURCE_SAVER(
        labelRes = R.string.player_4k_mode_resource_saver,
        targetWidth = 2560,
        targetHeight = 1440,
        strategy = Tv4kEffectStrategy.MATRIX,
        droppedFrameLimit = 36
    ),
    FAST(
        labelRes = R.string.player_4k_mode_fast,
        targetWidth = 3840,
        targetHeight = 2160,
        strategy = Tv4kEffectStrategy.MATRIX,
        droppedFrameLimit = 24
    ),
    BALANCED(
        labelRes = R.string.player_4k_mode_balanced,
        targetWidth = 3840,
        targetHeight = 2160,
        strategy = Tv4kEffectStrategy.LANCZOS,
        droppedFrameLimit = 12
    );

    val isEnabled: Boolean
        get() = this != OFF

    val effectPlan: Tv4kEffectPlan
        get() = Tv4kEffectPlan(strategy, targetWidth, targetHeight)

    fun fallback(): Tv4kMode = when (this) {
        BALANCED -> FAST
        FAST -> RESOURCE_SAVER
        RESOURCE_SAVER, OFF -> OFF
    }
}

internal object Tv4kRuntimePolicy {
    fun effectiveMode(
        requested: Tv4kMode,
        supports4kOutput: Boolean,
        isLowRamDevice: Boolean
    ): Tv4kMode = when {
        requested == Tv4kMode.OFF -> Tv4kMode.OFF
        !supports4kOutput -> Tv4kMode.OFF
        isLowRamDevice && requested == Tv4kMode.BALANCED -> Tv4kMode.FAST
        else -> requested
    }
}
