package com.notify2email.app.power

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

object BatteryOptimizationManager {

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true
        }

        val powerManager = context.getSystemService(PowerManager::class.java) ?: return false
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun createIgnoreBatteryOptimizationsIntent(context: Context): Intent {
        return Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
    }
}
