package com.jing.sakura.home

import android.content.Context

internal class FirstLaunchSupportStore(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun shouldShow(): Boolean {
        val packageInfo = runCatching {
            appContext.packageManager.getPackageInfo(appContext.packageName, 0)
        }.getOrNull() ?: return false
        return shouldShowFirstLaunchSupportNotice(
            alreadySeen = preferences.getBoolean(KEY_SEEN, false),
            firstInstallTime = packageInfo.firstInstallTime,
            lastUpdateTime = packageInfo.lastUpdateTime
        )
    }

    fun markSeen() {
        preferences.edit().putBoolean(KEY_SEEN, true).apply()
    }

    private companion object {
        const val PREFERENCES = "first_launch_support_notice"
        const val KEY_SEEN = "seen"
    }
}

internal fun shouldShowFirstLaunchSupportNotice(
    alreadySeen: Boolean,
    firstInstallTime: Long,
    lastUpdateTime: Long
): Boolean = !alreadySeen &&
    firstInstallTime > 0L &&
    lastUpdateTime > 0L &&
    firstInstallTime == lastUpdateTime
