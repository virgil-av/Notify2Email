package com.notify2email.app.di

import android.content.Context
import com.notify2email.app.PhoneEventsApp
import com.notify2email.app.data.AndroidPermissionRepository
import com.notify2email.app.data.ForegroundServiceStateRepository
import com.notify2email.app.data.RoomEventRepository
import com.notify2email.app.data.RoomLogRepository
import com.notify2email.app.data.SharedPreferencesSettingsRepository
import com.notify2email.app.domain.repository.EventRepository
import com.notify2email.app.domain.repository.LogRepository
import com.notify2email.app.domain.repository.PermissionRepository
import com.notify2email.app.domain.repository.ServiceStateRepository
import com.notify2email.app.domain.repository.SettingsRepository
import com.notify2email.app.email.EventBatchQueueManager
import com.notify2email.app.email.SharedPreferencesSmtpConfigProvider
import com.notify2email.app.email.SmtpEmailSender
import com.notify2email.app.notifications.NotificationFilterManager
import com.notify2email.app.storage.room.AppDatabase
import com.notify2email.app.util.ContactNameResolver
import com.notify2email.app.util.SimSlotResolver

class AppContainer(
    context: Context
) {
    val appContext = context.applicationContext
    private val database by lazy { AppDatabase.getInstance(appContext) }

    val emailSender by lazy { SmtpEmailSender() }
    val smtpConfigProvider by lazy { SharedPreferencesSmtpConfigProvider(appContext) }
    val notificationFilterManager by lazy { NotificationFilterManager(appContext) }

    val eventRepository: EventRepository by lazy {
        RoomEventRepository(database.eventDao(), database.queuedEventDao())
    }

    val logRepository: LogRepository by lazy {
        RoomLogRepository(database.logEntryDao())
    }

    val eventBatchQueueManager by lazy {
        EventBatchQueueManager(
            emailSender = emailSender,
            configProvider = smtpConfigProvider,
            queueDao = database.queuedEventDao(),
            eventRepository = eventRepository,
            logRepository = logRepository
        )
    }

    val settingsRepository: SettingsRepository by lazy {
        SharedPreferencesSettingsRepository(appContext)
    }

    val serviceStateRepository: ServiceStateRepository by lazy {
        ForegroundServiceStateRepository(appContext)
    }

    val permissionRepository: PermissionRepository by lazy {
        AndroidPermissionRepository(appContext)
    }

    val contactNameResolver by lazy { ContactNameResolver(appContext) }
    val simSlotResolver by lazy { SimSlotResolver(appContext) }
}

val Context.appContainer: AppContainer
    get() = (applicationContext as PhoneEventsApp).appContainer
