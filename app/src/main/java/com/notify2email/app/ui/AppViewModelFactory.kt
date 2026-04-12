package com.notify2email.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.notify2email.app.di.AppContainer
import com.notify2email.app.ui.dashboard.DashboardViewModel
import com.notify2email.app.ui.events.EventsViewModel
import com.notify2email.app.ui.logs.LogsViewModel
import com.notify2email.app.ui.permissions.PermissionsViewModel
import com.notify2email.app.ui.settings.SettingsViewModel

class AppViewModelFactory(
    private val appContainer: AppContainer
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(DashboardViewModel::class.java) -> {
                DashboardViewModel(
                    eventRepository = appContainer.eventRepository,
                    serviceStateRepository = appContainer.serviceStateRepository,
                    settingsRepository = appContainer.settingsRepository,
                    permissionRepository = appContainer.permissionRepository
                ) as T
            }

            modelClass.isAssignableFrom(EventsViewModel::class.java) -> {
                EventsViewModel(
                    eventRepository = appContainer.eventRepository
                ) as T
            }

            modelClass.isAssignableFrom(SettingsViewModel::class.java) -> {
                SettingsViewModel(
                    settingsRepository = appContainer.settingsRepository,
                    serviceStateRepository = appContainer.serviceStateRepository,
                    notificationFilterManager = appContainer.notificationFilterManager
                ) as T
            }

            modelClass.isAssignableFrom(PermissionsViewModel::class.java) -> {
                PermissionsViewModel(
                    permissionRepository = appContainer.permissionRepository
                ) as T
            }

            modelClass.isAssignableFrom(LogsViewModel::class.java) -> {
                LogsViewModel(
                    logRepository = appContainer.logRepository
                ) as T
            }

            else -> error("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
