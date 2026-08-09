package com.jing.sakura.update

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

private class NonRetryableUpdateException(message: String) : IOException(message)

internal enum class TvUpdateSourceFormat {
    AULAMA_MANIFEST,
    GITHUB_RELEASE
}

internal data class TvUpdateEndpoint(
    val url: String,
    val format: TvUpdateSourceFormat
)

internal data class TvUpdateUrlPolicy(
    val updateHosts: Set<String>,
    val downloadHosts: Set<String>,
    val requireHttps: Boolean = true
) {
    fun requireUpdateUrl(value: String): HttpUrl = requireUrl(value, updateHosts, "更新資料")

    fun requireDownloadUrl(value: String): HttpUrl = requireUrl(value, downloadHosts, "更新檔案")

    private fun requireUrl(value: String, allowedHosts: Set<String>, label: String): HttpUrl {
        val url = value.toHttpUrlOrNull() ?: throw IOException("$label URL 無效")
        if (requireHttps && (url.scheme != "https" || url.port != 443)) {
            throw IOException("${label}必須使用 HTTPS")
        }
        if (url.host.lowercase() !in allowedHosts) {
            throw IOException("${label}來源不受信任")
        }
        return url
    }
}

internal val PRODUCTION_TV_UPDATE_URL_POLICY = TvUpdateUrlPolicy(
    updateHosts = setOf("aulama.org", "api.github.com"),
    downloadHosts = setOf("aulama.org", "github.com")
)

internal class TvUpdateChecker(
    private val client: OkHttpClient,
    private val primary: TvUpdateEndpoint,
    private val fallback: TvUpdateEndpoint,
    private val currentVersionCode: Int,
    private val currentVersionName: String,
    private val userAgent: String,
    private val urlPolicy: TvUpdateUrlPolicy = PRODUCTION_TV_UPDATE_URL_POLICY,
    private val retryDelayMillis: Long = 350L
) {
    fun checkForUpdateDetailed(): TvUpdateCheckResult {
        val primaryFailure = try {
            return check(primary)
        } catch (error: IOException) {
            error
        }

        return try {
            check(fallback)
        } catch (fallbackFailure: IOException) {
            fallbackFailure.addSuppressed(primaryFailure)
            throw fallbackFailure
        }
    }

    private fun check(endpoint: TvUpdateEndpoint): TvUpdateCheckResult {
        val document = fetch(endpoint.url)
        return try {
            when (endpoint.format) {
                TvUpdateSourceFormat.AULAMA_MANIFEST -> parseAulamaManifest(document)
                TvUpdateSourceFormat.GITHUB_RELEASE -> parseGithubRelease(document)
            }
        } catch (error: IOException) {
            throw error
        } catch (error: Exception) {
            throw IOException("更新資料格式無效", error)
        }
    }

    private fun fetch(value: String): FetchedUpdateDocument {
        val url = urlPolicy.requireUpdateUrl(value)
        var lastFailure: IOException? = null

        repeat(MAX_ATTEMPTS) { attempt ->
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("Accept", "application/json, application/vnd.github+json")
                    .header("User-Agent", userAgent)
                    .build()
                client.newCall(request).execute().use { response ->
                    val transient = response.code == 408 || response.code == 429 || response.code in 500..599
                    if (!response.isSuccessful) {
                        val failure = IOException("更新資料請求失敗：HTTP ${response.code}")
                        if (transient && attempt + 1 < MAX_ATTEMPTS) {
                            lastFailure = failure
                            waitBeforeRetry()
                            return@repeat
                        }
                        if (transient) throw failure
                        throw NonRetryableUpdateException(failure.message.orEmpty())
                    }

                    val finalUrl = try {
                        urlPolicy.requireUpdateUrl(response.request.url.toString())
                    } catch (error: IOException) {
                        throw NonRetryableUpdateException(error.message.orEmpty())
                    }
                    val contentType = response.body?.contentType()
                    if (contentType?.subtype?.endsWith("json", ignoreCase = true) != true) {
                        throw NonRetryableUpdateException("更新資料 Content-Type 無效")
                    }
                    return FetchedUpdateDocument(
                        body = response.body?.string().orEmpty(),
                        url = finalUrl
                    )
                }
            } catch (error: NonRetryableUpdateException) {
                throw error
            } catch (error: IOException) {
                lastFailure = error
                if (attempt + 1 >= MAX_ATTEMPTS) throw error
                waitBeforeRetry()
            }
        }
        throw lastFailure ?: IOException("無法取得更新資料")
    }

    private fun parseAulamaManifest(document: FetchedUpdateDocument): TvUpdateCheckResult {
        val root = parseObject(document.body)
        val platform = root.requiredString("platform")
        if (platform != "android-tv") throw IOException("更新資料平台不符")
        if (root.requiredString("status") != "published") return TvUpdateCheckResult.UpToDate

        val versionCode = root.requiredInt("versionCode")
        if (versionCode <= currentVersionCode) return TvUpdateCheckResult.UpToDate

        val version = root.requiredString("version")
        val apkValue = root.requiredString("apk")
        val resolvedApk = document.url.resolve(apkValue)
            ?: throw IOException("更新檔案 URL 無效")
        val downloadUrl = urlPolicy.requireDownloadUrl(resolvedApk.toString()).toString()
        val sha256 = normalizeSha256(root.requiredString("sha256"))

        return TvUpdateCheckResult.Available(
            TvUpdate(
                version = version,
                versionCode = versionCode,
                downloadUrl = downloadUrl,
                sha256 = sha256,
                notes = root.optionalString("notes").take(MAX_RELEASE_NOTES_LENGTH)
            )
        )
    }

    private fun parseGithubRelease(document: FetchedUpdateDocument): TvUpdateCheckResult {
        val root = parseObject(document.body)
        if (root.get("draft")?.asBoolean == true || root.get("prerelease")?.asBoolean == true) {
            return TvUpdateCheckResult.UpToDate
        }
        val version = extractVersion(root.requiredString("tag_name"))
        if (!isNewerVersion(version, currentVersionName)) return TvUpdateCheckResult.UpToDate

        val apkAssets = root.getAsJsonArray("assets")
            ?.mapNotNull { it.takeIf { value -> value.isJsonObject }?.asJsonObject }
            ?.filter { item -> item.optionalString("name").endsWith(".apk", ignoreCase = true) }
            .orEmpty()
        val asset = apkAssets.firstOrNull { item ->
            item.optionalString("name").contains("tv", ignoreCase = true)
        } ?: apkAssets.firstOrNull() ?: throw IOException("Release 未包含 APK")
        val downloadUrl = urlPolicy.requireDownloadUrl(asset.requiredString("browser_download_url")).toString()
        val sha256 = normalizeSha256(asset.requiredString("digest"))

        return TvUpdateCheckResult.Available(
            TvUpdate(
                version = version,
                versionCode = null,
                downloadUrl = downloadUrl,
                sha256 = sha256,
                notes = root.optionalString("body").trim().take(MAX_RELEASE_NOTES_LENGTH)
            )
        )
    }

    private fun waitBeforeRetry() {
        if (retryDelayMillis > 0L) Thread.sleep(retryDelayMillis)
    }

    private data class FetchedUpdateDocument(
        val body: String,
        val url: HttpUrl
    )

    companion object {
        private const val MAX_ATTEMPTS = 2
        private const val MAX_RELEASE_NOTES_LENGTH = 4_000
        private val VERSION_PATTERN = Regex("\\d+(?:\\.\\d+)+")

        private fun parseObject(value: String): JsonObject =
            JsonParser.parseString(value).takeIf { it.isJsonObject }?.asJsonObject
                ?: throw IOException("更新資料並非 JSON object")

        private fun JsonObject.requiredString(name: String): String =
            optionalString(name).takeIf(String::isNotBlank)
                ?: throw IOException("更新資料缺少 $name")

        private fun JsonObject.optionalString(name: String): String =
            runCatching { get(name)?.takeUnless { it.isJsonNull }?.asString.orEmpty() }.getOrDefault("")

        private fun JsonObject.requiredInt(name: String): Int =
            runCatching { get(name)?.asInt }.getOrNull()
                ?: throw IOException("更新資料缺少 $name")

        private fun normalizeSha256(value: String): String {
            val normalized = value.substringAfter("sha256:", value).trim().lowercase()
            if (!SHA256_PATTERN.matches(normalized)) throw IOException("更新檔案 SHA-256 無效")
            return normalized
        }

        private fun extractVersion(value: String): String =
            VERSION_PATTERN.find(value)?.value.orEmpty()

        private fun isNewerVersion(remote: String, local: String): Boolean {
            val remoteParts = remote.toVersionParts()
            val localParts = local.toVersionParts()
            val maxSize = maxOf(remoteParts.size, localParts.size)
            repeat(maxSize) { index ->
                val remotePart = remoteParts.getOrElse(index) { 0 }
                val localPart = localParts.getOrElse(index) { 0 }
                if (remotePart != localPart) return remotePart > localPart
            }
            return false
        }

        private fun String.toVersionParts(): List<Int> =
            substringBefore('-')
                .split('.')
                .map { part -> part.filter(Char::isDigit).toIntOrNull() ?: 0 }
    }
}

internal val SHA256_PATTERN = Regex("^[0-9a-fA-F]{64}$")
