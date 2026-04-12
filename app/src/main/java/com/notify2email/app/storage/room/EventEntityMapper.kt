package com.notify2email.app.storage.room

import com.notify2email.app.domain.model.Event
import com.notify2email.app.domain.model.EventType
import com.notify2email.app.domain.model.SentStatus

fun EventEntity.toDomain(): Event {
    return Event(
        id = id,
        type = EventType.valueOf(type),
        source = source,
        content = content,
        timestamp = timestamp,
        sentStatus = SentStatus.valueOf(sentStatus),
        errorMessage = errorMessage
    )
}

fun Event.toEntity(): EventEntity {
    return EventEntity(
        id = id,
        type = type.name,
        source = source,
        content = content,
        timestamp = timestamp,
        sentStatus = sentStatus.name,
        errorMessage = errorMessage
    )
}
