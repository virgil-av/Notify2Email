package com.notify2email.app.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                if (!BootStartWorker.shouldStartService(context.applicationContext)) {
                    return
                }

                val bootStartWork = OneTimeWorkRequestBuilder<BootStartWorker>()
                    .setInitialDelay(12, TimeUnit.SECONDS)
                    .build()

                WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                    UNIQUE_BOOT_START_WORK,
                    ExistingWorkPolicy.REPLACE,
                    bootStartWork
                )
            }
        }
    }

    companion object {
        private const val UNIQUE_BOOT_START_WORK = "boot_start_foreground_service"
    }
}
