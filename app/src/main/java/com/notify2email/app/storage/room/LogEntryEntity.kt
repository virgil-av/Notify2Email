package com.notify2email.app.storage.room

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.notify2email.app.domain.model.LogLevel

@Entity(tableName = "logs")
data class LogEntryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val timestamp: Long,
    val message: String,
    val level: LogLevel = LogLevel.INFO
)
