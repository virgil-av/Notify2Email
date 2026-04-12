package com.notify2email.app.receivers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.notify2email.app.di.appContainer
import com.notify2email.app.services.EventForegroundService

class BootStartWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        if (!shouldStartService(applicationContext)) {
            applicationContext.appContainer.logRepository.addLog(
                "BootStartWorker: skipped service restart because service was not previously enabled."
            )
            return Result.success()
        }

        return runCatching {
            EventForegroundService.start(applicationContext)
            applicationContext.appContainer.logRepository.addLog(
                "BootStartWorker: requested delayed foreground service start after reboot."
            )
            Result.success()
        }.getOrElse { error ->
            applicationContext.appContainer.logRepository.addLog(
                "BootStartWorker: failed to request service start: ${error.message ?: error.javaClass.simpleName}"
            )
            Result.retry()
        }
    }

    companion object {
        fun shouldStartService(context: Context): Boolean {
            val preferences = context.applicationContext.getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE
            )
            return preferences.getBoolean(KEY_SERVICE_RUNNING, false)
        }

        private const val PREFS_NAME = "service_state"
        private const val KEY_SERVICE_RUNNING = "service_running"
    }
}
