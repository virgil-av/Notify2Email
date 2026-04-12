package com.notify2email.app.permissions

import android.Manifest
import android.os.Build

enum class AppPermission(
    val permission: String?,
    val title: String,
    val rationaleMessage: String
) {
    READ_SMS(
        permission = Manifest.permission.RECEIVE_SMS,
        title = "SMS permission",
        rationaleMessage = "SMS access is needed so the app can detect incoming messages and forward them by email."
    ),
    READ_CALL_LOG(
        permission = Manifest.permission.READ_CALL_LOG,
        title = "Call log permission",
        rationaleMessage = "Call log access is needed so the app can read recent calls and send them by email."
    ),
    POST_NOTIFICATIONS(
        permission = Manifest.permission.POST_NOTIFICATIONS,
        title = "Notifications permission",
        rationaleMessage = "Notifications permission is needed so the foreground service can show its required status notification."
    ),
    NOTIFICATION_LISTENER(
        permission = null,
        title = "Notification access",
        rationaleMessage = "Notification access is needed so the app can capture posted notifications and forward them by email."
    );

    fun isRuntimePermissionRequired(): Boolean {
        return when (this) {
            READ_SMS, READ_CALL_LOG -> true
            POST_NOTIFICATIONS -> Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
            NOTIFICATION_LISTENER -> false
        }
    }
}
