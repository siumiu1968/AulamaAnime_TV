package com.jing.sakura.update

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import com.jing.sakura.compose.theme.SakuraTheme

class UpdateDebugActivity : ComponentActivity() {
    private lateinit var updateManager: TvUpdateManager

    @OptIn(ExperimentalTvMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        updateManager = TvUpdateManager(this)
        val update = TvUpdate(
            version = "2.9.1",
            versionCode = null,
            downloadUrl = "https://github.com/siumiu1968/ciyuanbox-tv/releases/download/v2.9.1/aulama-anime-tv-v2.9.1.apk",
            sha256 = "4614cf90052700b857b109f485c2060f87a1750fef54194e24f07befbeda0584",
            notes = """
                ## 今次更新
                - 測試真實 APK 下載進度
                - 下載完成後開啟系統安裝程式
                - 防止重複建立下載工作
            """.trimIndent()
        )
        setContent {
            SakuraTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    TvUpdateDialog(
                        update = update,
                        downloadState = updateManager.downloadState.collectAsState().value,
                        onDownload = { runCatching { updateManager.download(update) } },
                        onLater = { finish() }
                    )
                }
            }
        }
        updateManager.resumePendingDownload()
    }

    override fun onResume() {
        super.onResume()
        if (::updateManager.isInitialized) updateManager.resumePendingDownload()
    }

    override fun onDestroy() {
        if (::updateManager.isInitialized) updateManager.close()
        super.onDestroy()
    }
}
