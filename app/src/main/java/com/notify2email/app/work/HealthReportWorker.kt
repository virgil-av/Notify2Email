package com.notify2email.app.work

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.notify2email.app.collectors.health.HealthReportCollector
import com.notify2email.app.di.appContainer
import com.notify2email.app.domain.formatter.EventFormatter
import com.notify2email.app.domain.model.Event
import com.notify2email.app.domain.model.EventType
import com.notify2email.app.domain.model.SentStatus
import com.notify2email.app.email.HtmlEmail
import com.notify2email.app.email.SendEmailResult
import com.notify2email.app.email.SmtpConfigProvider
import java.util.UUID

class HealthReportWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = applicationContext.appContainer
        val settings = container.settingsRepository.getSettings()

        if (!settings.healthReportEnabled) {
            Log.i(TAG, "Health report is disabled. Skipping.")
            return Result.success()
        }

        val collector = HealthReportCollector(applicationContext)
        val batteryLevel = collector.getBatteryLevel()
        val isCharging = collector.isBatteryCharging()
        val isScreenOn = collector.isScreenOn()
        val notifications = collector.getActiveNotificationSummaries()

        val reportContent = buildString {
            append("Battery: $batteryLevel% (${if (isCharging) "Charging" else "Discharging"})\n")
            append("Screen: ${if (isScreenOn) "ON" else "OFF"}\n")
            append("Active Notifications (${notifications.size}):\n")
            notifications.forEach { append("- $it\n") }
        }

        val emailSettings = container.smtpConfigProvider.getEmailSettings(SmtpConfigProvider.KEY_NOTIFICATIONS_ENABLED)
        if (emailSettings == null) {
            Log.w(TAG, "SMTP not configured for health report.")
            return Result.failure()
        }

        val htmlBody = EventFormatter.formatEventHtml(
            type = EventType.NOTIFICATION, // Using NOTIFICATION as a base type for the health report
            source = "Health System",
            timestampMillis = System.currentTimeMillis(),
            content = reportContent.replace("\n", "<br>")
        )

        val result = container.emailSender.sendEmail(
            config = emailSettings.smtpConfig,
            email = HtmlEmail(
                from = emailSettings.fromAddress,
                to = emailSettings.toAddressPrimary,
                cc = emailSettings.ccAddresses,
                bcc = emailSettings.bccAddresses,
                subject = "[Notify2Email] Phone Health Snapshot",
                body = htmlBody,
                isHtml = true
            )
        )

        container.eventRepository.saveEvent(
            Event(
                id = UUID.randomUUID().toString(),
                type = EventType.NOTIFICATION,
                source = "Health Report",
                content = "Sent health snapshot: $batteryLevel% battery",
                timestamp = System.currentTimeMillis(),
                sentStatus = if (result is SendEmailResult.Success) SentStatus.SENT else SentStatus.FAILED,
                errorMessage = result.exceptionOrNull()?.message
            )
        )

        container.logRepository.addLog("Health report worker executed. Success: ${result is SendEmailResult.Success}")

        return if (result is SendEmailResult.Success) Result.success() else Result.retry()
    }

    companion object {
        private const val TAG = "HealthReportWorker"
        const val WORK_NAME = "HealthReportWork"
    }
}
