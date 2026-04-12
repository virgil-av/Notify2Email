package com.notify2email.app

import android.app.Application
import com.notify2email.app.di.AppContainer

class PhoneEventsApp : Application() {
    lateinit var appContainer: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        appContainer = AppContainer(this)
        appContainer.eventBatchQueueManager.start()
    }
}
