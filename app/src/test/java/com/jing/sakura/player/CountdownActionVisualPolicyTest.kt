package com.jing.sakura.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class CountdownActionVisualPolicyTest {
    @Test
    fun focusedCountdownStartsWithDarkSurfaceAndVisibleWhiteLabel() {
        val style = CountdownActionVisualPolicy.resolve(
            isFocused = true,
            isPressed = false,
            countdownActive = true
        )

        assertEquals(0xD9171C25.toInt(), style.surfaceColor)
        assertEquals(0xFFFFFFFF.toInt(), style.labelColor)
        assertNotNull(style.labelShadowColor)
    }

    @Test
    fun focusedIdleButtonKeepsExistingLightSurfaceTreatment() {
        val style = CountdownActionVisualPolicy.resolve(
            isFocused = true,
            isPressed = false,
            countdownActive = false
        )

        assertEquals(0xFFFFFFFF.toInt(), style.surfaceColor)
        assertEquals(0xFF08090B.toInt(), style.labelColor)
        assertNull(style.labelShadowColor)
    }
}
