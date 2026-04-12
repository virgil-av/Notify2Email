package com.notify2email.app.data

import com.notify2email.app.domain.model.AppLog
import com.notify2email.app.domain.model.LogLevel
import com.notify2email.app.domain.repository.LogRepository
import com.notify2email.app.storage.room.LogEntryDao
import com.notify2email.app.storage.room.LogEntryEntity
import com.notify2email.app.storage.room.toDomain
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomLogRepository(
    private val logEntryDao: LogEntryDao
) : LogRepository {

    override fun observeLogs(): Flow<List<AppLog>> {
        return logEntryDao.observeAll().map { entities -> entities.map { it.toDomain() } }
    }

    override suspend fun addLog(message: String, level: LogLevel, timestamp: Long) {
        if (message.isBlank()) return
        logEntryDao.insert(
            LogEntryEntity(
                timestamp = timestamp,
                message = message,
                level = level
            )
        )
    }

    override suspend fun clearLogs() {
        logEntryDao.clearAll()
    }
}
