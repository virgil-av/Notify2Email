package com.notify2email.app.domain.model

enum class NotificationFilterMode {
    BLACKLIST,
    WHITELIST
}

data class NotificationFilterApp(
    val packageName: String,
    val appName: String,
    val enabled: Boolean,
    val isSystemApp: Boolean = false
)

data class NotificationFilterSettings(
    val mode: NotificationFilterMode = NotificationFilterMode.BLACKLIST,
    val apps: List<NotificationFilterApp> = emptyList()
)
