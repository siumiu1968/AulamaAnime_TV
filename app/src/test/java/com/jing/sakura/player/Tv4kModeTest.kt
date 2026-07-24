package com.jing.sakura.player

import org.junit.Assert.assertEquals
import org.junit.Test

class Tv4kModeTest {
    @Test
    fun qualityModesDegradeOneStepAtATime() {
        assertEquals(Tv4kMode.FAST, Tv4kMode.BALANCED.fallback())
        assertEquals(Tv4kMode.RESOURCE_SAVER, Tv4kMode.FAST.fallback())
        assertEquals(Tv4kMode.OFF, Tv4kMode.RESOURCE_SAVER.fallback())
        assertEquals(Tv4kMode.OFF, Tv4kMode.OFF.fallback())
    }

    @Test
    fun offModeCompletelyBypassesVideoEffects() {
        val plan = Tv4kMode.OFF.effectPlan

        assertEquals(true, plan.bypass)
        assertEquals(Tv4kEffectStrategy.NONE, plan.strategy)
        assertEquals(0, plan.targetWidth)
        assertEquals(0, plan.targetHeight)
    }

    @Test
    fun enabledModesUseDistinctEffectPlans() {
        assertEquals(Tv4kEffectStrategy.MATRIX, Tv4kMode.RESOURCE_SAVER.effectPlan.strategy)
        assertEquals(2560, Tv4kMode.RESOURCE_SAVER.effectPlan.targetWidth)
        assertEquals(Tv4kEffectStrategy.MATRIX, Tv4kMode.FAST.effectPlan.strategy)
        assertEquals(3840, Tv4kMode.FAST.effectPlan.targetWidth)
        assertEquals(Tv4kEffectStrategy.LANCZOS, Tv4kMode.BALANCED.effectPlan.strategy)
    }

    @Test
    fun unsupportedOutputFallsBackToTrueBypass() {
        assertEquals(
            Tv4kMode.OFF,
            Tv4kRuntimePolicy.effectiveMode(
                requested = Tv4kMode.BALANCED,
                supports4kOutput = false,
                isLowRamDevice = false
            )
        )
    }

    @Test
    fun lowRamGoogleTvAvoidsLanczosButKeepsFastMode() {
        assertEquals(
            Tv4kMode.FAST,
            Tv4kRuntimePolicy.effectiveMode(
                requested = Tv4kMode.BALANCED,
                supports4kOutput = true,
                isLowRamDevice = true
            )
        )
    }
}
