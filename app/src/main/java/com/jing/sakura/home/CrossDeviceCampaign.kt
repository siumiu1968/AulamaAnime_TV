package com.jing.sakura.home

import android.content.Context
import android.content.SharedPreferences

internal object CrossDeviceCampaign {
    const val ID = "cross-device-20261019"
    const val WEB_URL = "https://aulama.org/anime/"
    const val ANDROID_DOWNLOAD_URL = "https://aulama.org/anime/downloads/aulama-anime.apk"
}

internal class CrossDeviceCampaignStore(private val preferences: SharedPreferences) {
    constructor(context: Context) : this(
        context.applicationContext.getSharedPreferences("cross_device_campaigns", Context.MODE_PRIVATE)
    )

    fun isDismissed(campaignId: String = CrossDeviceCampaign.ID): Boolean =
        preferences.getBoolean("dismissed:$campaignId", false)

    fun dismiss(campaignId: String = CrossDeviceCampaign.ID) {
        preferences.edit().putBoolean("dismissed:$campaignId", true).apply()
    }
}

internal fun shouldShowCrossDeviceCampaign(
    dismissed: Boolean,
    homeVisible: Boolean,
    startupStage: HomeStartupStage,
    blockingOverlay: Boolean
): Boolean = !dismissed && homeVisible &&
    startupStage == HomeStartupStage.Ready && !blockingOverlay
