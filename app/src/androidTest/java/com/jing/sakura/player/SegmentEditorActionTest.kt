package com.jing.sakura.player

import android.app.Activity
import android.view.ContextThemeWrapper
import androidx.leanback.media.PlaybackGlueHost
import androidx.leanback.media.PlayerAdapter
import androidx.leanback.widget.Action
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SegmentEditorActionTest {
    private class TestPlayerAdapter : PlayerAdapter() {
        override fun play() = Unit
        override fun pause() = Unit
        override fun isPrepared() = true
    }

    private fun withGlue(block: (ProgressTransportControlGlue<TestPlayerAdapter>, () -> Int) -> Unit) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val context = ContextThemeWrapper(instrumentation.targetContext, androidx.leanback.R.style.Theme_Leanback)
            var opened = 0
            val glue = ProgressTransportControlGlue(context, Activity(), TestPlayerAdapter(),
                updateProgress = {}, chooseEpisode = {}, switchSourceFallback = {},
                playPreviousEpisode = {}, playNextEpisode = {}, open4kModePicker = {}, openSpeedPicker = {},
                openSegmentEditor = { opened++ })
            try {
                block(glue) { opened }
            } finally {
                glue.host = null
            }
        }
    }

    private fun secondaryActions(glue: ProgressTransportControlGlue<TestPlayerAdapter>): List<Action> {
        val adapter = glue.controlsRow.secondaryActionsAdapter as ArrayObjectAdapter
        return (0 until adapter.size()).map { adapter[it] as Action }
    }

    @Test fun attachedControlsShowOneEditorActionDispatchClickAndRemoveItOnSignOut() = withGlue { glue, opened ->
        glue.host = object : PlaybackGlueHost() {}
        val defaultIds = secondaryActions(glue).map { it.id }
        assertEquals(listOf(10L, 13L, 11L), defaultIds)

        repeat(3) { glue.showSegmentEditorAction(true) }
        val actions = secondaryActions(glue)
        assertEquals(defaultIds + 14L, actions.map { it.id })
        val editorAction = actions.single { it.id == 14L }
        assertEquals("片頭片尾", editorAction.label1.toString())
        glue.onActionClicked(editorAction)
        assertEquals(1, opened())

        repeat(3) { glue.showSegmentEditorAction(false) }
        assertEquals(defaultIds, secondaryActions(glue).map { it.id })
        repeat(3) { glue.showSegmentEditorAction(true) }
        assertEquals(defaultIds + 14L, secondaryActions(glue).map { it.id })
    }

    @Test fun latestVisibilityBeforeHostAttachmentIsAppliedToTheCreatedControls() {
        for (visible in listOf(true, false)) {
            withGlue { glue, _ ->
                glue.showSegmentEditorAction(true)
                repeat(3) { glue.showSegmentEditorAction(visible) }
                glue.host = object : PlaybackGlueHost() {}
                val expectedIds = listOf(10L, 13L, 11L) + if (visible) listOf(14L) else emptyList()
                assertEquals(expectedIds, secondaryActions(glue).map { it.id })
            }
        }
    }
}
