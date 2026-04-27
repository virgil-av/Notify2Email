package com.notify2email.app.data

import android.content.Context
import com.notify2email.app.domain.model.AppTheme
import com.notify2email.app.domain.model.DeliveryResult
import com.notify2email.app.domain.model.SmtpSettings
import com.notify2email.app.domain.repository.SettingsRepository
import com.notify2email.app.email.SendEmailResult
import com.notify2email.app.email.SmtpConfigProvider
import com.notify2email.app.email.SmtpProviderRules
import com.notify2email.app.email.SmtpTestHelper
import com.notify2email.app.email.TlsMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asStateFlow

class SharedPreferencesSettingsRepository(
    context: Context,
    private val smtpTestHelper: SmtpTestHelper = SmtpTestHelper()
) : SettingsRepository {

    private val preferences = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val settingsFlow = MutableStateFlow(readSettings())

    override fun observeSettings(): Flow<SmtpSettings> = settingsFlow.asStateFlow()

    override suspend fun getSettings(): SmtpSettings = settingsFlow.value

    override suspend fun saveSettings(settings: SmtpSettings) {
        val normalizedSettings = SmtpProviderRules.normalizeSettings(settings)
        preferences.edit()
            .putString(KEY_SMTP_HOST, normalizedSettings.host)
            .putInt(KEY_SMTP_PORT, normalizedSettings.port)
            .putString(KEY_TLS_MODE, normalizedSettings.encryption.name)
            .putString(KEY_SMTP_USERNAME, normalizedSettings.username)
            .putString(KEY_PASSWORD, normalizedSettings.password)
            .putString(KEY_FROM_ADDRESS, normalizedSettings.fromEmail)
            .putString(KEY_TO_ADDRESS_PRIMARY, normalizedSettings.toEmailPrimary)
            .putString(KEY_CC_ADDRESSES, normalizedSettings.ccEmails.joinToString(","))
            .putString(KEY_BCC_ADDRESSES, normalizedSettings.bccEmails.joinToString(","))
            .putString(KEY_SUBJECT_PATTERN, normalizedSettings.subjectPattern)
            .putBoolean(SmtpConfigProvider.KEY_EMAIL_ENABLED, normalizedSettings.enabled)
            .putBoolean(SmtpConfigProvider.KEY_NOTIFICATIONS_ENABLED, normalizedSettings.notificationsEnabled)
            .putBoolean(SmtpConfigProvider.KEY_SMS_ENABLED, normalizedSettings.smsEnabled)
            .putBoolean(SmtpConfigProvider.KEY_CALL_LOGS_ENABLED, normalizedSettings.callsEnabled)
            .putString(KEY_APP_THEME, normalizedSettings.appTheme.name)
            .putBoolean(KEY_HEALTH_REPORT_ENABLED, normalizedSettings.healthReportEnabled)
            .putInt(KEY_HEALTH_REPORT_INTERVAL, normalizedSettings.healthReportIntervalHours)
            .putString(KEY_HEALTH_REPORT_START_TIME, normalizedSettings.healthReportStartTime)
            .putBoolean(KEY_CALL_FAILSAFE_ENABLED, normalizedSettings.callDetectionFailsafeEnabled)
            .putInt(KEY_BATCH_DELAY, normalizedSettings.batchDelaySeconds)
            .apply()

        settingsFlow.value = readSettings()
    }

    override suspend fun testSmtp(settings: SmtpSettings): DeliveryResult {
        return when (val result = smtpTestHelper.sendTestEmail(settings)) {
            is SendEmailResult.Success -> DeliveryResult.Success(result.attempts)
            is SendEmailResult.TransientFailure -> DeliveryResult.Failure(result.error, result.attempts)
            is SendEmailResult.PermanentFailure -> DeliveryResult.Failure(result.error, result.attempts)
            is SendEmailResult.RetryExhausted -> DeliveryResult.Failure(result.error, result.attempts)
        }
    }

    private fun readSettings(): SmtpSettings {
        return SmtpSettings(
            host = preferences.getString(KEY_SMTP_HOST, "").orEmpty(),
            port = preferences.getInt(KEY_SMTP_PORT, DEFAULT_SMTP_PORT),
            encryption = runCatching {
                TlsMode.valueOf(
                    preferences.getString(KEY_TLS_MODE, TlsMode.STARTTLS.name).orEmpty()
                )
            }.getOrDefault(TlsMode.STARTTLS),
            username = preferences.getString(KEY_SMTP_USERNAME, "").orEmpty(),
            password = preferences.getString(KEY_PASSWORD, "").orEmpty(),
            fromEmail = preferences.getString(KEY_FROM_ADDRESS, "").orEmpty(),
            toEmailPrimary = preferences.getString(KEY_TO_ADDRESS_PRIMARY, "").orEmpty(),
            ccEmails = preferences.getString(KEY_CC_ADDRESSES, "")
                .orEmpty()
                .split(",")
                .map(String::trim)
                .filter(String::isNotEmpty)
                .distinct(),
            bccEmails = preferences.getString(KEY_BCC_ADDRESSES, "")
                .orEmpty()
                .split(",")
                .map(String::trim)
                .filter(String::isNotEmpty)
                .distinct(),
            subjectPattern = preferences.getString(KEY_SUBJECT_PATTERN, "").orEmpty(),
            enabled = preferences.getBoolean(SmtpConfigProvider.KEY_EMAIL_ENABLED, true),
            smsEnabled = preferences.getBoolean(SmtpConfigProvider.KEY_SMS_ENABLED, true),
            callsEnabled = preferences.getBoolean(SmtpConfigProvider.KEY_CALL_LOGS_ENABLED, true),
            notificationsEnabled = preferences.getBoolean(SmtpConfigProvider.KEY_NOTIFICATIONS_ENABLED, true),
            appTheme = runCatching {
                AppTheme.valueOf(
                    preferences.getString(KEY_APP_THEME, AppTheme.LIGHT.name).orEmpty()
                )
            }.getOrDefault(AppTheme.LIGHT),
            healthReportEnabled = preferences.getBoolean(KEY_HEALTH_REPORT_ENABLED, false),
            healthReportIntervalHours = preferences.getInt(KEY_HEALTH_REPORT_INTERVAL, 1),
            healthReportStartTime = preferences.getString(KEY_HEALTH_REPORT_START_TIME, "09:00") ?: "09:00",
            callDetectionFailsafeEnabled = preferences.getBoolean(KEY_CALL_FAILSAFE_ENABLED, false),
            batchDelaySeconds = preferences.getInt(KEY_BATCH_DELAY, 30)
        )
    }

    companion object {
        private const val PREFS_NAME = "smtp_settings"
        private const val KEY_SMTP_HOST = "smtp_host"
        private const val KEY_SMTP_PORT = "smtp_port"
        private const val KEY_SMTP_USERNAME = "smtp_username"
        private const val KEY_FROM_ADDRESS = "smtp_from_address"
        private const val KEY_TO_ADDRESS_PRIMARY = "smtp_to_address_primary"
        private const val KEY_CC_ADDRESSES = "smtp_cc_addresses"
        private const val KEY_BCC_ADDRESSES = "smtp_bcc_addresses"
        private const val KEY_TLS_MODE = "smtp_tls_mode"
        private const val KEY_PASSWORD = "smtp_password"
        private const val KEY_SUBJECT_PATTERN = "smtp_subject_pattern"
        private const val KEY_APP_THEME = "app_theme"
        private const val KEY_HEALTH_REPORT_ENABLED = "health_report_enabled"
        private const val KEY_HEALTH_REPORT_INTERVAL = "health_report_interval"
        private const val KEY_HEALTH_REPORT_START_TIME = "health_report_start_time"
        private const val KEY_CALL_FAILSAFE_ENABLED = "call_failsafe_enabled"
        private const val KEY_BATCH_DELAY = "batch_delay_seconds"
        private const val DEFAULT_SMTP_PORT = 587
    }
}
