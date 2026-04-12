package com.notify2email.app.domain.model

data class Event(
    val id: String,
    val type: EventType,
    val source: String,
    val content: String,
    val timestamp: Long,
    val sentStatus: SentStatus,
    val errorMessage: String? = null
)

enum class EventType {
    SMS,
    CALL,
    NOTIFICATION
}

enum class SentStatus {
    SENT,
    FAILED,
    PENDING,
    WAITING_IN_QUEUE,
    SMTP_NOT_CONFIGURED,
    SKIPPED
}
