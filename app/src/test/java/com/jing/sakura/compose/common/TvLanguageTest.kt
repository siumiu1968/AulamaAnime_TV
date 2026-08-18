package com.jing.sakura.compose.common

import org.junit.Assert.assertEquals
import org.junit.Test

class TvLanguageTest {
    @Test
    fun derivesTheInitialLanguageFromTheSystemChineseLocale() {
        assertEquals(TvLanguage.Simplified, TvLanguage.fromSystemLanguageTag("zh-CN"))
        assertEquals(TvLanguage.Simplified, TvLanguage.fromSystemLanguageTag("zh-Hans-SG"))
        assertEquals(TvLanguage.Traditional, TvLanguage.fromSystemLanguageTag("zh-TW"))
        assertEquals(TvLanguage.Traditional, TvLanguage.fromSystemLanguageTag("zh-Hant-HK"))
        assertEquals(TvLanguage.Traditional, TvLanguage.fromSystemLanguageTag("en-US"))
    }
}
