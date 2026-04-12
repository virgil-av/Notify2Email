package com.notify2email.app.domain.model

data class ServiceState(
    val isRunning: Boolean,
    val lastUpdatedMillis: Long
)
