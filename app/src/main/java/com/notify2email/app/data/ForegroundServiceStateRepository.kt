package com.notify2email.app.data

import android.content.Context
import com.notify2email.app.domain.model.ServiceState
import com.notify2email.app.domain.repository.ServiceStateRepository
import com.notify2email.app.services.EventForegroundService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class ForegroundServiceStateRepository(
    context: Context
) : ServiceStateRepository {

    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val stateFlow = MutableStateFlow(readState())

    override fun isServiceRunning(): Boolean = preferences.getBoolean(KEY_SERVICE_RUNNING, false)

    override fun observeServiceState(): Flow<ServiceState> = stateFlow.asStateFlow()

    override suspend fun getServiceState(): ServiceState = stateFlow.value

    override suspend fun startService() {
        EventForegroundService.start(appContext)
        updateState(isRunning = true)
    }

    override suspend fun stopService() {
        EventForegroundService.stop(appContext)
        updateState(isRunning = false)
    }

    override suspend fun setServiceRunning(isRunning: Boolean) {
        updateState(isRunning = isRunning)
    }

    private fun updateState(isRunning: Boolean) {
        val updated = ServiceState(
            isRunning = isRunning,
            lastUpdatedMillis = System.currentTimeMillis()
        )
        preferences.edit()
            .putBoolean(KEY_SERVICE_RUNNING, updated.isRunning)
            .putLong(KEY_LAST_UPDATED, updated.lastUpdatedMillis)
            .apply()
        stateFlow.value = updated
    }

    private fun readState(): ServiceState {
        return ServiceState(
            isRunning = preferences.getBoolean(KEY_SERVICE_RUNNING, false),
            lastUpdatedMillis = preferences.getLong(KEY_LAST_UPDATED, 0L)
        )
    }

    companion object {
        private const val PREFS_NAME = "service_state"
        private const val KEY_SERVICE_RUNNING = "service_running"
        private const val KEY_LAST_UPDATED = "service_last_updated"
    }
}
