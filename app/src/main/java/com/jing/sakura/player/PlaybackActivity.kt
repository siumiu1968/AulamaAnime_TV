package com.jing.sakura.player

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.view.KeyEvent
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.FragmentActivity
import com.jing.sakura.R

/** Hosts the official Android TV Leanback playback surface and controls. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlaybackActivity : FragmentActivity() {
    private var previewTransfer: PreviewPlaybackTransfer? = null
    private var previewSnapshot: ImageView? = null
    private var previewBitmap: Bitmap? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        setContentView(R.layout.activity_playback)
        @Suppress("DEPRECATION")
        val arg = intent.getSerializableExtra("video") as? NavigateToPlayerArg
        if (arg != null) {
            previewTransfer = PreviewPlaybackHandoff.claim(arg)
            previewTransfer?.snapshot?.let { bitmap ->
                previewBitmap = bitmap
                previewSnapshot = ImageView(this).apply {
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    setImageBitmap(bitmap)
                    isFocusable = false
                    importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO
                }.also {
                    findViewById<FrameLayout>(R.id.playback_root).addView(it, 1,
                        ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                }
            }
        }

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(
                    R.id.playback_fragment_container,
                    AnimePlayerFragment(),
                    TAG_PLAYER
                )
                .commit()
        }
    }

    internal fun takePreviewPlayer(): PreviewPlaybackTransfer? = previewTransfer.also { previewTransfer = null }

    internal fun onPlaybackFirstFrame() {
        val image = previewSnapshot ?: return
        image.animate().alpha(0f).setDuration(160L).withEndAction { clearPreviewSnapshot() }.start()
    }

    internal fun clearPreviewSnapshot() {
        previewSnapshot?.let {
            it.animate().cancel()
            it.setImageDrawable(null)
            (it.parent as? ViewGroup)?.removeView(it)
        }
        previewSnapshot = null
        previewBitmap?.recycle()
        previewBitmap = null
    }

    override fun onDestroy() {
        previewTransfer?.player?.release()
        previewTransfer = null
        clearPreviewSnapshot()
        super.onDestroy()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val playerFragment = supportFragmentManager.findFragmentByTag(TAG_PLAYER)
            as? AnimePlayerFragment
        if (playerFragment?.handlePlaybackKeyEvent(event) == true) return true
        return super.dispatchKeyEvent(event)
    }

    companion object {
        private const val TAG_PLAYER = "leanback_player"

        fun startActivity(context: Context, arg: NavigateToPlayerArg) {
            val intent = createIntent(context, arg)
            if (arg.previewHandoffId == null) context.startActivity(intent)
            else context.startActivity(intent,
                ActivityOptions.makeCustomAnimation(context, 0, 0).toBundle())
        }

        fun discardPreviewContinuation(arg: NavigateToPlayerArg) {
            PreviewPlaybackHandoff.expire(arg.previewHandoffId)
        }

        private fun createIntent(context: Context, arg: NavigateToPlayerArg): Intent =
            Intent(context, PlaybackActivity::class.java).putExtra("video", arg)
    }
}
