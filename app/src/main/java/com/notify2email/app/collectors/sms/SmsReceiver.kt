package com.notify2email.app.collectors.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Telephony
import android.telephony.SmsMessage
import android.util.Log
import androidx.core.content.ContextCompat
import com.notify2email.app.di.appContainer
import com.notify2email.app.domain.formatter.EventFormatter
import com.notify2email.app.domain.model.EventType
import com.notify2email.app.email.BatchQueueEvent
import com.notify2email.app.email.SmtpConfigProvider
import com.notify2email.app.util.TimeUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // Log immediately to Logcat (this is faster than the database log)
        Log.i(TAG, "!!! SMS Broadcast TRIGGERED action=${intent.action} !!!")

        val appContext = context.applicationContext
        val container = appContext.appContainer

        val pendingResult = goAsync()
        val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        receiverScope.launch {
            try {
                // Unconditional log to the UI log
                container.logRepository.addLog("SYSTEM: Received SMS Broadcast (${intent.action})")

                if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
                    Log.d(TAG, "Ignoring action: ${intent.action}")
                    return@launch
                }

                // 2. Check Permission
                if (ContextCompat.checkSelfPermission(appContext, android.Manifest.permission.RECEIVE_SMS) != PackageManager.PERMISSION_GRANTED) {
                    Log.w(TAG, "$DEBUG_PREFIX missing RECEIVE_SMS permission")
                    container.logRepository.addLog("$DEBUG_PREFIX error: missing RECEIVE_SMS permission.")
                    return@launch
                }

                // 3. Check global service status
                val isServiceRunning = container.serviceStateRepository.isServiceRunning()
                if (!isServiceRunning) {
                    Log.w(TAG, "$DEBUG_PREFIX ignoring SMS because service is NOT RUNNING")
                    container.logRepository.addLog("$DEBUG_PREFIX ignored: service state is OFF.")
                    return@launch
                }

                // 4. Check if SMS forwarding is enabled
                val isSmsEnabled = container.smtpConfigProvider.isFeatureEnabled(SmtpConfigProvider.KEY_SMS_ENABLED)
                if (!isSmsEnabled) {
                    Log.w(TAG, "$DEBUG_PREFIX ignoring SMS because feature is DISABLED")
                    container.logRepository.addLog("$DEBUG_PREFIX ignored: SMS feature is disabled in settings.")
                    return@launch
                }

                val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
                if (messages.isNullOrEmpty()) {
                    Log.w(TAG, "$DEBUG_PREFIX no SMS PDUs found")
                    container.logRepository.addLog("$DEBUG_PREFIX error: no SMS data found in broadcast.")
                    return@launch
                }

                val settings = container.settingsRepository.getSettings()
                val smsEvent = messages.toSmsEvent()
                
                val resolvedName = if (settings.resolveContactNames) {
                    container.contactNameResolver.resolve(smsEvent.sender)
                } else null

                val simInfo = if (settings.showSimInfo) {
                    container.simSlotResolver.resolveBestEffort(intent = intent)
                } else null

                val displayName = resolvedName ?: smsEvent.sender
                val simSuffix = simInfo?.displayName?.let { " ($it)" } ?: ""
                val sourceTag = if (resolvedName != null) "$resolvedName (${smsEvent.sender})" else smsEvent.sender

                Log.i(TAG, "$DEBUG_PREFIX captured SMS from $displayName$simSuffix")
                container.logRepository.addLog("$DEBUG_PREFIX captured SMS from $displayName$simSuffix.")

                container.eventBatchQueueManager.enqueue(
                    BatchQueueEvent(
                        sourceTag = sourceTag,
                        enabledKey = SmtpConfigProvider.KEY_SMS_ENABLED,
                        eventType = EventType.SMS,
                        identity = smsEvent.sender,
                        contentPreview = smsEvent.messageBody.trim(),
                        detailBody = EventFormatter.formatEventHtml(
                            type = EventType.SMS,
                            source = sourceTag,
                            timestampMillis = smsEvent.timestampMillis,
                            content = smsEvent.messageBody + (if (simSuffix.isNotEmpty()) "\n\nReceived on $simSuffix" else "")
                        ),
                        timestampMillis = smsEvent.timestampMillis,
                        dedupeKey = smsEvent.dedupeKey
                    )
                )
            } catch (error: Exception) {
                Log.e(TAG, "$DEBUG_PREFIX processing failed", error)
                container.logRepository.addLog("$DEBUG_PREFIX processing error: ${error.message}")
            } finally {
                pendingResult.finish()
                receiverScope.cancel()
            }
        }
    }

    private fun Array<SmsMessage>.toSmsEvent(): SmsEvent {
        val firstMessage = first()
        val body = joinToString(separator = "") { it.messageBody.orEmpty() }.trim()
        return SmsEvent(
            sender = firstMessage.displayOriginatingAddress.orEmpty(),
            messageBody = body,
            timestampMillis = firstMessage.timestampMillis,
            dedupeKey = buildSmsDedupeKey(body)
        )
    }

    private fun buildSmsDedupeKey(body: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(body.trim().toByteArray(Charsets.UTF_8))
            .joinToString(separator = "") { byte -> "%02x".format(byte) }
        return "sms_body_hash|$digest"
    }

    companion object {
        private const val TAG = "SmsReceiver"
        private const val DEBUG_PREFIX = "[SMS]"
    }
}

data class SmsEvent(
    val sender: String,
    val messageBody: String,
    val timestampMillis: Long,
    val dedupeKey: String
)
