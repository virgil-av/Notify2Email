package com.notify2email.app.domain.model

import com.notify2email.app.email.TlsMode

data class SmtpSettings(
    val host: String,
    val port: Int,
    val encryption: TlsMode,
    val username: String,
    val password: String,
    val fromEmail: String,
    val toEmailPrimary: String,
    val ccEmails: List<String> = emptyList(),
    val bccEmails: List<String> = emptyList(),
    val subjectPattern: String = "", // New field for custom subject
    val enabled: Boolean,
    val smsEnabled: Boolean = true,
    val callsEnabled: Boolean = true,
    val notificationsEnabled: Boolean = true,
    val appTheme: AppTheme = AppTheme.LIGHT
)
