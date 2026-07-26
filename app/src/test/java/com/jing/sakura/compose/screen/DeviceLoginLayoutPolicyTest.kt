package com.jing.sakura.compose.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceLoginLayoutPolicyTest {
    @Test
    fun enlargesQrCodeOnTheLegacy1080pTvDensity() {
        val qrSizeDp = DeviceLoginLayoutPolicy.qrSizeDp(
            availableWidthDp = 1_280f,
            availableHeightDp = 720f
        )

        assertEquals(223, qrSizeDp)
        assertTrue(qrSizeDp > 148)
    }

    @Test
    fun capsQrCodeOnLargeLayouts() {
        assertEquals(
            228,
            DeviceLoginLayoutPolicy.qrSizeDp(
                availableWidthDp = 1_920f,
                availableHeightDp = 1_080f
            )
        )
    }

    @Test
    fun keepsQrCodeUsableOnCompactLayouts() {
        assertEquals(
            167,
            DeviceLoginLayoutPolicy.qrSizeDp(
                availableWidthDp = 960f,
                availableHeightDp = 540f
            )
        )
        assertEquals(
            160,
            DeviceLoginLayoutPolicy.qrSizeDp(
                availableWidthDp = 640f,
                availableHeightDp = 360f
            )
        )
    }
}
