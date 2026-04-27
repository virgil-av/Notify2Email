package com.notify2email.app.work

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import java.time.LocalDateTime
import java.time.Duration
import java.time.LocalTime

object WorkScheduler {

    fun scheduleHealthReport(context: Context, intervalHours: Int, startTime: String, enabled: Boolean) {
        val workManager = WorkManager.getInstance(context)
        
        if (!enabled) {
            workManager.cancelUniqueWork(HealthReportWorker.WORK_NAME)
            return
        }

        val initialDelay = calculateInitialDelay(startTime)

        val workRequest = PeriodicWorkRequestBuilder<HealthReportWorker>(
            intervalHours.toLong(), TimeUnit.HOURS
        )
            .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
            .build()

        workManager.enqueueUniquePeriodicWork(
            HealthReportWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            workRequest
        )
    }

    private fun calculateInitialDelay(startTime: String): Long {
        val parts = startTime.split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull() ?: 9
        val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0

        val now = LocalDateTime.now()
        var target = now.with(LocalTime.of(hour, minute, 0, 0))

        if (target.isBefore(now)) {
            target = target.plusDays(1)
        }

        return Duration.between(now, target).toMillis()
    }
}
