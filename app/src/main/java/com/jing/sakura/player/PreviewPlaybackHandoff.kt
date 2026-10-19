package com.jing.sakura.player

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import java.util.UUID

internal data class PreparedPreviewSource(
    val url: String,
    val headers: Map<String, String>,
    val positionMs: Long,
    val durationMs: Long
)

@UnstableApi
internal data class PreviewPlaybackTransfer(
    val player: ExoPlayer,
    val trackSelector: DefaultTrackSelector,
    val source: PreparedPreviewSource,
    val snapshot: Bitmap?
)

/** One short-lived owner between the Compose preview and the Leanback activity. */
@UnstableApi
internal object PreviewPlaybackHandoff {
    private const val TIMEOUT_MS = 10_000L
    private val handler = Handler(Looper.getMainLooper())
    private data class Entry(
        val animeId: String,
        val sourceId: String,
        val episodeId: String,
        val source: PreparedPreviewSource,
        var transfer: PreviewPlaybackTransfer?,
        val promoteBuffer: () -> Unit,
        val detachPreview: () -> Unit
    )
    private val entries = mutableMapOf<String, Entry>()

    fun register(
        arg: NavigateToPlayerArg,
        url: String,
        headers: Map<String, String>,
        player: ExoPlayer,
        trackSelector: DefaultTrackSelector,
        snapshot: Bitmap?,
        promoteBuffer: () -> Unit = {},
        detachPreview: () -> Unit
    ): NavigateToPlayerArg {
        check(Looper.myLooper() == Looper.getMainLooper())
        val token = UUID.randomUUID().toString()
        val source = PreparedPreviewSource(url, headers.toMap(), player.currentPosition.coerceAtLeast(0L),
            player.contentDuration.coerceAtLeast(0L))
        synchronized(entries) {
            entries[token] = Entry(arg.animeId, arg.sourceId, initialEpisodeId(arg), source,
                PreviewPlaybackTransfer(player, trackSelector, source, snapshot), promoteBuffer, detachPreview)
        }
        handler.postDelayed({ expire(token) }, TIMEOUT_MS)
        return arg.copy(resumePositionMs = source.positionMs, previewHandoffId = token)
    }

    /** Source metadata remains briefly after the activity claims the player for ViewModel startup. */
    fun source(arg: NavigateToPlayerArg, episodeId: String): PreparedPreviewSource? =
        synchronized(entries) {
            arg.previewHandoffId?.let(entries::get)?.takeIf {
                it.animeId == arg.animeId && it.sourceId == arg.sourceId && it.episodeId == episodeId
            }?.source
        }

    fun claim(arg: NavigateToPlayerArg): PreviewPlaybackTransfer? {
        check(Looper.myLooper() == Looper.getMainLooper())
        val (entry, transfer) = synchronized(entries) {
            val entry = arg.previewHandoffId?.let(entries::get)?.takeIf {
                it.animeId == arg.animeId && it.sourceId == arg.sourceId &&
                    it.episodeId == initialEpisodeId(arg)
            } ?: return null
            val transfer = entry.transfer ?: return null
            if (transfer.player.playerError != null || transfer.player.playbackState == Player.STATE_IDLE ||
                transfer.player.playbackState == Player.STATE_ENDED) {
                // Reject source and player atomically: old events are not replayed to a new listener.
                arg.previewHandoffId?.let(entries::remove)
                entry to null
            } else {
                entry.transfer = null
                entry to transfer
            }
        }
        if (transfer == null) {
            dispose(entry)
            return null
        }
        entry.promoteBuffer()
        entry.detachPreview()
        return transfer
    }

    /** Also used when activity navigation fails, so an unclaimed decoder cannot leak. */
    fun expire(token: String?) {
        check(Looper.myLooper() == Looper.getMainLooper())
        if (token == null) return
        val entry = synchronized(entries) { entries.remove(token) } ?: return
        dispose(entry)
    }

    private fun dispose(entry: Entry) {
        entry.transfer?.let {
            entry.detachPreview()
            it.player.release()
            it.snapshot?.recycle()
        }
    }

    private fun initialEpisodeId(arg: NavigateToPlayerArg): String =
        (arg.playlists.getOrNull(arg.playlistIndex)?.episodeList?.takeIf { it.isNotEmpty() }
            ?: arg.playlist).getOrNull(arg.playIndex)?.episodeId.orEmpty()
}
