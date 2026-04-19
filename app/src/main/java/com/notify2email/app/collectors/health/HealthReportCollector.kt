package com.notify2email.app.collectors.health

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.PowerManager
import android.service.notification.StatusBarNotification
import com.notify2email.app.notifications.PhoneNotificationListenerService

class HealthReportCollector(private val context: Context) {

    fun getBatteryLevel(): Int {
        val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { filter ->
            context.registerReceiver(null, filter)
        }
        val level: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        return if (level >= 0 && scale > 0) (level * 100 / scale) else -1
    }

    fun isBatteryCharging(): Boolean {
        val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { filter ->
            context.registerReceiver(null, filter)
        }
        val status: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        return status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
    }

    fun isScreenOn(): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.isInteractive
    }

    fun getActiveNotificationsCount(): Int {
        return try {
            PhoneNotificationListenerService.getActiveNotifications(context)?.size ?: 0
        } catch (e: Exception) {
            -1
        }
    }

    fun getActiveNotificationSummaries(): List<String> {
        return try {
            val notifications = PhoneNotificationListenerService.getActiveNotifications(context) ?: emptyArray()
            notifications.mapNotNull { sbn ->
                val packageName = sbn.packageName
                val title = sbn.notification.extras.getCharSequence("android.title")?.toString() ?: ""
                if (title.isNotBlank()) "$packageName: $title" else packageName
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
