package com.notify2email.app.domain.repository

import com.notify2email.app.domain.model.AppLog
import com.notify2email.app.domain.model.LogLevel
import kotlinx.coroutines.flow.Flow

interface LogRepository {
    fun observeLogs(): Flow<List<AppLog>>
    suspend fun addLog(
        message: String,
        level: LogLevel = LogLevel.INFO,
        timestamp: Long = System.currentTimeMillis()
    )
    suspend fun clearLogs()
}
