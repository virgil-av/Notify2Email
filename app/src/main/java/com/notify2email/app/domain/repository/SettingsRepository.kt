package com.notify2email.app.domain.repository

import com.notify2email.app.domain.model.DeliveryResult
import com.notify2email.app.domain.model.SmtpSettings
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    fun observeSettings(): Flow<SmtpSettings>
    suspend fun getSettings(): SmtpSettings
    suspend fun saveSettings(settings: SmtpSettings)
    suspend fun testSmtp(settings: SmtpSettings): DeliveryResult
}
