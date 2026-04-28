package com.notify2email.app.permissions

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

import android.app.role.RoleManager
import android.os.Build
import android.util.Log

object PermissionNavigator {
    private const val TAG = "PermissionNavigator"

    fun openAppSettings(context: Context) {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null)
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun openNotificationListenerSettings(context: Context) {
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun requestCallScreeningRole(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(Context.ROLE_SERVICE) as RoleManager
            val isAvailable = roleManager.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)
            val isHeld = roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
            
            Log.d(TAG, "Requesting Call Screening Role: available=$isAvailable, held=$isHeld")
            
            if (isAvailable && !isHeld) {
                val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING)
                context.startActivity(intent)
            } else {
                Log.w(TAG, "Call Screening Role not requested: available=$isAvailable, held=$isHeld")
            }
        }
    }
}
