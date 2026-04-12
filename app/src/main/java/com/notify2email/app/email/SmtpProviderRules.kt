package com.notify2email.app.email

import com.notify2email.app.domain.model.SmtpSettings

object SmtpProviderRules {

    fun detectProvider(host: String): SmtpProvider {
        val normalizedHost = host.trim().lowercase()
        return when {
            "yahoo" in normalizedHost -> SmtpProvider.YAHOO
            else -> SmtpProvider.GENERIC
        }
    }

    fun normalizeSettings(settings: SmtpSettings): SmtpSettings {
        val provider = detectProvider(settings.host)
        val withEncryption = when (provider) {
            SmtpProvider.YAHOO -> settings.copy(
                port = YAHOO_SSL_PORT,
                encryption = TlsMode.SSL_TLS
            )

            SmtpProvider.GENERIC -> settings.copy(
                encryption = settings.port.recommendedTlsMode(settings.encryption)
            )
        }

        val normalizedCc = com.notify2email.app.util.EmailValidator.normalizeRecipients(
            withEncryption.ccEmails,
            withEncryption.toEmailPrimary
        )
        val normalizedBcc = com.notify2email.app.util.EmailValidator.normalizeRecipients(
            withEncryption.bccEmails,
            withEncryption.toEmailPrimary,
            excludeFrom = normalizedCc
        )

        return withEncryption.copy(
            ccEmails = normalizedCc,
            bccEmails = normalizedBcc
        )
    }

    fun normalizeConfig(config: SmtpConfig): SmtpConfig {
        val provider = detectProvider(config.host)
        return when (provider) {
            SmtpProvider.YAHOO -> config.copy(
                port = YAHOO_SSL_PORT,
                tlsMode = TlsMode.SSL_TLS
            )

            SmtpProvider.GENERIC -> config.copy(
                tlsMode = config.port.recommendedTlsMode(config.tlsMode)
            )
        }
    }

    fun yahooPasswordWarning(host: String): String? {
        return if (detectProvider(host) == SmtpProvider.YAHOO) {
            "Yahoo requires App Password"
        } else {
            null
        }
    }

    private fun Int.recommendedTlsMode(current: TlsMode): TlsMode {
        return when (this) {
            465 -> TlsMode.SSL_TLS
            587 -> TlsMode.STARTTLS
            else -> current
        }
    }

    private const val YAHOO_SSL_PORT = 465
}

enum class SmtpProvider {
    GENERIC,
    YAHOO
}
