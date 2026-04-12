package com.notify2email.app.permissions

import android.app.AlertDialog
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat

class PermissionManager(
    private val activity: ComponentActivity
) {

    private val preferences = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isGranted(appPermission: AppPermission): Boolean {
        if (appPermission == AppPermission.NOTIFICATION_LISTENER) {
            return NotificationAccessChecker.isNotificationListenerEnabled(activity)
        }

        if (!appPermission.isRuntimePermissionRequired()) {
            return true
        }

        val permission = appPermission.permission ?: return false
        return ContextCompat.checkSelfPermission(activity, permission) == PackageManager.PERMISSION_GRANTED
    }

    fun markRequested(appPermission: AppPermission) {
        preferences.edit().putBoolean(requestedKey(appPermission), true).apply()
    }

    fun wasRequestedBefore(appPermission: AppPermission): Boolean {
        return preferences.getBoolean(requestedKey(appPermission), false)
    }

    fun shouldShowRationale(appPermission: AppPermission): Boolean {
        val permission = appPermission.permission ?: return false
        if (!appPermission.isRuntimePermissionRequired()) return false
        return activity.shouldShowRequestPermissionRationale(permission)
    }

    fun isPermanentlyDenied(appPermission: AppPermission): Boolean {
        val permission = appPermission.permission ?: return false
        if (!appPermission.isRuntimePermissionRequired()) return false
        return wasRequestedBefore(appPermission) &&
            !isGranted(appPermission) &&
            !activity.shouldShowRequestPermissionRationale(permission)
    }

    fun showDeniedExplanation(appPermission: AppPermission, onDismiss: (() -> Unit)? = null) {
        AlertDialog.Builder(activity)
            .setTitle(appPermission.title)
            .setMessage(appPermission.rationaleMessage)
            .setPositiveButton(android.R.string.ok) { dialog, _ ->
                dialog.dismiss()
                onDismiss?.invoke()
            }
            .show()
    }

    fun showPermanentlyDeniedDialog(appPermission: AppPermission) {
        AlertDialog.Builder(activity)
            .setTitle(appPermission.title)
            .setMessage("${appPermission.rationaleMessage}\n\nPermission was permanently denied. Open app settings to grant it manually.")
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton("Open settings") { _, _ ->
                PermissionNavigator.openAppSettings(activity)
            }
            .show()
    }

    companion object {
        private const val PREFS_NAME = "permission_manager"

        private fun requestedKey(appPermission: AppPermission): String {
            return "requested_${appPermission.name.lowercase()}"
        }
    }
}

object NotificationAccessChecker {
    fun isNotificationListenerEnabled(context: Context): Boolean {
        val enabledListeners = android.provider.Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        ).orEmpty()
        return enabledListeners.contains(context.packageName)
    }
}
