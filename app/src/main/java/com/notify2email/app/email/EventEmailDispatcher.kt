package com.notify2email.app.email

import android.util.Log
import com.notify2email.app.domain.formatter.EventFormatter
import com.notify2email.app.domain.model.Event
import com.notify2email.app.domain.model.EventType
import com.notify2email.app.domain.model.SentStatus
import com.notify2email.app.domain.repository.EventRepository
import com.notify2email.app.domain.repository.LogRepository
import java.util.UUID

class EventEmailDispatcher(
    private val emailSender: SmtpEmailSender,
    private val configProvider: SmtpConfigProvider,
    private val eventRepository: EventRepository,
    private val logRepository: LogRepository
) {

    suspend fun dispatch(
        sourceTag: String,
        enabledKey: String,
        eventType: EventType,
        subject: String,
        body: String,
        identity: String,
        contentPreview: String,
        timestampMillis: Long = System.currentTimeMillis()
    ): SendEmailResult? {
        val emailSettings = configProvider.getEmailSettings(enabledKey)
        if (emailSettings == null) {
            Log.w(sourceTag, "Skipping email dispatch because SMTP settings are incomplete or disabled")
            logRepository.addLog("$sourceTag: skipped send because SMTP settings are incomplete or disabled.")
            eventRepository.saveEvent(
                Event(
                    id = UUID.randomUUID().toString(),
                    type = eventType,
                    source = sourceTag,
                    content = contentPreview,
                    timestamp = timestampMillis,
                    sentStatus = SentStatus.SKIPPED
                )
            )
            return null
        }

        val bodyHtml = EventFormatter.formatEventHtml(
            type = eventType,
            source = identity,
            timestampMillis = timestampMillis,
            content = contentPreview
        )

        val defaultSubject = "[Notify2Email] New $eventType from $identity"
        val finalSubject = if (emailSettings.subjectPattern.isNotBlank()) {
            emailSettings.subjectPattern
                .replace("{type}", eventType.name)
                .replace("{source}", identity)
        } else {
            defaultSubject
        }

        val result = emailSender.sendEmail(
            config = emailSettings.smtpConfig,
            email = HtmlEmail(
                from = emailSettings.fromAddress,
                to = emailSettings.toAddressPrimary,
                cc = emailSettings.ccAddresses,
                bcc = emailSettings.bccAddresses,
                subject = finalSubject,
                body = bodyHtml,
                isHtml = true
            )
        )

        eventRepository.saveEvent(
            Event(
                id = UUID.randomUUID().toString(),
                type = eventType,
                source = sourceTag,
                content = contentPreview,
                timestamp = timestampMillis,
                sentStatus = result.toSentStatus(),
                errorMessage = result.exceptionOrNull()?.message
            )
        )

        when (result) {
            is SendEmailResult.Success -> {
                Log.i(sourceTag, "Email sent for $identity after ${result.attempts} attempt(s)")
                logRepository.addLog("$sourceTag: email sent for $identity after ${result.attempts} attempt(s).")
            }

            is SendEmailResult.TransientFailure -> {
                Log.w(sourceTag, "Temporary SMTP failure for $identity: ${result.error}")
                logRepository.addLog("$sourceTag: temporary SMTP failure for $identity: ${result.error}")
            }

            is SendEmailResult.PermanentFailure -> {
                Log.e(sourceTag, "Permanent SMTP failure for $identity: ${result.error}")
                logRepository.addLog("$sourceTag: permanent SMTP failure for $identity: ${result.error}")
            }

            is SendEmailResult.RetryExhausted -> {
                Log.e(sourceTag, "SMTP retries exhausted for $identity: ${result.error}")
                logRepository.addLog("$sourceTag: SMTP retries exhausted for $identity: ${result.error}")
            }
        }

        return result
    }

    private fun SendEmailResult.toSentStatus(): SentStatus {
        return when (this) {
            is SendEmailResult.Success -> SentStatus.SENT
            is SendEmailResult.TransientFailure,
            is SendEmailResult.PermanentFailure,
            is SendEmailResult.RetryExhausted -> SentStatus.FAILED
        }
    }
}
