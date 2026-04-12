package com.notify2email.app.email

import android.util.Log
import com.notify2email.app.domain.formatter.EventFormatter
import com.notify2email.app.domain.model.EventType
import com.notify2email.app.storage.room.QueuedEventEntity
import jakarta.mail.Authenticator
import jakarta.mail.Message
import jakarta.mail.MessagingException
import jakarta.mail.PasswordAuthentication
import jakarta.mail.SendFailedException
import jakarta.mail.Session
import jakarta.mail.Transport
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Properties

class SmtpEmailSender(
    private val dispatcherProvider: EmailDispatcherProvider = DefaultEmailDispatcherProvider()
) {

    suspend fun sendBatch(
        events: List<QueuedEventEntity>,
        settings: EmailSettings
    ): SendEmailResult {
        if (events.isEmpty()) return SendEmailResult.Success(0)

        val emailSubject = "[Notify2Email] Events Digest (${events.size} updates)"
        val emailBody = EventFormatter.formatBatchHtml(
            events = events.map {
                EventFormatter.BatchEventData(
                    type = EventType.valueOf(it.type),
                    typeLabel = it.customTypeLabel,
                    source = it.sourceTag,
                    timestampMillis = it.timestampMillis,
                    content = it.contentPreview
                )
            },
            generatedAtMillis = System.currentTimeMillis()
        )

        val htmlEmail = HtmlEmail(
            from = settings.fromAddress,
            to = settings.toAddressPrimary,
            cc = settings.ccAddresses,
            bcc = settings.bccAddresses,
            subject = emailSubject,
            body = emailBody,
            isHtml = true
        )

        return sendEmail(
            config = settings.smtpConfig,
            email = htmlEmail,
            retryPolicy = RetryPolicy(maxAttempts = 2, initialDelayMillis = 2_000)
        )
    }

    suspend fun sendEmail(
        config: SmtpConfig,
        email: HtmlEmail,
        retryPolicy: RetryPolicy = RetryPolicy()
    ): SendEmailResult {
        var lastError = "Unknown error"
        for (attempt in 1..retryPolicy.maxAttempts) {
            try {
                validateInputs(config, email)
                sendOnce(config, email)
                return SendEmailResult.Success(attempt)
            } catch (e: Exception) {
                lastError = formatErrorMessage(e)
                val result = classifyFailure(e, attempt, config)
                if (result is SendEmailResult.PermanentFailure) return result

                if (attempt < retryPolicy.maxAttempts) {
                    delay(retryPolicy.delayForAttempt(attempt))
                }
            }
        }
        return SendEmailResult.RetryExhausted(retryPolicy.maxAttempts, lastError)
    }

    private suspend fun sendOnce(config: SmtpConfig, email: HtmlEmail) {
        withContext(dispatcherProvider.io) {
            val props = config.toMailProperties()
            val session = Session.getInstance(props, object : Authenticator() {
                override fun getPasswordAuthentication(): PasswordAuthentication {
                    return PasswordAuthentication(config.username, config.password)
                }
            })

            val message = MimeMessage(session).apply {
                setFrom(InternetAddress(email.from))
                setRecipient(Message.RecipientType.TO, InternetAddress(email.to))

                email.cc.forEach {
                    addRecipient(Message.RecipientType.CC, InternetAddress(it))
                }
                email.bcc.forEach {
                    addRecipient(Message.RecipientType.BCC, InternetAddress(it))
                }

                subject = email.subject
                if (email.isHtml) {
                    setContent(email.body, "text/html; charset=utf-8")
                } else {
                    setText(email.body)
                }
            }

            Transport.send(message)
        }
    }

    private fun validateInputs(config: SmtpConfig, email: HtmlEmail) {
        if (config.host.isBlank()) throw IllegalArgumentException("SMTP Host is blank")
        if (config.username.isBlank()) throw IllegalArgumentException("SMTP Username is blank")
        if (email.from.isBlank()) throw IllegalArgumentException("From address is blank")
        if (email.to.isBlank()) throw IllegalArgumentException("To address is blank")
    }

    private fun classifyFailure(e: Throwable, attempt: Int, config: SmtpConfig): SendEmailResult {
        val msg = formatErrorMessage(e)
        return when (e) {
            is AuthenticationFailedException,
            is jakarta.mail.AuthenticationFailedException -> {
                SendEmailResult.PermanentFailure(attempt, "Authentication failed: Check username/password.")
            }
            is SendFailedException -> {
                SendEmailResult.PermanentFailure(attempt, "Send failed: Check recipient addresses.")
            }
            is MessagingException -> {
                if (msg.contains("Unknown SMTP host", ignoreCase = true)) {
                    SendEmailResult.PermanentFailure(attempt, "Unknown host: ${config.host}")
                } else {
                    SendEmailResult.TransientFailure(attempt, msg)
                }
            }
            else -> SendEmailResult.TransientFailure(attempt, msg)
        }
    }

    private fun formatErrorMessage(t: Throwable?): String {
        if (t == null) return "Unknown error"
        val base = t.message ?: t.javaClass.simpleName
        var cause = t.cause
        var depth = 0
        var fullMsg = base
        while (cause != null && depth < 3) {
            fullMsg += " -> ${cause.message ?: cause.javaClass.simpleName}"
            cause = cause.cause
            depth++
        }
        return fullMsg
    }
}

data class SmtpConfig(
    val host: String,
    val port: Int,
    val tlsMode: TlsMode,
    val username: String,
    val password: String,
    val connectionTimeoutMillis: Int = 10_000,
    val readTimeoutMillis: Int = 10_000
) {
    fun toMailProperties(): Properties = Properties().apply {
        put("mail.smtp.host", host)
        put("mail.smtp.port", port.toString())
        put("mail.smtp.auth", "true")
        put("mail.smtp.connectiontimeout", connectionTimeoutMillis.toString())
        put("mail.smtp.timeout", readTimeoutMillis.toString())

        when (tlsMode) {
            TlsMode.NONE -> {
                put("mail.smtp.starttls.enable", "false")
            }
            TlsMode.STARTTLS -> {
                put("mail.smtp.starttls.enable", "true")
                put("mail.smtp.starttls.required", "true")
            }
            TlsMode.SSL_TLS -> {
                put("mail.smtp.ssl.enable", "true")
                put("mail.smtp.socketFactory.port", port.toString())
                put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory")
                put("mail.smtp.socketFactory.fallback", "false")
            }
        }
    }
}

enum class TlsMode { NONE, STARTTLS, SSL_TLS }

data class HtmlEmail(
    val from: String,
    val to: String,
    val cc: List<String> = emptyList(),
    val bcc: List<String> = emptyList(),
    val subject: String,
    val body: String,
    val isHtml: Boolean = true
)

data class RetryPolicy(
    val maxAttempts: Int = 3,
    val initialDelayMillis: Long = 5000
) {
    fun delayForAttempt(attempt: Int): Long = initialDelayMillis * attempt
}

sealed interface SendEmailResult {
    val attempts: Int
    data class Success(override val attempts: Int) : SendEmailResult
    data class TransientFailure(override val attempts: Int, val error: String) : SendEmailResult
    data class PermanentFailure(override val attempts: Int, val error: String) : SendEmailResult
    data class RetryExhausted(override val attempts: Int, val error: String) : SendEmailResult

    val isSuccess: Boolean get() = this is Success
    fun exceptionOrNull(): Throwable? = when (this) {
        is Success -> null
        is TransientFailure -> Exception(error)
        is PermanentFailure -> Exception(error)
        is RetryExhausted -> Exception(error)
    }
}

interface EmailDispatcherProvider { val io: kotlinx.coroutines.CoroutineDispatcher }
class DefaultEmailDispatcherProvider : EmailDispatcherProvider { override val io = Dispatchers.IO }
class AuthenticationFailedException(message: String) : Exception(message)
