package com.notify2email.app.domain.model

data class AppState(
    val serviceRunning: Boolean,
    val smsSentCount: Int,
    val callsSentCount: Int,
    val notificationsSentCount: Int
)
