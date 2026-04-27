package com.notify2email.app.email

import android.content.Context
import android.content.SharedPreferences

interface SmtpConfigProvider {
    fun getEmailSettings(enabledKey: String = KEY_EMAIL_ENABLED): EmailSettings?
    fun isFeatureEnabled(key: String): Boolean
    fun isSmtpConfigured(): Boolean
    fun getBatchDelaySeconds(): Int
    fun isBatchDelayEnabled(): Boolean

    companion object {
        const val KEY_EMAIL_ENABLED = "email_enabled"
        const val KEY_NOTIFICATIONS_ENABLED = "notifications_email_enabled"
        const val KEY_SMS_ENABLED = "sms_email_enabled"
        const val KEY_CALL_LOGS_ENABLED = "call_logs_email_enabled"
    }
}

data class EmailSettings(
    val smtpConfig: SmtpConfig,
    val fromAddress: String,
    val toAddressPrimary: String,
    val ccAddresses: List<String>,
    val bccAddresses: List<String>,
    val subjectPattern: String
)

class SharedPreferencesSmtpConfigProvider(
    context: Context
) : SmtpConfigProvider {

    val context: Context = context.applicationContext

    private val preferences: SharedPreferences =
        this.context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun isFeatureEnabled(key: String): Boolean {
        val globallyEnabled = preferences.getBoolean(SmtpConfigProvider.KEY_EMAIL_ENABLED, true)
        val featureEnabled = preferences.getBoolean(key, true)
        return globallyEnabled && featureEnabled
    }

    override fun isSmtpConfigured(): Boolean {
        val host = preferences.getString(KEY_HOST, null)?.trim().orEmpty()
        val username = preferences.getString(KEY_USERNAME, null)?.trim().orEmpty()
        val password = preferences.getString(KEY_PASSWORD, null)?.trim().orEmpty()
        val fromAddress = preferences.getString(KEY_FROM_ADDRESS, null)?.trim().orEmpty()
        val toAddressPrimary = preferences.getString(KEY_TO_ADDRESS_PRIMARY, null)?.trim().orEmpty()

        return host.isNotBlank() &&
                username.isNotBlank() &&
                password.isNotBlank() &&
                fromAddress.isNotBlank() &&
                toAddressPrimary.isNotBlank()
    }

    override fun getEmailSettings(enabledKey: String): EmailSettings? {
        val host = preferences.getString(KEY_HOST, null)?.trim().orEmpty()
        val port = preferences.getInt(KEY_PORT, DEFAULT_PORT)
        val tlsModeValue = preferences.getString(KEY_TLS_MODE, TlsMode.STARTTLS.name).orEmpty()
        val username = preferences.getString(KEY_USERNAME, null)?.trim().orEmpty()
        val password = preferences.getString(KEY_PASSWORD, null)?.trim().orEmpty()
        val fromAddress = preferences.getString(KEY_FROM_ADDRESS, null)?.trim().orEmpty()
        val toAddressPrimary = preferences.getString(KEY_TO_ADDRESS_PRIMARY, null)?.trim().orEmpty()
        val ccAddressesString = preferences.getString(KEY_CC_ADDRESSES, "").orEmpty()
        val bccAddressesString = preferences.getString(KEY_BCC_ADDRESSES, "").orEmpty()
        val globallyEnabled = preferences.getBoolean(SmtpConfigProvider.KEY_EMAIL_ENABLED, true)
        val sendingEnabled = preferences.getBoolean(enabledKey, true)

        val ccAddresses = ccAddressesString.split(",")
            .map(String::trim)
            .filter(String::isNotEmpty)
            .distinct()

        val bccAddresses = bccAddressesString.split(",")
            .map(String::trim)
            .filter(String::isNotEmpty)
            .distinct()
        if (!globallyEnabled ||
            !sendingEnabled ||
            host.isBlank() ||
            username.isBlank() ||
            password.isBlank() ||
            fromAddress.isBlank() ||
            toAddressPrimary.isBlank()
        ) {
            return null
        }

        val tlsMode = runCatching {
            TlsMode.valueOf(tlsModeValue)
        }.getOrDefault(TlsMode.STARTTLS)
        val normalizedConfig = SmtpProviderRules.normalizeConfig(
            SmtpConfig(
                host = host,
                port = port,
                tlsMode = tlsMode,
                username = username,
                password = password
            )
        )

        val subjectPattern = preferences.getString(KEY_SUBJECT_PATTERN, "").orEmpty()

        return EmailSettings(
            smtpConfig = normalizedConfig,
            fromAddress = fromAddress,
            toAddressPrimary = toAddressPrimary,
            ccAddresses = ccAddresses,
            bccAddresses = bccAddresses,
            subjectPattern = subjectPattern
        )
    }

    override fun getBatchDelaySeconds(): Int {
        return preferences.getInt(KEY_BATCH_DELAY, 30)
    }

    override fun isBatchDelayEnabled(): Boolean {
        return preferences.getBoolean(KEY_BATCH_DELAY_ENABLED, false)
    }

    companion object {
        private const val PREFS_NAME = "smtp_settings"
        private const val KEY_HOST = "smtp_host"
        private const val KEY_PORT = "smtp_port"
        private const val KEY_TLS_MODE = "smtp_tls_mode"
        private const val KEY_USERNAME = "smtp_username"
        private const val KEY_PASSWORD = "smtp_password"
        private const val KEY_FROM_ADDRESS = "smtp_from_address"
        private const val KEY_TO_ADDRESS_PRIMARY = "smtp_to_address_primary"
        private const val KEY_CC_ADDRESSES = "smtp_cc_addresses"
        private const val KEY_BCC_ADDRESSES = "smtp_bcc_addresses"
        private const val KEY_SUBJECT_PATTERN = "smtp_subject_pattern"
        private const val KEY_BATCH_DELAY = "batch_delay_seconds"
        private const val KEY_BATCH_DELAY_ENABLED = "batch_delay_enabled"
        private const val DEFAULT_PORT = 587
    }
}
