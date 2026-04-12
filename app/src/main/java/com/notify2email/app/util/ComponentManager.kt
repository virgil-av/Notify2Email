package com.notify2email.app.util

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.notify2email.app.collectors.sms.SmsReceiver

object ComponentManager {
    private const val TAG = "ComponentManager"

    fun setComponentEnabled(context: Context, cls: Class<*>, enabled: Boolean) {
        val componentName = ComponentName(context, cls)
        val state = if (enabled) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }

        context.packageManager.setComponentEnabledSetting(
            componentName,
            state,
            PackageManager.DONT_KILL_APP
        )
        Log.d(TAG, "Component ${cls.simpleName} set to enabled=$enabled")
    }

    fun syncAll(context: Context, serviceRunning: Boolean, smsEnabled: Boolean) {
        // SmsReceiver is only active if the global service is running AND SMS feature is enabled
        setComponentEnabled(context, SmsReceiver::class.java, serviceRunning && smsEnabled)
    }
}
