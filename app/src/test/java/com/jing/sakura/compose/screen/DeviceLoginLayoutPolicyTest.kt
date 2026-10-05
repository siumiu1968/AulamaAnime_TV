package com.jing.sakura.compose.screen

import com.jing.sakura.compose.common.TvLanguage
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

    @Test
    fun welcomeCopyUsesNativeSimplifiedAndTraditionalPhrasing() {
        val simplified = welcomeCopy(TvLanguage.Simplified)
        val traditional = welcomeCopy(TvLanguage.Traditional)

        assertEquals("让每一段精彩\n都在大屏幕绽放", simplified.slogan)
        assertEquals("使用 Aulama ID 登录", simplified.loginButton)
        assertEquals("免登录使用", simplified.guestButton)
        assertTrue(simplified.guestDescription.contains("这台电视"))
        assertEquals("讓每一段精彩\n都在大螢幕綻放", traditional.slogan)
        assertTrue(traditional.loginDescription.contains("跨裝置同步"))
        assertTrue(traditional.guestDescription.contains("這部電視"))
    }

    @Test
    fun welcomeChoiceCopyFitsTheFixedWidthCards() {
        // Choice cards are 244dp wide with 16dp padding; keep copy short enough to avoid clipping.
        for (language in TvLanguage.values()) {
            val copy = welcomeCopy(language)
            assertTrue(copy.loginDescription.length <= 28)
            assertTrue(copy.guestDescription.length <= 28)
            assertTrue(copy.loginHint.length <= 12)
            assertTrue(copy.guestHint.length <= 12)
        }
    }

    @Test
    fun welcomeTitleShrinksInsteadOfEllipsizingExtremeTitles() {
        val short = welcomeTitleLayout("攻殼機動隊")
        val extreme = welcomeTitleLayout(
            "才女的侍從 在滿是高嶺之花的貴族學校暗中照顧（毫無生活自理能力的）學院第一大小姐"
        )

        assertEquals(24, short.fontSizeSp)
        assertEquals(2, short.maxLines)
        assertTrue(extreme.fontSizeSp < short.fontSizeSp)
        assertTrue(extreme.maxLines >= 3)
    }
}
