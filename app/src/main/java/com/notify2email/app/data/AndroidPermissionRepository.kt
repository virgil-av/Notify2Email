package com.notify2email.app.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.notify2email.app.domain.model.PermissionState
import com.notify2email.app.domain.repository.PermissionRepository
import com.notify2email.app.permissions.NotificationAccessChecker
import com.notify2email.app.power.BatteryOptimizationManager

class AndroidPermissionRepository(
    context: Context
) : PermissionRepository {

    private val appContext = context.applicationContext

    override suspend fun getPermissionState(): PermissionState {
        return PermissionState(
            smsGranted = hasPermission(Manifest.permission.RECEIVE_SMS),
            callLogGranted = hasPermission(Manifest.permission.READ_CALL_LOG),
            notificationAccessGranted = NotificationAccessChecker.isNotificationListenerEnabled(appContext),
            postNotificationsGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                hasPermission(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                true
            },
            batteryOptimizationIgnored = BatteryOptimizationManager.isIgnoringBatteryOptimizations(appContext)
        )
    }

    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(appContext, permission) == PackageManager.PERMISSION_GRANTED
    }
}
