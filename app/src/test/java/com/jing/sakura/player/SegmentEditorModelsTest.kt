package com.jing.sakura.player

import okio.ByteString.Companion.encodeUtf8
import org.junit.Assert.*
import org.junit.Test

class SegmentEditorModelsTest {
    @Test fun highestAdministratorCanOpenEditorBeforeOptionalMetadataLoads() {
        assertTrue(canOpenPlaybackSegmentEditor("super_admin"))
        assertTrue(canOpenPlaybackSegmentEditor(" SUPER_ADMIN "))
        listOf(null, "", "admin", "member", "free").forEach {
            assertFalse(canOpenPlaybackSegmentEditor(it))
        }
        // Opening the entry never grants permission to edit data returned by the server.
        assertFalse(canEditPlaybackSegments("super_admin", false))
    }

    @Test fun bothHighestRoleAndServerPermissionAreRequired() {
        assertTrue(canEditPlaybackSegments("super_admin", true))
        listOf(null, "admin", "member", "free").forEach { assertFalse(canEditPlaybackSegments(it, true)) }
        assertFalse(canEditPlaybackSegments("super_admin", false))
    }

    @Test fun rawAllTimingMustNotBeSavedWithCurrentLineOffsetTwice() {
        val snapshot = SegmentEditorSnapshot.parse("""{"item":{"introStart":15,"introEnd":105},
            "rawSegments":{"all":{"introStart":10,"introEnd":100},"provider":{"introStart":12,"introEnd":102}},
            "permissions":{"canEdit":true}}""")
        assertEquals(10_000L, snapshot.all[SegmentMarker.INTRO_START])
        assertEquals(12_000L, snapshot.current[SegmentMarker.INTRO_START])
        assertTrue(snapshot.canEdit)
        val empty = SegmentEditorSnapshot.parse("""{"item":null,"rawSegments":{"all":{},"provider":null},"permissions":{"canEdit":true}}""")
        assertTrue(empty.canEdit)
        assertNull(empty.all.error(1_000_000))
    }

    @Test fun legacyAllScopeUsesBaseTimingsRatherThanProviderOffset() {
        val snapshot = SegmentEditorSnapshot.parse("""{"item":{"introStart":15,"introEnd":105,
            "baseSegments":{"introStart":10,"introEnd":100}},
            "effectiveSegments":{"introStart":15,"introEnd":105},"permissions":{"canEdit":true}}""")
        assertEquals(10_000L, snapshot.all[SegmentMarker.INTRO_START])
        assertEquals(15_000L, snapshot.current[SegmentMarker.INTRO_START])
        val tenths = SegmentEditorSnapshot.parse("""{"item":{"introStart":32.3},"permissions":{"canEdit":true}}""")
        assertEquals("00:32.3", segmentTimeLabel(tenths.all[SegmentMarker.INTRO_START]))
    }

    @Test fun providerFallbackUsesEffectiveTimingsAndRecognizesEveryTvLine() {
        val snapshot = SegmentEditorSnapshot.parse("""{"rawSegments":{"all":{"introStart":1}},
            "effectiveSegments":{"introStart":3.1},"permissions":{"canEdit":false}}""")
        assertEquals(3_100L, snapshot.current[SegmentMarker.INTRO_START])
        for (provider in listOf("girigiri_cht", "girigiri_chs", "sakura", "age")) {
            assertEquals(provider, playbackSegmentProvider("cycapi:" + """{"xp":"$provider"}""".encodeUtf8().base64Url()))
        }
        assertEquals("cycani", playbackSegmentProvider("cycapi:broken"))
        assertEquals("cycani", playbackSegmentProvider("52000"))
        assertEquals("cycani", playbackSegmentProvider("cycapi:" + """{"xp":"untrusted"}""".encodeUtf8().base64()))
    }

    @Test fun adjustmentsClampAndKeepTenthsAndClearingIsReversible() {
        var draft = SegmentDraft().set(SegmentMarker.INTRO_START, 1234)
        assertEquals(1200L, draft[SegmentMarker.INTRO_START])
        draft = draft.adjusted(SegmentMarker.INTRO_START, -5000, 10000)
        assertEquals(0L, draft[SegmentMarker.INTRO_START])
        draft = draft.adjusted(SegmentMarker.INTRO_END, 50000, 10000)
        assertEquals(10000L, draft[SegmentMarker.INTRO_END])
        assertNull(draft.error(10000))
        draft = draft.clear("intro").clear("outro")
        assertEquals(setOf("intro", "outro"), draft.cleared)
        assertNull(draft[SegmentMarker.INTRO_END])
        assertEquals(setOf("outro"), draft.set(SegmentMarker.INTRO_START, 100).cleared)
        assertEquals("01:02.3", segmentTimeLabel(62300))
    }

    @Test fun incompleteOverlappingReversedAndOutOfVideoRangesCannotSave() {
        val start = SegmentDraft().set(SegmentMarker.INTRO_START, 1000)
        assertNotNull(start.error(10000))
        assertNotNull(start.set(SegmentMarker.INTRO_END, 1000).error(10000))
        val intro = start.set(SegmentMarker.INTRO_END, 5000)
        assertNull(intro.error(10000))
        val overlap = intro.set(SegmentMarker.OUTRO_START, 4000).set(SegmentMarker.OUTRO_END, 9000)
        assertNotNull(overlap.error(10000))
        assertNotNull(intro.set(SegmentMarker.INTRO_END, 11000).error(10000))
    }
}
