package com.jing.sakura.update

import android.app.Activity
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import com.jing.sakura.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.InputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

data class TvUpdate(
    val version: String,
    val versionCode: Int?,
    val downloadUrl: String,
    val sha256: String,
    val notes: String
)

sealed interface TvUpdateCheckResult {
    data class Available(val update: TvUpdate) : TvUpdateCheckResult
    data object UpToDate : TvUpdateCheckResult
}

sealed interface TvUpdateDownloadState {
    data object Idle : TvUpdateDownloadState
    data object Starting : TvUpdateDownloadState
    data class Downloading(
        val downloadedBytes: Long,
        val totalBytes: Long,
        val percent: Int?
    ) : TvUpdateDownloadState
    data object PreparingInstall : TvUpdateDownloadState
    data object Installing : TvUpdateDownloadState
    data class Failed(val message: String) : TvUpdateDownloadState
}

internal data class TvDownloadSnapshot(
    val status: Int,
    val downloadedBytes: Long,
    val totalBytes: Long,
    val reason: Int
) {
    val percent: Int?
        get() = if (totalBytes > 0L) {
            ((downloadedBytes.toDouble() / totalBytes.toDouble()) * 100.0)
                .toInt()
                .coerceIn(0, 100)
        } else {
            null
        }
}

class TvUpdateManager(private val activity: Activity) {
    private val downloadManager = activity.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    private val preferences = activity.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .retryOnConnectionFailure(false)
        .build()
    private val checker = TvUpdateChecker(
        client = client,
        primary = TvUpdateEndpoint(PRIMARY_UPDATE_URL, TvUpdateSourceFormat.AULAMA_MANIFEST),
        fallback = TvUpdateEndpoint(FALLBACK_UPDATE_URL, TvUpdateSourceFormat.GITHUB_RELEASE),
        currentVersionCode = BuildConfig.VERSION_CODE,
        currentVersionName = BuildConfig.VERSION_NAME,
        userAgent = "Aulama-Anime-TV/${BuildConfig.VERSION_NAME}"
    )
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _downloadState = MutableStateFlow<TvUpdateDownloadState>(TvUpdateDownloadState.Idle)
    val downloadState: StateFlow<TvUpdateDownloadState> = _downloadState.asStateFlow()
    private var monitorJob: Job? = null
    private var installPermissionRequested = false

    suspend fun checkForUpdate(): TvUpdate? =
        when (val result = checkForUpdateDetailed()) {
            is TvUpdateCheckResult.Available -> result.update
            TvUpdateCheckResult.UpToDate -> null
        }

    suspend fun checkForUpdateDetailed(): TvUpdateCheckResult = withContext(Dispatchers.IO) {
        checker.checkForUpdateDetailed()
    }

    fun download(update: TvUpdate): Long {
        val expectedSha256 = update.sha256.lowercase()
        require(SHA256_PATTERN.matches(expectedSha256)) { "更新檔案 SHA-256 無效" }
        PRODUCTION_TV_UPDATE_URL_POLICY.requireDownloadUrl(update.downloadUrl)

        val pendingId = preferences.getLong(PENDING_DOWNLOAD_ID, -1L)
        if (pendingId >= 0L) {
            val pendingSnapshot = querySnapshot(pendingId)
            val pendingSha256 = preferences.getString(PENDING_DOWNLOAD_SHA256, null)
            if (pendingSnapshot != null &&
                pendingSnapshot.status in RESUMABLE_DOWNLOAD_STATUSES &&
                pendingSha256.equals(expectedSha256, ignoreCase = true)
            ) {
                if (pendingSnapshot.status == DownloadManager.STATUS_SUCCESSFUL) {
                    installPermissionRequested = false
                }
                monitorDownload(pendingId)
                return pendingId
            }
            downloadManager.remove(pendingId)
            clearPendingDownload(pendingId)
        }

        _downloadState.value = TvUpdateDownloadState.Starting
        val fileName = "aulama-anime-tv-v${update.version}.apk"
        activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?.resolve(fileName)
            ?.delete()
        val request = DownloadManager.Request(Uri.parse(update.downloadUrl))
            .setTitle("Aulama Anime TV ${update.version}")
            .setDescription("下載完成後即可安裝")
            .setMimeType(APK_MIME_TYPE)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)
            .setDestinationInExternalFilesDir(activity, Environment.DIRECTORY_DOWNLOADS, fileName)
        return runCatching { downloadManager.enqueue(request) }
            .onFailure { error ->
                _downloadState.value = TvUpdateDownloadState.Failed(
                    error.message ?: "無法開始下載，請稍後再試"
                )
            }
            .getOrThrow()
            .also { downloadId ->
                preferences.edit()
                    .putLong(PENDING_DOWNLOAD_ID, downloadId)
                    .putString(PENDING_DOWNLOAD_SHA256, expectedSha256)
                    .apply()
                monitorDownload(downloadId)
            }
    }

    fun resumePendingDownload() {
        val downloadId = preferences.getLong(PENDING_DOWNLOAD_ID, -1L)
        if (downloadId >= 0L) {
            monitorDownload(downloadId)
        }
    }

    fun handleDownloadComplete(downloadId: Long) {
        if (downloadId == preferences.getLong(PENDING_DOWNLOAD_ID, -1L)) {
            monitorDownload(downloadId)
        }
    }

    fun close() {
        scope.cancel()
    }

    private fun installPendingUpdate(): Boolean {
        val downloadId = preferences.getLong(PENDING_DOWNLOAD_ID, -1L)
        if (downloadId < 0) return false
        val status = queryStatus(downloadId) ?: return false
        if (status != DownloadManager.STATUS_SUCCESSFUL) return false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !activity.packageManager.canRequestPackageInstalls()
        ) {
            if (installPermissionRequested) return false
            installPermissionRequested = true
            activity.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${activity.packageName}")
                )
            )
            return true
        }

        installPermissionRequested = false

        val uri = downloadManager.getUriForDownloadedFile(downloadId) ?: return false
        return runCatching {
            activity.startActivity(
                Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, APK_MIME_TYPE)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            clearPendingDownload(downloadId)
            true
        }.getOrDefault(false)
    }

    private fun monitorDownload(downloadId: Long) {
        if (monitorJob?.isActive == true && monitoredDownloadId == downloadId) return
        monitorJob?.cancel()
        monitoredDownloadId = downloadId
        monitorJob = scope.launch {
            while (isActive) {
                val snapshot = querySnapshot(downloadId)
                if (snapshot == null) {
                    clearPendingDownload(downloadId)
                    _downloadState.value = TvUpdateDownloadState.Failed("找不到下載項目，請重新下載")
                    break
                }
                when (snapshot.status) {
                    DownloadManager.STATUS_PENDING,
                    DownloadManager.STATUS_RUNNING,
                    DownloadManager.STATUS_PAUSED -> {
                        _downloadState.value = TvUpdateDownloadState.Downloading(
                            downloadedBytes = snapshot.downloadedBytes,
                            totalBytes = snapshot.totalBytes,
                            percent = snapshot.percent
                        )
                        delay(DOWNLOAD_POLL_INTERVAL_MS)
                    }

                    DownloadManager.STATUS_SUCCESSFUL -> {
                        _downloadState.value = TvUpdateDownloadState.PreparingInstall
                        if (!verifyDownloadedApk(downloadId)) {
                            downloadManager.remove(downloadId)
                            clearPendingDownload(downloadId)
                            _downloadState.value = TvUpdateDownloadState.Failed(
                                "更新檔案驗證失敗，請重新下載"
                            )
                            break
                        }
                        val launched = withContext(Dispatchers.Main.immediate) {
                            installPendingUpdate()
                        }
                        _downloadState.value = if (launched) {
                            TvUpdateDownloadState.Installing
                        } else {
                            TvUpdateDownloadState.Failed("下載已完成，但無法開啟安裝程式")
                        }
                        break
                    }

                    DownloadManager.STATUS_FAILED -> {
                        clearPendingDownload(downloadId)
                        _downloadState.value = TvUpdateDownloadState.Failed(
                            downloadFailureMessage(snapshot.reason)
                        )
                        break
                    }

                    else -> {
                        clearPendingDownload(downloadId)
                        _downloadState.value = TvUpdateDownloadState.Failed("下載狀態異常，請重新下載")
                        break
                    }
                }
            }
            if (monitoredDownloadId == downloadId) monitoredDownloadId = -1L
        }
    }

    private fun queryStatus(downloadId: Long): Int? = querySnapshot(downloadId)?.status

    private fun querySnapshot(downloadId: Long): TvDownloadSnapshot? {
        val cursor: Cursor = downloadManager.query(
            DownloadManager.Query().setFilterById(downloadId)
        ) ?: return null
        return cursor.use {
            if (!it.moveToFirst()) return@use null
            TvDownloadSnapshot(
                status = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)),
                downloadedBytes = it.getLong(
                    it.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                ).coerceAtLeast(0L),
                totalBytes = it.getLong(
                    it.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                ).coerceAtLeast(0L),
                reason = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
            )
        }
    }

    private fun clearPendingDownload(downloadId: Long) {
        if (preferences.getLong(PENDING_DOWNLOAD_ID, -1L) == downloadId) {
            preferences.edit()
                .remove(PENDING_DOWNLOAD_ID)
                .remove(PENDING_DOWNLOAD_SHA256)
                .apply()
        }
    }

    private fun verifyDownloadedApk(downloadId: Long): Boolean {
        val expected = preferences.getString(PENDING_DOWNLOAD_SHA256, null)
            ?.lowercase()
            ?.takeIf(SHA256_PATTERN::matches)
            ?: return false
        val uri = downloadManager.getUriForDownloadedFile(downloadId) ?: return false
        return runCatching {
            activity.contentResolver.openInputStream(uri)?.use(::sha256Hex) == expected
        }.getOrDefault(false)
    }

    private fun downloadFailureMessage(reason: Int): String = when (reason) {
        DownloadManager.ERROR_INSUFFICIENT_SPACE -> "儲存空間不足，請清理空間後再試"
        DownloadManager.ERROR_CANNOT_RESUME -> "下載連線中斷，請重新下載"
        DownloadManager.ERROR_HTTP_DATA_ERROR,
        DownloadManager.ERROR_UNHANDLED_HTTP_CODE -> "下載伺服器暫時未能回應，請稍後再試"
        DownloadManager.ERROR_FILE_ALREADY_EXISTS -> "更新檔案已存在，請重新下載"
        else -> "下載失敗，請檢查網絡後再試"
    }

    companion object {
        private const val PRIMARY_UPDATE_URL = "https://aulama.org/anime/tv-update.json"
        private const val FALLBACK_UPDATE_URL =
            "https://api.github.com/repos/siumiu1968/AulamaAnime_TV/releases/latest"
        private const val APK_MIME_TYPE = "application/vnd.android.package-archive"
        private const val PREFERENCES = "tv_update"
        private const val PENDING_DOWNLOAD_ID = "pending_download_id"
        private const val PENDING_DOWNLOAD_SHA256 = "pending_download_sha256"
        private const val DOWNLOAD_POLL_INTERVAL_MS = 500L
        private val RESUMABLE_DOWNLOAD_STATUSES = setOf(
            DownloadManager.STATUS_PENDING,
            DownloadManager.STATUS_RUNNING,
            DownloadManager.STATUS_PAUSED,
            DownloadManager.STATUS_SUCCESSFUL
        )
    }

    private var monitoredDownloadId: Long = -1L
}

internal fun sha256Hex(input: InputStream): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    while (true) {
        val read = input.read(buffer)
        if (read < 0) break
        if (read > 0) digest.update(buffer, 0, read)
    }
    return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
}
