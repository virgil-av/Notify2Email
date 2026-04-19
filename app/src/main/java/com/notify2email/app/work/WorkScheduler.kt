package com.notify2email.app.work

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object WorkScheduler {

    fun scheduleHealthReport(context: Context, intervalHours: Int, enabled: Boolean) {
        val workManager = WorkManager.getInstance(context)
        
        if (!enabled) {
            workManager.cancelUniqueWork(HealthReportWorker.WORK_NAME)
            return
        }

        val workRequest = PeriodicWorkRequestBuilder<HealthReportWorker>(
            intervalHours.toLong(), TimeUnit.HOURS
        ).build()

        workManager.enqueueUniquePeriodicWork(
            HealthReportWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            workRequest
        )
    }
}
