package com.jing.sakura.repo

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CycaniContentPolicyTest {
    @Test
    fun `suppresses unavailable arknights test title in both scripts`() {
        assertTrue(isSuppressedAnimeTitle("明日方舟：焰燼曙明"))
        assertTrue(isSuppressedAnimeTitle("明日方舟: 焰烬曙明"))
    }

    @Test
    fun `keeps unrelated arknights titles`() {
        assertFalse(isSuppressedAnimeTitle("明日方舟：黎明前奏"))
        assertFalse(isSuppressedAnimeTitle("小林家的龍女僕"))
    }

    @Test
    fun `suppresses unavailable leviathan chronicle in both scripts only`() {
        assertTrue(isSuppressedAnimeTitle("利維坦號戰記"))
        assertTrue(isSuppressedAnimeTitle("利维坦号战记"))
        assertFalse(isSuppressedAnimeTitle("利維坦"))
    }
}
