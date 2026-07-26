package com.jing.sakura.compose.common

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class TvPreviewPreferences private constructor(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )
    private val _previewEnabled = MutableStateFlow(
        preferences.getBoolean(KEY_PREVIEW_ENABLED, true)
    )
    val previewEnabled: StateFlow<Boolean> = _previewEnabled

    fun setPreviewEnabled(enabled: Boolean) {
        if (_previewEnabled.value == enabled) return
        preferences.edit().putBoolean(KEY_PREVIEW_ENABLED, enabled).apply()
        _previewEnabled.value = enabled
    }

    companion object {
        private const val PREFERENCES_NAME = "aulama_tv_preferences"
        private const val KEY_PREVIEW_ENABLED = "preview_enabled"

        @Volatile
        private var instance: TvPreviewPreferences? = null

        fun get(context: Context): TvPreviewPreferences = instance ?: synchronized(this) {
            instance ?: TvPreviewPreferences(context).also { instance = it }
        }
    }
}
