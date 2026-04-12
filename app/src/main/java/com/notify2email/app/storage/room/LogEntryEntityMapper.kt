package com.notify2email.app.storage.room

import com.notify2email.app.domain.model.AppLog

fun LogEntryEntity.toDomain(): AppLog {
    return AppLog(
        id = id,
        timestamp = timestamp,
        message = message,
        level = level
    )
}
