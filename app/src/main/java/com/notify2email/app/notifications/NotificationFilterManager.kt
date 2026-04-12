package com.notify2email.app.notifications

import android.app.Notification
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.notify2email.app.domain.model.NotificationFilterApp
import com.notify2email.app.domain.model.NotificationFilterMode
import com.notify2email.app.domain.model.NotificationFilterSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class NotificationFilterManager(
    context: Context
) {

    private val appContext = context.applicationContext
    private val packageManager = appContext.packageManager
    private val preferences = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val settingsFlow = MutableStateFlow(readSettings())

    fun observeSettings(): Flow<NotificationFilterSettings> = settingsFlow.asStateFlow()

    fun getSettings(): NotificationFilterSettings = settingsFlow.value

    fun setMode(mode: NotificationFilterMode) {
        preferences.edit()
            .putString(KEY_MODE, mode.name)
            .apply()
        settingsFlow.value = readSettings()
    }

    fun setAppEnabled(packageName: String, enabled: Boolean) {
        preferences.edit()
            .putBoolean(enabledKey(packageName), enabled)
            .apply()
        settingsFlow.value = readSettings()
    }

    fun recordDetectedApp(packageName: String, appName: String) {
        if (packageName.isBlank()) return

        val edit = preferences.edit()
        edit.putString(labelKey(packageName), appName.ifBlank { packageName })
        
        // If this app is not yet known, set its initial state to enabled.
        if (!preferences.contains(enabledKey(packageName))) {
            edit.putBoolean(enabledKey(packageName), true)
        }
        
        edit.apply()
        settingsFlow.value = readSettings()
    }

    fun evaluate(event: NotificationEvent): FilterDecision {
        if (event.isOngoing && event.category == Notification.CATEGORY_SERVICE) {
            return FilterDecision(
                isFiltered = true,
                reason = "foreground service notification"
            )
        }

        val settings = settingsFlow.value
        val explicitValue = if (preferences.contains(enabledKey(event.packageName))) {
            preferences.getBoolean(enabledKey(event.packageName), true)
        } else {
            true
        }

        val enabled = explicitValue
        val shouldFilter = when (settings.mode) {
            NotificationFilterMode.BLACKLIST -> !enabled
            NotificationFilterMode.WHITELIST -> !enabled
        }

        return if (shouldFilter) {
            val reason = when (settings.mode) {
                NotificationFilterMode.BLACKLIST -> if (isSystemApp(event.packageName)) "system app notification" else "blacklisted app"
                NotificationFilterMode.WHITELIST -> "app not in whitelist"
            }
            FilterDecision(true, reason)
        } else {
            FilterDecision(false, null)
        }
    }

    private fun readSettings(): NotificationFilterSettings {
        val mode = runCatching {
            NotificationFilterMode.valueOf(
                preferences.getString(KEY_MODE, NotificationFilterMode.BLACKLIST.name).orEmpty()
            )
        }.getOrDefault(NotificationFilterMode.BLACKLIST)

        val apps = preferences.all.keys
            .filter { it.startsWith(KEY_LABEL_PREFIX) }
            .map { key ->
                val packageName = key.removePrefix(KEY_LABEL_PREFIX)
                val appName = preferences.getString(key, packageName).orEmpty().ifBlank { packageName }
                NotificationFilterApp(
                    packageName = packageName,
                    appName = appName,
                    enabled = preferences.getBoolean(
                        enabledKey(packageName),
                        true
                    ),
                    isSystemApp = isSystemApp(packageName)
                )
            }
            .sortedWith(compareBy<NotificationFilterApp> { it.appName.lowercase() }.thenBy { it.packageName })

        return NotificationFilterSettings(
            mode = mode,
            apps = apps
        )
    }

    private fun defaultEnabledFor(
        packageName: String,
        mode: NotificationFilterMode
    ): Boolean {
        return when (mode) {
            NotificationFilterMode.BLACKLIST -> packageName !in DEFAULT_NOISY_PACKAGES
            NotificationFilterMode.WHITELIST -> false
        }
    }

    private fun isSystemApp(packageName: String): Boolean {
        return runCatching {
            val applicationInfo = packageManager.getApplicationInfo(packageName, 0)
            applicationInfo.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
        }.getOrDefault(false)
    }

    private fun isEssentialSystemApp(packageName: String): Boolean {
        val essentials = setOf(
            "com.google.android.dialer",
            "com.android.phone",
            "com.android.server.telecom",
            "com.google.android.apps.messaging",
            "com.android.messaging",
            "com.samsung.android.messaging",
            "com.samsung.android.dialer",
            "com.google.android.contacts",
            "com.android.contacts"
        )
        return packageName in essentials
    }

    private fun enabledKey(packageName: String): String = "$KEY_ENABLED_PREFIX$packageName"

    private fun labelKey(packageName: String): String = "$KEY_LABEL_PREFIX$packageName"

    companion object {
        private const val PREFS_NAME = "notification_filters"
        private const val KEY_MODE = "filter_mode"
        private const val KEY_LABEL_PREFIX = "label::"
        private const val KEY_ENABLED_PREFIX = "enabled::"

        private val DEFAULT_NOISY_PACKAGES = setOf(
            "com.teamviewer.teamviewer.market.mobile",
            "com.carriez.flutter_hbb"
        )
    }
}

data class FilterDecision(
    val isFiltered: Boolean,
    val reason: String?
)
