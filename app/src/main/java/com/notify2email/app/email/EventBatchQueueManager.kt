package com.notify2email.app.email

import android.util.Log
import com.notify2email.app.domain.model.Event
import com.notify2email.app.domain.model.EventType
import com.notify2email.app.domain.model.SentStatus
import com.notify2email.app.domain.repository.EventRepository
import com.notify2email.app.domain.repository.LogRepository
import com.notify2email.app.storage.room.QueuedEventDao
import com.notify2email.app.storage.room.QueuedEventEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

class EventBatchQueueManager(
    private val emailSender: SmtpEmailSender,
    private val configProvider: SmtpConfigProvider,
    private val queueDao: QueuedEventDao,
    private val eventRepository: EventRepository,
    private val logRepository: LogRepository
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val stateMutex = Mutex()
    private var flushJob: Job? = null
    private var isFlushing = false

    fun start() {
        scope.launch {
            stateMutex.withLock {
                if (!isFlushing && flushJob?.isActive != true) {
                    scheduleNextFlushLocked()
                }
            }
        }
    }

    suspend fun enqueue(event: BatchQueueEvent) {
        if (shouldDropAsDuplicate(event)) {
            logRepository.addLog("${event.sourceTag}: duplicate notification dropped for ${event.identity}.")
            return
        }

        val eventId = UUID.randomUUID().toString()

        // 1. Instant Persist for UI visibility
        val uiEvent = Event(
            id = eventId,
            type = event.eventType,
            source = event.sourceTag,
            content = event.contentPreview,
            timestamp = event.timestampMillis,
            sentStatus = SentStatus.WAITING_IN_QUEUE
        )
        eventRepository.saveEvent(uiEvent)

        // 2. Queue for transmission
        queueDao.insert(
            QueuedEventEntity(
                id = eventId,
                type = event.eventType.name,
                enabledKey = event.enabledKey,
                sourceTag = event.sourceTag,
                identity = event.identity,
                contentPreview = event.contentPreview,
                detailBody = event.detailBody,
                timestampMillis = event.timestampMillis,
                enqueuedAtMillis = System.currentTimeMillis(),
                dedupeKey = event.dedupeKey,
                customTypeLabel = event.customTypeLabel
            )
        )

        logRepository.addLog("${event.sourceTag}: queued ${event.eventType.name.lowercase()} event for batching.")

        stateMutex.withLock {
            if (!isFlushing) {
                val immediate = event.eventType == EventType.CALL || 
                               event.eventType == EventType.SMS || 
                               !configProvider.isBatchDelayEnabled()

                if (immediate) {
                    flushJob?.cancel()
                    flushJob = scope.launch { flushBatch() }
                } else if (flushJob?.isActive != true) {
                    scheduleNextFlushLocked()
                }
            }
        }
    }

    private suspend fun shouldDropAsDuplicate(event: BatchQueueEvent): Boolean {
        val dedupeKey = event.dedupeKey ?: return false

        val windowMs = when (event.eventType) {
            EventType.NOTIFICATION -> NOTIFICATION_DEDUPE_WINDOW_MS
            EventType.SMS -> SMS_DEDUPE_WINDOW_MS
            EventType.CALL -> CALL_DEDUPE_WINDOW_MS
            else -> return false
        }

        return queueDao.countRecentDuplicates(
            type = event.eventType.name,
            dedupeKey = dedupeKey,
            sinceMillis = event.timestampMillis - windowMs
        ) > 0
    }

    private suspend fun scheduleNextFlushLocked() {
        val oldestEnqueuedAt = queueDao.getOldestEnqueuedAt() ?: return
        
        if (!configProvider.isBatchDelayEnabled()) {
            flushJob = scope.launch { flushBatch() }
            return
        }

        val batchDelayMs = configProvider.getBatchDelaySeconds() * 1000L
        val delayMillis = (oldestEnqueuedAt + batchDelayMs - System.currentTimeMillis()).coerceAtLeast(0L)

        flushJob = scope.launch {
            delay(delayMillis)
            flushBatch()
        }
    }

    private suspend fun flushBatch() {
        val batch = stateMutex.withLock {
            if (isFlushing) return@withLock null
            isFlushing = true
            flushJob = null
            queueDao.getAllOrdered()
        } ?: return

        if (batch.isEmpty()) {
            stateMutex.withLock { isFlushing = false }
            return
        }

        try {
            // Check if SMTP is configured globally
            if (!configProvider.isSmtpConfigured()) {
                val errorMsg = "SMTP not configured"
                logRepository.addLog("Batching error: $errorMsg. Events marked for configuration update.")
                batch.forEach { item ->
                    updateEventStatus(item.id, SentStatus.SMTP_NOT_CONFIGURED, errorMsg)
                    queueDao.deleteById(item.id)
                }
                return
            }

            val sendableEvents = batch.filter { configProvider.isFeatureEnabled(it.enabledKey) }
            val skippedEvents = batch - sendableEvents.toSet()

            skippedEvents.forEach { queued ->
                updateEventStatus(queued.id, SentStatus.SKIPPED)
                queueDao.deleteById(queued.id)
            }

            if (sendableEvents.isEmpty()) {
                logRepository.addLog("Batch skipped because corresponding features are disabled.")
                return
            }

            val emailSettings = configProvider.getEmailSettings(sendableEvents.first().enabledKey)
            if (emailSettings == null) {
                sendableEvents.forEach { queued ->
                    updateEventStatus(queued.id, SentStatus.FAILED, "Settings unavailable")
                    queueDao.deleteById(queued.id)
                }
                logRepository.addLog("Batch failed: SMTP settings unavailable at send time.")
                return
            }

            val result = emailSender.sendBatch(sendableEvents, emailSettings)

            val overallStatus = if (result.isSuccess) {
                SentStatus.SENT
            } else {
                SentStatus.FAILED
            }
            val errorMessage = result.exceptionOrNull()?.message

            sendableEvents.forEach { queued ->
                updateEventStatus(queued.id, overallStatus, errorMessage)
                queueDao.deleteById(queued.id)
            }

            if (overallStatus == SentStatus.SENT) {
                logRepository.addLog("Batched email sent successfully for ${sendableEvents.size} events.")
            } else {
                logRepository.addLog("Batched email failed: $errorMessage")
            }
        } catch (error: Exception) {
            Log.e(TAG, "Failed to flush queued events", error)
            logRepository.addLog("Batch flush failed unexpectedly: ${error.message}")
        } finally {
            stateMutex.withLock {
                isFlushing = false
                if (queueDao.getCount() > 0) {
                    scheduleNextFlushLocked()
                }
            }
        }
    }

    private suspend fun updateEventStatus(
        eventId: String,
        sentStatus: SentStatus,
        errorMessage: String? = null
    ) {
        val existing = eventRepository.getEvents().find { it.id == eventId }
        if (existing != null) {
            eventRepository.saveEvent(
                existing.copy(
                    sentStatus = sentStatus,
                    errorMessage = errorMessage
                )
            )
        } else {
            // Fallback: This shouldn't happen if enqueued correctly
            Log.w(TAG, "Could not find event $eventId in repository to update status to $sentStatus")
        }
    }

    companion object {
        private const val TAG = "EventBatchQueue"
        private const val NOTIFICATION_DEDUPE_WINDOW_MS = 60_000L
        private const val SMS_DEDUPE_WINDOW_MS = 60_000L
        private const val CALL_DEDUPE_WINDOW_MS = 60_000L
    }
}
