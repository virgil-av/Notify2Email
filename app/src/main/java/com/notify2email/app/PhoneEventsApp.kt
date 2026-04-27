package com.notify2email.app

import android.app.Application
import androidx.work.Configuration
import com.notify2email.app.di.AppContainer
import com.notify2email.app.work.HealthReportWorker
import com.notify2email.app.work.WorkScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class PhoneEventsApp : Application(), Configuration.Provider {
    lateinit var appContainer: AppContainer
        private set

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        appContainer = AppContainer(this)
        appContainer.eventBatchQueueManager.start()

        applicationScope.launch {
            val settings = appContainer.settingsRepository.getSettings()
            WorkScheduler.scheduleHealthReport(
                context = this@PhoneEventsApp,
                intervalHours = settings.healthReportIntervalHours,
                startTime = settings.healthReportStartTime,
                enabled = settings.healthReportEnabled
            )
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()
}
