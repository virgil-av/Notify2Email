package com.notify2email.app.email

import com.notify2email.app.domain.model.SmtpSettings

class SmtpTestHelper(
    private val emailSender: SmtpEmailSender = SmtpEmailSender()
) {

    suspend fun sendTestEmail(settings: SmtpSettings): SendEmailResult {
        val normalizedSettings = SmtpProviderRules.normalizeSettings(settings)
        val config = SmtpConfig(
            host = normalizedSettings.host,
            port = normalizedSettings.port,
            tlsMode = normalizedSettings.encryption,
            username = settings.username,
            password = settings.password
        )

        return emailSender.sendEmail(
            config = config,
            email = HtmlEmail(
                from = normalizedSettings.fromEmail,
                to = normalizedSettings.toEmailPrimary,
                cc = normalizedSettings.ccEmails,
                bcc = normalizedSettings.bccEmails,
                subject = "[Notify2Email] SMTP Connection Test",
                body = """
                    <div style="font-family: sans-serif; padding: 20px; border: 1px solid #e2e8f0; border-radius: 8px;">
                        <h2 style="color: #10b981; margin-top: 0;">Connection Successful!</h2>
                        <p>This is a test email sent from <b>Notify2Email</b> to verify your SMTP settings.</p>
                        <hr style="border: none; border-top: 1px solid #f1f5f9; margin: 20px 0;">
                        <div style="font-size: 12px; color: #94a3b8;">
                            Sent at: ${java.util.Date()}<br>
                            Host: ${config.host}:${config.port}
                        </div>
                    </div>
                """.trimIndent(),
                isHtml = true
            )
        )
    }
}
