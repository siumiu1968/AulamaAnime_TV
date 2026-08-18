package com.jing.sakura.compose.screen

import org.junit.Assert.assertEquals
import org.junit.Test

class DetailTitleLayoutPolicyTest {
    @Test
    fun `short title keeps large two-line treatment`() {
        assertEquals(
            DetailTitleLayout(37, 43, 2, 3),
            DetailTitleLayoutPolicy.forTitle("鬼的新娘")
        )
    }

    @Test
    fun `long title shrinks and gains lines instead of truncating`() {
        val title = "才女的侍從在滿是高嶺之花的貴族學校暗中照顧毫無生活自理能力的大小姐"

        assertEquals(
            DetailTitleLayout(29, 34, 3, 2),
            DetailTitleLayoutPolicy.forTitle(title)
        )
    }

    @Test
    fun `very extreme title can use four lines`() {
        val title = "這是一個比一般日本輕小說作品名稱更長而且仍然需要在電視詳情頁完整顯示的極端動漫標題所以介面必須自動縮小字體並增加可用行數即使標題再長一倍都不應該使用省略號截斷作品名稱"

        assertEquals(
            DetailTitleLayout(22, 27, 4, 1),
            DetailTitleLayoutPolicy.forTitle(title)
        )
    }
}
