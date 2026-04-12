package com.notify2email.app.domain.repository

import com.notify2email.app.domain.model.ServiceState
import kotlinx.coroutines.flow.Flow

interface ServiceStateRepository {
    fun isServiceRunning(): Boolean
    fun observeServiceState(): Flow<ServiceState>
    suspend fun getServiceState(): ServiceState
    suspend fun startService()
    suspend fun stopService()
    suspend fun setServiceRunning(isRunning: Boolean)
}
