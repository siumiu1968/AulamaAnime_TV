package com.jing.sakura.player

import androidx.media3.common.C
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.LoadControl
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.analytics.PlayerId
import androidx.media3.exoplayer.source.MediaSource.MediaPeriodId
import androidx.media3.exoplayer.source.TrackGroupArray
import androidx.media3.exoplayer.trackselection.ExoTrackSelection
import androidx.media3.exoplayer.upstream.Allocator
import androidx.media3.exoplayer.upstream.DefaultAllocator

/** Both profiles share allocations; promotion changes loading decisions, never the player/buffer. */
@UnstableApi
internal class PreviewHandoffLoadControl private constructor(
    private val normal: LoadControl,
    private val preview: LoadControl
) : LoadControl {
    @Volatile private var promoted = false
    private val active: LoadControl get() = if (promoted) normal else preview

    fun promote() { promoted = true }

    // Java interface defaults are not forwarded by Kotlin delegation. Implement every current
    // ExoPlayer entry point explicitly; the legacy defaults deliberately throw at runtime.
    override fun getAllocator(): Allocator = normal.allocator
    override fun getBackBufferDurationUs(playerId: PlayerId): Long =
        normal.getBackBufferDurationUs(playerId)
    override fun retainBackBufferFromKeyframe(playerId: PlayerId): Boolean =
        normal.retainBackBufferFromKeyframe(playerId)

    override fun onPrepared(playerId: PlayerId) {
        normal.onPrepared(playerId)
        preview.onPrepared(playerId)
    }

    override fun onTracksSelected(playerId: PlayerId, timeline: Timeline, mediaPeriodId: MediaPeriodId,
        renderers: Array<out Renderer>, trackGroups: TrackGroupArray,
        trackSelections: Array<out ExoTrackSelection>) {
        normal.onTracksSelected(playerId, timeline, mediaPeriodId, renderers, trackGroups, trackSelections)
        preview.onTracksSelected(playerId, timeline, mediaPeriodId, renderers, trackGroups, trackSelections)
    }

    override fun onStopped(playerId: PlayerId) {
        normal.onStopped(playerId)
        preview.onStopped(playerId)
    }

    override fun onReleased(playerId: PlayerId) {
        normal.onReleased(playerId)
        preview.onReleased(playerId)
    }

    override fun shouldContinueLoading(parameters: LoadControl.Parameters): Boolean =
        active.shouldContinueLoading(parameters)

    override fun shouldStartPlayback(parameters: LoadControl.Parameters): Boolean =
        active.shouldStartPlayback(parameters)

    companion object {
        fun create(): PreviewHandoffLoadControl {
            val allocator = DefaultAllocator(true, C.DEFAULT_BUFFER_SEGMENT_SIZE)
            return PreviewHandoffLoadControl(
                normal = DefaultLoadControl.Builder().setAllocator(allocator).build(),
                preview = DefaultLoadControl.Builder().setAllocator(allocator)
                    .setBufferDurationsMs(1_500, 5_000, 400, 800).setBackBuffer(0, false).build()
            )
        }
    }
}
