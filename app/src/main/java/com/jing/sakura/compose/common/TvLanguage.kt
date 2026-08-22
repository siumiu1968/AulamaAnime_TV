package com.jing.sakura.compose.common

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.core.os.ConfigurationCompat
import com.github.houbb.opencc4j.util.ZhConverterUtil
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.LinkedHashMap
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

enum class TvLanguage(val storageValue: String) {
    Traditional("zh-Hant"),
    Simplified("zh-Hans");

    companion object {
        internal fun fromSystemLanguageTag(languageTag: String): TvLanguage {
            val locale = Locale.forLanguageTag(languageTag.replace('_', '-'))
            if (locale.language != "zh") return Traditional
            if (locale.script.equals("Hant", ignoreCase = true)) return Traditional
            if (locale.script.equals("Hans", ignoreCase = true)) return Simplified
            return if (locale.country.uppercase(Locale.ROOT) in setOf("TW", "HK", "MO")) {
                Traditional
            } else {
                Simplified
            }
        }
    }
}

class TvLanguagePreferences private constructor(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )
    private val _language = MutableStateFlow(
        TvLanguage.entries.firstOrNull {
            it.storageValue == preferences.getString(KEY_LANGUAGE, null)
        } ?: TvLanguage.fromSystemLanguageTag(
            ConfigurationCompat.getLocales(context.resources.configuration)
                .get(0)
                ?.toLanguageTag()
                .orEmpty()
        )
    )
    val language: StateFlow<TvLanguage> = _language

    fun setLanguage(language: TvLanguage) {
        if (_language.value == language) return
        preferences.edit().putString(KEY_LANGUAGE, language.storageValue).apply()
        _language.value = language
    }

    companion object {
        private const val PREFERENCES_NAME = "aulama_tv_preferences"
        private const val KEY_LANGUAGE = "language"

        @Volatile
        private var instance: TvLanguagePreferences? = null

        fun get(context: Context): TvLanguagePreferences = instance ?: synchronized(this) {
            instance ?: TvLanguagePreferences(context).also { instance = it }
        }
    }
}

val LocalTvLanguage = compositionLocalOf { TvLanguage.Traditional }

object ChineseText {
    private const val MAX_CACHE_ENTRIES = 512
    private val warmUpStarted = AtomicBoolean(false)
    private val _ready = MutableStateFlow(false)
    internal val ready: StateFlow<Boolean> = _ready
    private val cache = object : LinkedHashMap<String, String>(MAX_CACHE_ENTRIES, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean =
            size > MAX_CACHE_ENTRIES
    }

    fun warmUpAsync() {
        if (_ready.value || !warmUpStarted.compareAndSet(false, true)) return
        Thread({
            val initialized = runCatching {
                ZhConverterUtil.toTraditional("測試")
                ZhConverterUtil.toSimple("测试")
            }.isSuccess
            if (initialized) {
                _ready.value = true
            } else {
                warmUpStarted.set(false)
            }
        }, "opencc-warmup").apply {
            isDaemon = true
            priority = Thread.MIN_PRIORITY
            start()
        }
    }

    fun convert(text: String, language: TvLanguage): String {
        if (text.isBlank()) return text
        if (!_ready.value) {
            warmUpAsync()
            return text
        }
        val cacheKey = "${language.storageValue}:$text"
        synchronized(cache) { cache[cacheKey] }?.let { return it }
        val converted = runCatching {
            when (language) {
                TvLanguage.Traditional -> ZhConverterUtil.toTraditional(text)
                TvLanguage.Simplified -> ZhConverterUtil.toSimple(text)
            }
        }.getOrElse { text }
        synchronized(cache) { cache[cacheKey] = converted }
        return converted
    }
}

@Composable
fun localizedText(text: String): String {
    val language = LocalTvLanguage.current
    val conversionReady by ChineseText.ready.collectAsState()
    return remember(text, language, conversionReady) { ChineseText.convert(text, language) }
}
