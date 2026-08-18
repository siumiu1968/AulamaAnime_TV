package com.jing.sakura.update

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class TvUpdateChannel(val storageValue: String) {
    Stable("stable"),
    Preview("preview");

    companion object {
        fun fromStorageValue(value: String?): TvUpdateChannel =
            entries.firstOrNull { it.storageValue == value } ?: Stable
    }
}

class TvUpdateChannelPreferences private constructor(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )
    private val _channel = MutableStateFlow(
        TvUpdateChannel.fromStorageValue(preferences.getString(KEY_UPDATE_CHANNEL, null))
    )
    val channel: StateFlow<TvUpdateChannel> = _channel

    fun setChannel(channel: TvUpdateChannel) {
        if (_channel.value == channel) return
        preferences.edit().putString(KEY_UPDATE_CHANNEL, channel.storageValue).apply()
        _channel.value = channel
    }

    companion object {
        private const val PREFERENCES_NAME = "aulama_tv_preferences"
        private const val KEY_UPDATE_CHANNEL = "update_channel"

        @Volatile
        private var instance: TvUpdateChannelPreferences? = null

        fun get(context: Context): TvUpdateChannelPreferences = instance ?: synchronized(this) {
            instance ?: TvUpdateChannelPreferences(context).also { instance = it }
        }
    }
}
