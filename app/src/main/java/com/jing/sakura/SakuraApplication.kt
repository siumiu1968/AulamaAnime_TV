package com.jing.sakura

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.webkit.WebSettings
import androidx.room.Room
import coil.disk.DiskCache
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.memory.MemoryCache
import com.jing.sakura.auth.AulamaAuthRepository
import com.jing.sakura.auth.AuthViewModel
import com.jing.sakura.auth.GuestLibraryStore
import com.jing.sakura.auth.GuestModeStorage
import com.jing.sakura.auth.PlaybackHistorySyncQueue
import com.jing.sakura.auth.PlaybackHistorySyncScheduler
import com.jing.sakura.auth.SecureAuthStorage
import com.jing.sakura.auth.SearchHistorySyncQueue
import com.jing.sakura.auth.SearchHistorySyncScheduler
import com.jing.sakura.compose.common.ChineseText
import com.jing.sakura.detail.DetailPageViewModel
import com.jing.sakura.extend.WebViewCompatibleCookieJar
import com.jing.sakura.history.HistoryViewModel
import com.jing.sakura.home.CategoryViewModel
import com.jing.sakura.home.HomeViewModel
import com.jing.sakura.http.WebServerContext
import com.jing.sakura.player.MislabelledHlsMediaInterceptor
import com.jing.sakura.player.PlaybackActivity
import com.jing.sakura.player.VideoPlayerViewModel
import com.jing.sakura.remote.RemotePlaybackCoordinator
import com.jing.sakura.repo.WebPageRepository
import com.jing.sakura.room.SakuraDatabase
import com.jing.sakura.search.SearchResultViewModel
import com.jing.sakura.search.SearchViewModel
import com.jing.sakura.timeline.TimelineViewModel
import okhttp3.Dns
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.androidx.viewmodel.dsl.viewModelOf
import org.koin.core.context.startKoin
import org.koin.core.Koin
import org.koin.core.qualifier.qualifier
import org.koin.dsl.module
import java.lang.ref.WeakReference
import java.net.InetAddress
import java.net.Inet4Address
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class SakuraApplication : Application(), ImageLoaderFactory {

    private val remotePlaybackScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val startupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var remotePlaybackJob: Job? = null
    private var currentActivity = WeakReference<Activity>(null)
    private lateinit var applicationKoin: Koin

    private val remotePlaybackCallbacks = object : Application.ActivityLifecycleCallbacks {
        override fun onActivityResumed(activity: Activity) {
            currentActivity = WeakReference(activity)
            startRemotePlayback()
        }

        override fun onActivityPaused(activity: Activity) {
            if (currentActivity.get() === activity) {
                currentActivity.clear()
                remotePlaybackJob?.cancel()
                remotePlaybackJob = null
            }
        }

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
        override fun onActivityStarted(activity: Activity) = Unit
        override fun onActivityStopped(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
        override fun onActivityDestroyed(activity: Activity) = Unit
    }

    override fun onCreate() {
        super.onCreate()
        context = this
        ChineseText.warmUpAsync()
        applicationKoin = startKoin {
            androidContext(this@SakuraApplication)
            androidLogger()
            modules(httpModule(), roomModule(), viewModelModule())
        }.koin
        val accountKey = applicationKoin.get<AulamaAuthRepository>()
            .session.value?.account?.email
        if (!accountKey.isNullOrBlank()) {
            startupScope.launch {
                if (applicationKoin.get<PlaybackHistorySyncQueue>()
                        .pendingForAccount(accountKey)
                        .isNotEmpty()
                ) {
                    PlaybackHistorySyncScheduler.enqueue(this@SakuraApplication)
                }
                if (applicationKoin.get<SearchHistorySyncQueue>()
                        .pendingForAccount(accountKey)
                        .isNotEmpty()
                ) {
                    SearchHistorySyncScheduler.enqueue(this@SakuraApplication)
                }
            }
        }
        registerActivityLifecycleCallbacks(remotePlaybackCallbacks)
    }

    private fun startRemotePlayback() {
        if (remotePlaybackJob?.isActive == true) return
        remotePlaybackJob = remotePlaybackScope.launch {
            RemotePlaybackCoordinator.runWhileStarted(
                owner = this@SakuraApplication,
                authRepository = applicationKoin.get(),
                webPageRepository = applicationKoin.get()
            ) { playerArg ->
                val activity = currentActivity.get() ?: return@runWhileStarted false
                PlaybackActivity.startActivity(activity, playerArg)
                if (activity is PlaybackActivity) activity.finish()
                true
            }
        }
    }

    companion object {
        private const val TAG = "SakuraApplication"
        private const val FALLBACK_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 6.0; TV) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/99.0.0.0 Safari/537.36"

        @SuppressLint("StaticFieldLeak")
        lateinit var context: Context
            private set

        val USER_AGENT by lazy {
            try {
                WebSettings.getDefaultUserAgent(context)
            } catch (error: RuntimeException) {
                Log.w(TAG, "System WebView is unavailable; using a fallback user agent", error)
                System.getProperty("http.agent").orEmpty().ifBlank { FALLBACK_USER_AGENT }
            }
        }
    }

    private fun httpModule() = module {
        single(qualifier(KoinOkHttpClient.DATA)) { provideOkHttpClient() }
        single(qualifier(KoinOkHttpClient.MEDIA)) {
            basicOkhttpClient()
                .addNetworkInterceptor(MislabelledHlsMediaInterceptor)
                .apply {
                    if (BuildConfig.DEBUG) {
                        addNetworkInterceptor(
                            HttpLoggingInterceptor().apply {
                                level = HttpLoggingInterceptor.Level.BASIC
                            })
                    }
                }
                .build()
        }
        single(qualifier(KoinOkHttpClient.AULAMA)) { provideAulamaOkHttpClient() }
        single { SecureAuthStorage(get()) }
        single { GuestModeStorage(get()) }
        single { GuestLibraryStore(get()) }
        single { AulamaAuthRepository(get(qualifier(KoinOkHttpClient.AULAMA)), get()) }
        single { PlaybackHistorySyncQueue(get()) }
        single { SearchHistorySyncQueue(get()) }
        single {
            WebPageRepository(
                get(qualifier = qualifier(KoinOkHttpClient.DATA)),
                get()
            )
        }
    }

    private fun roomModule() = module {
        single {
            val builder = Room.databaseBuilder(context, SakuraDatabase::class.java, "sk_db")
                .addMigrations(
                    SakuraDatabase.MIGRATION_1_2,
                    SakuraDatabase.MIGRATION_2_3,
                    SakuraDatabase.MIGRATION_3_4,
                    SakuraDatabase.MIGRATION_4_5
                )
            if (BuildConfig.DEBUG) {
                builder.setQueryCallback({ sqlQuery, bindArgs ->
                    Log.d("RoomQuery", "sql: $sqlQuery, args: $bindArgs")
                }, Executors.newSingleThreadExecutor())
            }
            builder.build()
        }

        single {
            get<SakuraDatabase>().getVideoHistoryDao()
        }

        single {
            get<SakuraDatabase>().searchHistoryDao()
        }
    }

    private fun viewModelModule() = module {
        viewModel { holder ->
            DetailPageViewModel(holder.get(), get(), get(), get(), get(), holder.get())
        }
        viewModel { holder -> VideoPlayerViewModel(holder.get(), get(), get(), get(), get()) }
        viewModelOf(::HomeViewModel)
        viewModelOf(::AuthViewModel)
        viewModel { holder -> SearchViewModel(get(), get(), get(), holder.get()) }
        viewModel { holder -> TimelineViewModel(get(), holder.get()) }
        viewModelOf(::HistoryViewModel)
        viewModel { holder -> SearchResultViewModel(holder.get(), get(), holder.get()) }
        viewModel { holder -> CategoryViewModel(get(), holder.get()) }

    }

    private fun provideOkHttpClient(): OkHttpClient {
        val cookieJar = WebViewCompatibleCookieJar { error ->
            Log.w(TAG, "System WebView cookie store is unavailable; using in-memory cookies", error)
        }
        return basicOkhttpClient()
            .cookieJar(cookieJar)
            .apply {
                if (BuildConfig.DEBUG) {
                    addInterceptor(HttpLoggingInterceptor().apply {
                        level = HttpLoggingInterceptor.Level.HEADERS
                    })
                }
            }.build()
    }

    private fun provideAulamaOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .dns(object : Dns {
            override fun lookup(hostname: String): List<InetAddress> =
                Dns.SYSTEM.lookup(hostname).sortedBy { address ->
                    if (address is Inet4Address) 0 else 1
                }
        })
        .connectTimeout(5L, TimeUnit.SECONDS)
        .readTimeout(15L, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .addInterceptor { chain ->
            val request = chain.request()
            require(request.url.isHttps && request.url.host == "aulama.org") {
                "Aulama auth client only accepts https://aulama.org"
            }
            chain.proceed(
                request.newBuilder()
                    .header("User-Agent", "Aulama-Anime-TV/${BuildConfig.VERSION_NAME}")
                    .build()
            )
        }
        .apply {
            if (BuildConfig.DEBUG) {
                addNetworkInterceptor(
                    HttpLoggingInterceptor().apply {
                        level = HttpLoggingInterceptor.Level.BASIC
                    }
                )
            }
        }
        .build()

    private fun basicOkhttpClient(): OkHttpClient.Builder {
        val trustManager = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
            }

            override fun checkServerTrusted(
                chain: Array<out X509Certificate>?,
                authType: String?
            ) {
            }

            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }
        val sslSocketFactory = SSLContext.getInstance("TLS").apply {
            init(null, arrayOf(trustManager), SecureRandom())
        }.socketFactory
        return OkHttpClient.Builder()
            .connectTimeout(2L, TimeUnit.SECONDS)
            .readTimeout(20L, TimeUnit.SECONDS)
            .sslSocketFactory(sslSocketFactory, trustManager)
            .hostnameVerifier { _, _ -> true }
            .addInterceptor(Interceptor { chain ->
                val request = chain.request()
                val userAgent = request.header("User-Agent") ?: USER_AGENT
                chain.proceed(
                    request.newBuilder()
                        .header("User-Agent", userAgent)
                        .build()
                )
            })
    }

    override fun onTerminate() {
        unregisterActivityLifecycleCallbacks(remotePlaybackCallbacks)
        startupScope.cancel()
        remotePlaybackScope.cancel()
        WebServerContext.stopServer()
        super.onTerminate()
    }

    override fun newImageLoader(): ImageLoader = ImageLoader(this).newBuilder()
        .okHttpClient(basicOkhttpClient().build())
        .allowHardware(true)
        .memoryCache {
            MemoryCache.Builder(this)
                .maxSizePercent(0.25)
                .build()
        }
        .diskCache {
            DiskCache.Builder()
                .directory(cacheDir.resolve("anime_posters"))
                .maxSizeBytes(256L * 1024L * 1024L)
                .build()
        }
        .build()

    enum class KoinOkHttpClient {
        DATA, MEDIA, AULAMA
    }
}
