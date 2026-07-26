package com.jing.sakura.home

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import com.jing.sakura.R
import com.jing.sakura.auth.AuthUiState
import com.jing.sakura.auth.AuthViewModel
import com.jing.sakura.compose.screen.DeviceLoginScreen
import com.jing.sakura.compose.screen.HomeScreen
import com.jing.sakura.compose.theme.setAulamaTvContent
import com.jing.sakura.data.Resource
import com.jing.sakura.update.TvUpdate
import com.jing.sakura.update.TvUpdateDialog
import com.jing.sakura.update.TvUpdateDownloadState
import com.jing.sakura.update.TvUpdateManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel


class MainActivity : ComponentActivity() {
    private lateinit var updateManager: TvUpdateManager
    private val availableUpdate = mutableStateOf<TvUpdate?>(null)
    private val isCheckingForUpdate = mutableStateOf(false)
    private var receiverRegistered = false
    private var automaticUpdateCheck: Job? = null

    private val downloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
            val downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
            updateManager.handleDownloadComplete(downloadId)
        }
    }

    @OptIn(ExperimentalTvMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        updateManager = TvUpdateManager(this)
        registerDownloadReceiver()
        val viewModel: HomeViewModel by viewModel()
        val authViewModel: AuthViewModel by viewModel()
        setAulamaTvContent {
            val downloadState = updateManager.downloadState.collectAsState().value
            val homePageData = viewModel.homePageData.collectAsState().value
            val welcomeRandomSeed = remember { System.nanoTime().toInt() }
            val welcomeAnime = remember(homePageData, welcomeRandomSeed) {
                val rows = (homePageData as? Resource.Success)
                    ?.data
                    ?.seriesList
                    .orEmpty()
                    .map { it.value }
                welcomeBackdropAnime(rows = rows, randomSeed = welcomeRandomSeed)
            }
            LaunchedEffect(downloadState) {
                if (downloadState is TvUpdateDownloadState.Installing) {
                    availableUpdate.value = null
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                CompositionLocalProvider(
                    androidx.tv.material3.LocalContentColor provides MaterialTheme.colorScheme.onSurface,
                    androidx.compose.material3.LocalContentColor provides MaterialTheme.colorScheme.onSurface
                ) {
                    when (val authState = authViewModel.state.collectAsState().value) {
                        is AuthUiState.Authenticated -> HomeScreen(
                            viewModel = viewModel,
                            account = authState.account,
                            onLogout = authViewModel::logout,
                            isCheckingForUpdate = isCheckingForUpdate.value,
                            onCheckForUpdate = ::checkForUpdateManually
                        )
                        else -> DeviceLoginScreen(
                            state = authState,
                            onLogin = authViewModel::startLogin,
                            onCancel = authViewModel::cancelLogin,
                            onRetry = authViewModel::retryLogin,
                            welcomeAnime = welcomeAnime
                        )
                    }
                }
                availableUpdate.value?.let { update ->
                    TvUpdateDialog(
                        update = update,
                        downloadState = downloadState,
                        onDownload = {
                            runCatching { updateManager.download(update) }
                        },
                        onLater = { availableUpdate.value = null }
                    )
                }
            }
        }
        refreshAvailableUpdate()
        updateManager.resumePendingDownload()
    }

    override fun onResume() {
        super.onResume()
        if (::updateManager.isInitialized) {
            updateManager.resumePendingDownload()
            refreshAvailableUpdate()
        }
    }

    override fun onDestroy() {
        if (receiverRegistered) {
            runCatching { unregisterReceiver(downloadReceiver) }
        }
        if (::updateManager.isInitialized) updateManager.close()
        super.onDestroy()
    }

    private fun registerDownloadReceiver() {
        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(downloadReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(downloadReceiver, filter)
        }
        receiverRegistered = true
    }

    private fun checkForUpdateManually() {
        if (isCheckingForUpdate.value) return
        isCheckingForUpdate.value = true
        lifecycleScope.launch {
            try {
                val update = updateManager.checkForUpdate()
                if (update != null) {
                    availableUpdate.value = update
                } else {
                    Toast.makeText(
                        this@MainActivity,
                        "目前已是最新版本",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                Toast.makeText(
                    this@MainActivity,
                    "檢查更新失敗，請稍後再試",
                    Toast.LENGTH_SHORT
                ).show()
            } finally {
                isCheckingForUpdate.value = false
            }
        }
    }

    private fun refreshAvailableUpdate() {
        if (availableUpdate.value != null || automaticUpdateCheck?.isActive == true) return
        automaticUpdateCheck = lifecycleScope.launch {
            availableUpdate.value = runCatching { updateManager.checkForUpdate() }.getOrNull()
        }
    }
}
