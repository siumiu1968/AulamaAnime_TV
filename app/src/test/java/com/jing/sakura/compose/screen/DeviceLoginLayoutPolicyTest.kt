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

        assertEquals("下一集，在大屏幕继续。", simplified.slogan)
        assertEquals("使用 Aulama ID 登录", simplified.loginButton)
        assertEquals("下一集，喺大螢幕繼續。", traditional.slogan)
        assertTrue(traditional.message.contains("帶返嚟"))
    }

    @Test
    fun welcomeTitleShrinksInsteadOfEllipsizingExtremeTitles() {
        val short = welcomeTitleLayout("攻殼機動隊", compact = true)
        val extreme = welcomeTitleLayout(
            "才女的侍從 在滿是高嶺之花的貴族學校暗中照顧（毫無生活自理能力的）學院第一大小姐",
            compact = true
        )

        assertEquals(31, short.fontSizeSp)
        assertEquals(2, short.maxLines)
        assertTrue(extreme.fontSizeSp < short.fontSizeSp)
        assertTrue(extreme.maxLines >= 3)
    }
}
