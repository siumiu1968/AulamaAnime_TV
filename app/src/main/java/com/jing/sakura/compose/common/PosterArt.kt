package com.jing.sakura.compose.common

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import coil.request.CachePolicy
import coil.request.ImageRequest
import java.net.URLEncoder
import org.koin.core.context.GlobalContext
import com.jing.sakura.auth.AulamaAuthRepository

internal fun posterDownloadUrl(imageUrl: String, widthPx: Int = 420): String {
    if (!imageUrl.startsWith("https://") && !imageUrl.startsWith("http://")) return imageUrl
    if (imageUrl.startsWith("https://aulama.org/anime/api/image?")) return imageUrl
    val width = when { widthPx <= 320 -> 320; widthPx <= 540 -> 540; widthPx <= 960 -> 960; else -> 1600 }
    return "https://aulama.org/anime/api/image?w=$width&url=${URLEncoder.encode(imageUrl, "UTF-8")}"
}

internal fun posterImageRequest(context: Context, imageUrl: String, widthPx: Int = 420, heightPx: Int = 600): ImageRequest {
    val session = GlobalContext.getOrNull()?.getOrNull<AulamaAuthRepository>()?.session?.value
    val downloadUrl = if (session != null) posterDownloadUrl(imageUrl, widthPx) else imageUrl
    return ImageRequest.Builder(context)
        .data(downloadUrl)
        .apply { if (session != null && downloadUrl.startsWith("https://aulama.org/anime/api/image?")) addHeader("Authorization", "Bearer ${session.accessToken}") }
        .memoryCacheKey("$imageUrl@${widthPx}x$heightPx")
        .diskCacheKey(downloadUrl)
        .memoryCachePolicy(CachePolicy.ENABLED)
        .diskCachePolicy(CachePolicy.ENABLED)
        .networkCachePolicy(CachePolicy.ENABLED)
        .placeholder(android.graphics.drawable.ColorDrawable(android.graphics.Color.rgb(21, 27, 39)))
        .error(android.graphics.drawable.ColorDrawable(android.graphics.Color.rgb(21, 27, 39)))
        .crossfade(false)
        .size(widthPx, heightPx)
        .build()
}

@Composable
fun rememberPosterImageRequest(imageUrl: String, widthPx: Int = 420, heightPx: Int = 600): ImageRequest {
    val context = LocalContext.current
    return remember(imageUrl, widthPx, heightPx) { posterImageRequest(context, imageUrl, widthPx, heightPx) }
}
