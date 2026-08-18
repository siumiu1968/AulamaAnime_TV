package com.jing.sakura.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TvUpdateChannelTest {
    @Test
    fun defaultsUnknownStoredChannelToStable() {
        assertEquals(TvUpdateChannel.Stable, TvUpdateChannel.fromStorageValue(null))
        assertEquals(TvUpdateChannel.Stable, TvUpdateChannel.fromStorageValue("unknown"))
    }

    @Test
    fun togglesBetweenStableAndPreviewWithOneAction() {
        assertEquals(TvUpdateChannel.Preview, TvUpdateChannel.Stable.toggled())
        assertEquals(TvUpdateChannel.Stable, TvUpdateChannel.Preview.toggled())
    }

    @Test
    fun keepsStableAndPreviewManifestsSeparate() {
        val stable = tvUpdateRoute(TvUpdateChannel.Stable)
        val preview = tvUpdateRoute(TvUpdateChannel.Preview)

        assertTrue(stable.primary.url.endsWith("/tv-update.json"))
        assertTrue(preview.primary.url.endsWith("/tv-update-beta.json"))
        assertEquals(TvUpdateSourceFormat.AULAMA_MANIFEST, preview.primary.format)
    }
}
