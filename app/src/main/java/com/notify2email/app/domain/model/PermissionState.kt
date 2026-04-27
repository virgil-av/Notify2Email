package com.notify2email.app.domain.model

data class PermissionState(
    val smsGranted: Boolean,
    val callLogGranted: Boolean,
    val notificationAccessGranted: Boolean,
    val postNotificationsGranted: Boolean = true,
    val batteryOptimizationIgnored: Boolean = false,
    val contactsGranted: Boolean = false,
    val callScreeningRoleGranted: Boolean = false
)
