package com.notify2email.app.domain.model

data class AppLog(
    val id: Long,
    val timestamp: Long,
    val message: String,
    val level: LogLevel = LogLevel.INFO
)

enum class LogLevel {
    DEBUG, INFO, WARN, ERROR
}
