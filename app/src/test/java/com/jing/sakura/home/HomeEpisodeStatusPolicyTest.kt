package com.jing.sakura.home
import com.jing.sakura.data.AnimeData
import org.junit.Assert.assertEquals
import org.junit.Test
class HomeEpisodeStatusPolicyTest {
    private fun anime(id: String, status: String, source: String = "cycani") = AnimeData(id=id,url="",title=id,currentEpisode=status,imageUrl="",sourceId=source)
    @Test fun scheduleCorrectsStaleHomeStatusWithoutReorderingCards() {
        val result = withCurrentScheduleStatus(listOf(anime("a","第6集"),anime("b","已完結")),listOf(anime("a","11 · 週日24:00後")))
        assertEquals(listOf("a","b"),result.map { it.id })
        assertEquals("11 · 週日24:00後",result[0].currentEpisode)
        assertEquals("已完結",result[1].currentEpisode)
    }
    @Test fun blankStatusesAndDifferentProvidersDoNotOverwrite() {
        val items=listOf(anime("a","第6集"),anime("b","第4集"))
        assertEquals(items,withCurrentScheduleStatus(items,listOf(anime("a",""),anime("b","第11集","other"))))
    }
}
