package com.notify2email.app.notifications

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.notify2email.app.util.TimeUtils
import com.notify2email.app.domain.formatter.EventFormatter
import com.notify2email.app.di.appContainer
import com.notify2email.app.email.BatchQueueEvent
import com.notify2email.app.domain.model.EventType
import com.notify2email.app.domain.repository.LogRepository
import com.notify2email.app.email.SmtpConfigProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

private const val NOTIFICATION_DEDUPE_WINDOW_MILLIS = 8_000L
private const val NOTIFICATION_HASH_WINDOW_MILLIS = 5_000L
private const val NOTIFICATION_QUEUE_DEBOUNCE_MILLIS = 750L

class PhoneNotificationListenerService : NotificationListenerService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val notificationFilterManager by lazy {
        applicationContext.appContainer.notificationFilterManager
    }
    private val notificationProcessor: NotificationEventProcessor by lazy {
        EmailingNotificationEventProcessor(
            queueManager = applicationContext.appContainer.eventBatchQueueManager,
            logRepository = applicationContext.appContainer.logRepository
        )
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.i(TAG, "$DEBUG_PREFIX listener connected")
        serviceScope.launch {
            applicationContext.appContainer.logRepository.addLog("$DEBUG_PREFIX listener connected.")
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.w(TAG, "$DEBUG_PREFIX listener disconnected")
        serviceScope.launch {
            applicationContext.appContainer.logRepository.addLog("$DEBUG_PREFIX listener disconnected.")
        }
        requestRebind(ComponentName(this, PhoneNotificationListenerService::class.java))
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        super.onNotificationPosted(sbn)

        val container = applicationContext.appContainer
        val packageName = sbn.packageName

        // 1. Immediate unconditional log (UI)
        serviceScope.launch {
            container.logRepository.addLog("DEBUG: Notification trigger from $packageName")
        }

        // 2. Check global service status
        val isServiceRunning = container.serviceStateRepository.isServiceRunning()
        if (!isServiceRunning) {
            Log.w(TAG, "$DEBUG_PREFIX ignoring notification because service is NOT RUNNING")
            serviceScope.launch {
                container.logRepository.addLog("$DEBUG_PREFIX ignored: service state is OFF.")
            }
            return
        }

        val event = sbn.toNotificationEvent()
        notificationFilterManager.recordDetectedApp(event.packageName, event.appName)

        // 3. Determine Smart Event Type and check corresponding feature flag
        val smartEventType = NotificationClassifier.getSmartEventType(packageName, contentResolver)
        val eventType = smartEventType ?: EventType.NOTIFICATION
        
        val featureKey = when (eventType) {
            EventType.SMS -> SmtpConfigProvider.KEY_SMS_ENABLED
            EventType.CALL -> SmtpConfigProvider.KEY_CALL_LOGS_ENABLED
            else -> SmtpConfigProvider.KEY_NOTIFICATIONS_ENABLED
        }

        if (!container.smtpConfigProvider.isFeatureEnabled(featureKey)) {
            Log.w(TAG, "$DEBUG_PREFIX ignoring notification because $featureKey is DISABLED")
            serviceScope.launch {
                container.logRepository.addLog("$DEBUG_PREFIX ignored: feature is disabled in settings.")
            }
            return
        }

        val appName = if (smartEventType != null) {
            // For smart-labeled events, use the sender as the "app name" for display
            event.title ?: event.appName
        } else {
            event.appName.ifBlank { event.packageName }
        }

        // 5. Check base ignore criteria (ongoing noise, app self-notifs)
        val baseIgnoreReason = baseIgnoreReason(event)
        if (baseIgnoreReason != null) {
            serviceScope.launch {
                container.logRepository.addLog(
                    "$DEBUG_PREFIX filtered: $baseIgnoreReason from ${event.appName.ifBlank { event.packageName }}."
                )
            }
            return
        }

        // 6. Check custom user filters
        val filterDecision = notificationFilterManager.evaluate(event)
        if (filterDecision.isFiltered) {
            serviceScope.launch {
                container.logRepository.addLog(
                    "$DEBUG_PREFIX filtered by user: ${filterDecision.reason ?: "custom filter"} for ${event.appName.ifBlank { event.packageName }}."
                )
            }
            return
        }

        // 7. Deduplication
        if (isDuplicate(event)) {
            serviceScope.launch {
                container.logRepository.addLog(
                    "$DEBUG_PREFIX ignored duplicate: ${event.appName.ifBlank { event.packageName }}."
                )
            }
            return
        }

        // 8. Capture Success - Dispatch for processing
        serviceScope.launch {
            container.logRepository.addLog("$DEBUG_PREFIX captured notification from $appName.")
            runCatching {
                delay(NOTIFICATION_QUEUE_DEBOUNCE_MILLIS)
                notificationProcessor.process(event, eventType)
            }.onFailure { error ->
                Log.e(TAG, "$DEBUG_PREFIX failed to process", error)
                container.logRepository.addLog("$DEBUG_PREFIX processing error: ${error.message}")
            }
        }
    }

    override fun onDestroy() {
        recentNotificationWindows.entries.removeIf { (_, capturedAt) ->
            System.currentTimeMillis() - capturedAt > NOTIFICATION_DEDUPE_WINDOW_MILLIS
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun baseIgnoreReason(event: NotificationEvent): String? {
        if (event.packageName == packageName) {
            return "app self-notification"
        }

        // We filter out SYSTEM dialer call notifications because CallLogObserver handles them.
        // We do NOT filter WhatsApp/Signal/etc. because they don't appear in the Android CallLog.
        if (NotificationClassifier.isSystemDialer(event.packageName, contentResolver) && 
            (event.category == Notification.CATEGORY_CALL || event.category == Notification.CATEGORY_MISSED_CALL)) {
            return "redundant system call category"
        }

        val title = event.title.orEmpty().trim()
        val body = event.bestAvailableMessage.trim()
        val looksIrrelevant = body.isBlank() && title.isBlank()

        // Filter out "Checking for messages" / "Syncing" noise
        val syncKeywords = listOf("checking for new messages", "searching for new messages", "syncing...", "looking for messages")
        if (syncKeywords.any { body.lowercase().contains(it) }) {
            return "app sync noise"
        }

        // Ongoing noise (services, progress bars, etc.)
        val isForegroundNoise = event.isOngoing &&
                event.category in setOf(Notification.CATEGORY_SERVICE, Notification.CATEGORY_PROGRESS)

        if (looksIrrelevant || isForegroundNoise) {
            return "low-value notification"
        }

        return null
    }

    private fun isDuplicate(event: NotificationEvent): Boolean {
        val now = System.currentTimeMillis()
        recentNotificationWindows.entries.removeIf { (_, capturedAt) ->
            now - capturedAt > NOTIFICATION_DEDUPE_WINDOW_MILLIS
        }

        val previousSeenAt = recentNotificationWindows.putIfAbsent(event.windowedHash, now)
        return previousSeenAt != null && now - previousSeenAt <= NOTIFICATION_DEDUPE_WINDOW_MILLIS
    }

    private fun StatusBarNotification.toNotificationEvent(): NotificationEvent {
        val extras = notification.extras ?: Bundle.EMPTY
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.trim()
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()?.trim()
        val category = notification.category
        val appLabel = packageManager.safeApplicationLabel(packageName)

        return NotificationEvent(
            packageName = packageName,
            appName = appLabel,
            notificationKey = key,
            notificationId = id,
            channelId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                notification.channelId
            } else {
                null
            },
            category = category,
            title = title,
            text = text,
            bigText = bigText,
            subText = subText,
            postTimeMillis = postTime,
            receivedAtMillis = System.currentTimeMillis(),
            isOngoing = isOngoing,
            isClearable = isClearable
        )
    }

    private fun android.content.pm.PackageManager.safeApplicationLabel(targetPackage: String): String {
        return runCatching {
            val applicationInfo = getApplicationInfo(targetPackage, 0)
            getApplicationLabel(applicationInfo).toString()
        }.getOrDefault(targetPackage)
    }

    companion object {
        private const val TAG = "PhoneNotificationSvc"
        private const val DEBUG_PREFIX = "[NOTIF]"
        private val recentNotificationWindows = ConcurrentHashMap<String, Long>()

        fun isAccessGranted(context: Context): Boolean {
            val enabledListeners =
                android.provider.Settings.Secure.getString(
                    context.contentResolver,
                    "enabled_notification_listeners"
                ).orEmpty()

            return enabledListeners.contains(context.packageName)
        }
    }
}

/**
 * Shared logic for classifying notifications into SMS, CALL, or generic NOTIFICATION.
 */
object NotificationClassifier {
    fun isSystemDialer(packageName: String, contentResolver: android.content.ContentResolver): Boolean {
        val dialer = android.provider.Settings.Secure.getString(contentResolver, "dialer_default_application")
        val knownDialers = setOf(
            "com.google.android.dialer",
            "com.android.phone",
            "com.android.server.telecom",
            "com.android.incallui",
            "com.samsung.android.incallui",
            "com.samsung.android.dialer",
            dialer
        ).filterNotNull()
        return packageName in knownDialers
    }

    fun getSmartEventType(packageName: String, contentResolver: android.content.ContentResolver?): EventType? {
        val sms = if (contentResolver != null) {
            android.provider.Telephony.Sms.getDefaultSmsPackage(null) // Context-free attempt
        } else null

        val knownSms = setOf(
            "com.google.android.apps.messaging",
            "com.android.messaging",
            "com.samsung.android.messaging",
            "com.samsung.android.communications",
            sms
        ).filterNotNull()

        val voipApps = setOf("com.whatsapp", "com.whatsapp.w4b", "org.telegram.messenger", "org.thoughtcrime.securesms")

        return when {
            packageName in knownSms -> EventType.SMS
            packageName in voipApps -> EventType.CALL
            contentResolver != null && isSystemDialer(packageName, contentResolver) -> EventType.CALL
            else -> null
        }
    }
}

data class NotificationEvent(
    val packageName: String,
    val appName: String,
    val notificationKey: String,
    val notificationId: Int,
    val channelId: String?,
    val category: String?,
    val title: String?,
    val text: String?,
    val bigText: String?,
    val subText: String?,
    val postTimeMillis: Long,
    val receivedAtMillis: Long,
    val isOngoing: Boolean,
    val isClearable: Boolean
) {
    val normalizedBody: String
        get() = listOfNotNull(text, bigText, subText)
            .map(String::trim)
            .filter(String::isNotEmpty)
            .distinct()
            .joinToString(separator = "\n")

    val bestAvailableMessage: String
        get() = listOfNotNull(
            bigText?.trim(),
            mergedTitleAndText,
            text?.trim(),
            title?.trim()
        ).firstOrNull { it.isNotEmpty() }.orEmpty()

    private val mergedTitleAndText: String?
        get() {
            val normalizedTitle = title?.trim().orEmpty()
            val normalizedText = text?.trim().orEmpty()

            return when {
                normalizedTitle.isBlank() || normalizedText.isBlank() -> null
                normalizedTitle.equals(normalizedText, ignoreCase = true) -> normalizedText
                else -> "$normalizedTitle: $normalizedText"
            }
        }

    val windowedHash: String
        get() {
            val window = (postTimeMillis.takeIf { it > 0 } ?: receivedAtMillis) / NOTIFICATION_HASH_WINDOW_MILLIS
            val raw = buildString {
                append(packageName)
                append('|')
                append(title?.lowercase().orEmpty())
                append('|')
                append(normalizedBody.lowercase())
                append('|')
                append(window)
            }
            return raw.hashCode().toString()
        }

    val dedupeKey: String
        get() = buildString {
            append(packageName)
            append('|')
            append(postTimeMillis)
            append('|')
            append(title.orEmpty())
            append('|')
            append(normalizedBody)
        }

    val batchDedupeKey: String
        get() {
            // Context-less check for SMS deduplication hash
            val isSms = packageName in setOf(
                "com.google.android.apps.messaging", "com.android.messaging", 
                "com.samsung.android.messaging", "com.samsung.android.communications"
            )
            
            return if (isSms) {
                val digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(normalizedBody.trim().toByteArray(Charsets.UTF_8))
                    .joinToString(separator = "") { byte -> "%02x".format(byte) }
                "sms_body_hash|$digest"
            } else {
                buildString {
                    append(packageName)
                    append('|')
                    append(title.orEmpty())
                    append('|')
                    append(normalizedBody)
                }
            }
        }
}

fun interface NotificationEventProcessor {
    suspend fun process(event: NotificationEvent, overrideType: EventType?)
}

class LoggingNotificationEventProcessor : NotificationEventProcessor {
    override suspend fun process(event: NotificationEvent, overrideType: EventType?) {
        Log.i(
            "NotificationProcessor",
            "Captured notification from ${event.packageName} with key=${event.notificationKey}"
        )
    }
}

class EmailingNotificationEventProcessor(
    private val queueManager: com.notify2email.app.email.EventBatchQueueManager,
    private val logRepository: LogRepository
) : NotificationEventProcessor {

    override suspend fun process(event: NotificationEvent, overrideType: EventType?) {
        val finalType = overrideType ?: EventType.NOTIFICATION
        Log.i(TAG, "Captured notification from ${event.packageName} with key=${event.notificationKey} (as $finalType)")
        
        val appName = event.appName.ifBlank { event.packageName }
        val source = if (finalType == EventType.NOTIFICATION) appName else (event.title ?: appName)

        logRepository.addLog(
            "$TAG: event captured from $source (as $finalType)."
        )

        queueManager.enqueue(
            BatchQueueEvent(
                sourceTag = source,
                enabledKey = when (finalType) {
                    EventType.SMS -> SmtpConfigProvider.KEY_SMS_ENABLED
                    EventType.CALL -> SmtpConfigProvider.KEY_CALL_LOGS_ENABLED
                    else -> SmtpConfigProvider.KEY_NOTIFICATIONS_ENABLED
                },
                eventType = finalType,
                identity = event.notificationKey,
                contentPreview = event.bestAvailableMessage.ifBlank { "No message available" },
                detailBody = buildEmailBody(event, finalType),
                timestampMillis = event.receivedAtMillis,
                dedupeKey = event.batchDedupeKey
            )
        )
    }

    private fun buildEmailBody(event: NotificationEvent, type: EventType): String {
        val appName = event.appName.ifBlank { event.packageName }
        val source = if (type == EventType.NOTIFICATION) appName else (event.title ?: appName)
        
        return EventFormatter.formatEventHtml(
            type = type,
            source = source,
            timestampMillis = event.postTimeMillis.takeIf { it > 0 } ?: event.receivedAtMillis,
            content = event.bestAvailableMessage.ifBlank { "No message available" }
        )
    }

    private fun buildContentPreview(event: NotificationEvent): String {
        val appPart = event.appName.ifBlank { event.packageName }
        val bodyPart = event.bestAvailableMessage.ifBlank { "No message" }
        return "$appPart: $bodyPart".take(120)
    }

    companion object {
        private const val TAG = "NotificationProcessor"
    }
}

object NotificationEmailFormatter {

    fun format(event: NotificationEvent): String {
        val appName = event.appName.ifBlank { "Unknown app" }
        val message = event.bestAvailableMessage.ifBlank { "No message available" }
        val formattedTime = TimeUtils.formatDateTime(event.postTimeMillis.takeIf { it > 0 } ?: event.receivedAtMillis)

        return buildString {
            appendLine("App: $appName")
            appendLine("Message: $message")
            append("Time: $formattedTime")
        }
    }

    fun formatGroup(events: List<NotificationEvent>): String {
        return events.joinToString(separator = "\n\n") { format(it) }
    }
}
