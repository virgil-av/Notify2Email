package com.notify2email.app.collectors.calls

import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.database.Cursor
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.CallLog
import android.util.Log
import androidx.core.content.ContextCompat
import com.notify2email.app.di.appContainer
import com.notify2email.app.domain.formatter.EventFormatter
import com.notify2email.app.domain.model.EventType
import com.notify2email.app.util.TimeUtils
import com.notify2email.app.email.BatchQueueEvent
import com.notify2email.app.email.SmtpConfigProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CallLogObserver(
    context: Context
) : ContentObserver(Handler(Looper.getMainLooper())) {

    private val appContext = context.applicationContext
    private val observerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val preferences = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private var lastProcessedEntryId: Long = -1L
    private val processingMutex = Mutex()

    init {
        lastProcessedEntryId = preferences.getLong(KEY_LAST_PROCESSED_CALL_ID, -1L)
        Log.d(TAG, "$DEBUG_PREFIX initialized with lastProcessedEntryId: $lastProcessedEntryId")
    }

    fun register() {
        Log.i(TAG, "$DEBUG_PREFIX observer registered on ${CallLog.Calls.CONTENT_URI}")
        appContext.contentResolver.registerContentObserver(
            CallLog.Calls.CONTENT_URI,
            true,
            this
        )
        // Ensure we have a baseline on start to avoid capturing stale history.
        observerScope.launch {
            processingMutex.withLock {
                if (lastProcessedEntryId == -1L) {
                    Log.i(TAG, "$DEBUG_PREFIX no baseline ID on register, seeding...")
                    seedLastProcessedId(CallLog.Calls.CONTENT_URI)
                } else {
                    Log.i(TAG, "$DEBUG_PREFIX using existing baseline ID: $lastProcessedEntryId")
                }
            }
        }
    }

    fun unregister() {
        Log.i(TAG, "$DEBUG_PREFIX observer unregistered")
        appContext.contentResolver.unregisterContentObserver(this)
        observerScope.cancel()
    }

    override fun onChange(selfChange: Boolean) {
        onChange(selfChange, null)
    }

    override fun onChange(selfChange: Boolean, uri: Uri?) {
        super.onChange(selfChange, uri)
        val container = appContext.appContainer

        Log.i(TAG, "$DEBUG_PREFIX onChange triggered (selfChange=$selfChange, uri=$uri)")
        observerScope.launch {
            container.logRepository.addLog("SYSTEM: Call Log change detected (uri=$uri).")
        }

        processNewCalls(uri ?: CallLog.Calls.CONTENT_URI)
    }

    private fun processNewCalls(uri: Uri) {
        val container = appContext.appContainer

        // 1. Check global service status
        if (!container.serviceStateRepository.isServiceRunning()) {
            Log.d(TAG, "$DEBUG_PREFIX skipping because service is not running")
            return
        }

        // 2. Check feature enablement
        if (!container.smtpConfigProvider.isFeatureEnabled(SmtpConfigProvider.KEY_CALL_LOGS_ENABLED)) {
            Log.d(TAG, "$DEBUG_PREFIX skipping because call log feature is disabled")
            return
        }

        observerScope.launch {
            processingMutex.withLock {
                try {
                    // Start with a small delay to let the system DB settle
                    delay(2500)

                    if (ContextCompat.checkSelfPermission(
                            appContext,
                            android.Manifest.permission.READ_CALL_LOG
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        Log.w(TAG, "$DEBUG_PREFIX skipping read: permission missing")
                        return@withLock
                    }

                    val currentLastId = lastProcessedEntryId
                    if (currentLastId == -1L) {
                        Log.i(TAG, "$DEBUG_PREFIX first trigger, seeding baseline.")
                        seedLastProcessedId(uri)
                        return@withLock
                    }

                    // Check for ID reset (e.g., call log cleared or system DB maintenance)
                    val maxId = getMaxCallId()
                    if (maxId < currentLastId) {
                        val newBaseline = if (maxId == -1L) 0L else maxId
                        Log.w(TAG, "$DEBUG_PREFIX ID reset detected (DB max: $maxId, Last: $currentLastId). Resetting pointer to $newBaseline.")
                        lastProcessedEntryId = newBaseline
                        preferences.edit().putLong(KEY_LAST_PROCESSED_CALL_ID, newBaseline).commit()
                        // Continue with the new baseline
                    }

                    Log.d(TAG, "$DEBUG_PREFIX checking for new calls since ID: $lastProcessedEntryId")
                    
                    var newCalls = queryNewCalls(uri, lastProcessedEntryId)
                    
                    // Progressive retry for slow database writes (up to ~15s total)
                    var attempt = 1
                    while (newCalls.isEmpty() && attempt <= 3) {
                        val retryDelay = 2000L * attempt
                        Log.d(TAG, "$DEBUG_PREFIX no new entries yet, retry #$attempt in ${retryDelay}ms...")
                        delay(retryDelay)
                        newCalls = queryNewCalls(uri, lastProcessedEntryId)
                        attempt++
                    }

                    if (newCalls.isEmpty()) {
                        Log.d(TAG, "$DEBUG_PREFIX no new entries found after retries.")
                        return@withLock
                    }

                    Log.i(TAG, "$DEBUG_PREFIX found ${newCalls.size} new calls")

                    for (call in newCalls) {
                        processSingleCall(call)
                        lastProcessedEntryId = call.entryId
                        preferences.edit()
                            .putLong(KEY_LAST_PROCESSED_CALL_ID, lastProcessedEntryId)
                            .commit()
                    }

                } catch (error: Exception) {
                    Log.e(TAG, "$DEBUG_PREFIX processing error", error)
                }
            }
        }
    }

    private suspend fun seedLastProcessedId(uri: Uri) {
        val sortOrder = "${CallLog.Calls._ID} DESC LIMIT 1"

        try {
            // Use null projection to avoid issues with missing columns on different devices
            appContext.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                null,
                null,
                null,
                sortOrder
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    lastProcessedEntryId = cursor.getLong(cursor.getColumnIndexOrThrow(CallLog.Calls._ID))
                    preferences.edit()
                        .putLong(KEY_LAST_PROCESSED_CALL_ID, lastProcessedEntryId)
                        .commit()
                    Log.i(TAG, "$DEBUG_PREFIX seeded lastProcessedEntryId with $lastProcessedEntryId")
                } else {
                    lastProcessedEntryId = 0L
                    preferences.edit()
                        .putLong(KEY_LAST_PROCESSED_CALL_ID, 0L)
                        .commit()
                    Log.i(TAG, "$DEBUG_PREFIX call log is empty, baseline set to 0")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "$DEBUG_PREFIX failed to seed ID", e)
        }
    }

    private suspend fun processSingleCall(call: CallLogEvent) {
        val container = appContext.appContainer
        val settings = container.settingsRepository.getSettings()

        Log.i(
            TAG,
            "$DEBUG_PREFIX call detected for ${call.number.ifBlank { "unknown number" }} at ${call.timestampMillis} (SIM: ${call.subscriptionId ?: "Unknown"})"
        )
        
        val resolvedName = if (settings.resolveContactNames && call.cachedName.isNullOrBlank()) {
            container.contactNameResolver.resolve(call.number)
        } else call.cachedName

        val simInfo = if (settings.showSimInfo && call.subscriptionId != null) {
            container.simSlotResolver.resolveBestEffort(subscriptionId = call.subscriptionId)
        } else null

        val simLabel = if (settings.showSimInfo) {
            simInfo?.displayName?.let { " [$it]" } ?: (if (call.subscriptionId != null) " [SIM ${call.subscriptionId}]" else "")
        } else ""

        val contactInfo = if (!resolvedName.isNullOrBlank()) {
            "$resolvedName (${call.number})$simLabel"
        } else {
            "${call.number.ifBlank { "Unknown Number" }}$simLabel"
        }

        container.logRepository.addLog(
            "$DEBUG_PREFIX call captured for ${call.number.ifBlank { "unknown number" }}$simLabel."
        )

        val durationText = if (call.durationSeconds > 0) {
            val mins = call.durationSeconds / 60
            val secs = call.durationSeconds % 60
            if (mins > 0) "$mins min $secs sec" else "$secs sec"
        } else {
            "0 sec"
        }

        val typeLabel = if (call.callType.equals("Missed", ignoreCase = true)) "MISSED CALL" else "PHONE CALL"

        appContext.appContainer.eventBatchQueueManager.enqueue(
            BatchQueueEvent(
                sourceTag = contactInfo,
                enabledKey = SmtpConfigProvider.KEY_CALL_LOGS_ENABLED,
                eventType = EventType.CALL,
                identity = call.entryId.toString(),
                contentPreview = "${call.callType} Call | Duration: $durationText",
                detailBody = EventFormatter.formatEventHtml(
                    type = EventType.CALL,
                    source = contactInfo,
                    timestampMillis = call.timestampMillis,
                    content = "Type: ${call.callType} Call\nDuration: $durationText",
                    customTypeLabel = typeLabel
                ),
                timestampMillis = call.timestampMillis,
                dedupeKey = call.dedupeKey,
                customTypeLabel = typeLabel
            )
        )
    }

    private fun queryNewCalls(uri: Uri, sinceId: Long): List<CallLogEvent> {
        val results = mutableListOf<CallLogEvent>()
        if (sinceId == -1L) return emptyList()

        val selection = "${CallLog.Calls._ID} > ? AND ${CallLog.Calls.TYPE} IN (?, ?, ?, ?)"
        val selectionArgs = arrayOf(
            sinceId.toString(),
            CallLog.Calls.MISSED_TYPE.toString(),
            CallLog.Calls.INCOMING_TYPE.toString(),
            CallLog.Calls.OUTGOING_TYPE.toString(),
            CallLog.Calls.REJECTED_TYPE.toString()
        )

        val sortOrder = "${CallLog.Calls._ID} ASC"

        try {
            // Always query the base URI to ensure we don't miss records if URI is row-specific
            appContext.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                null, // Use null to get all columns safely
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    results.add(cursor.toCallLogEvent())
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "$DEBUG_PREFIX query failed", e)
        }
        return results
    }

    private fun Cursor.toCallLogEvent(): CallLogEvent {
        val id = getLong(getColumnIndexOrThrow(CallLog.Calls._ID))
        val number = getString(getColumnIndexOrThrow(CallLog.Calls.NUMBER)).orEmpty()
        val typeValue = getInt(getColumnIndexOrThrow(CallLog.Calls.TYPE))
        val date = getLong(getColumnIndexOrThrow(CallLog.Calls.DATE))
        val durationSeconds = getLong(getColumnIndexOrThrow(CallLog.Calls.DURATION))
        val cachedNameIndex = getColumnIndex(CallLog.Calls.CACHED_NAME)
        val cachedName = if (cachedNameIndex >= 0) getString(cachedNameIndex) else null

        val subIdIndex = getColumnIndex("subscription_id")
        val subscriptionId = if (subIdIndex >= 0 && !isNull(subIdIndex)) getInt(subIdIndex) else null

        return CallLogEvent(
            entryId = id,
            number = number,
            cachedName = cachedName,
            callType = mapCallType(typeValue),
            timestampMillis = date,
            durationSeconds = durationSeconds,
            subscriptionId = subscriptionId,
            dedupeKey = buildCallDedupeKey(number, cachedName, date)
        )
    }

    private fun buildCallDedupeKey(
        number: String,
        cachedName: String?,
        timestampMillis: Long
    ): String {
        // Normalize the identity (remove non-digits from number, or use lowercase name)
        val identity = if (!cachedName.isNullOrBlank()) {
            cachedName.replace(Regex("[^a-zA-Z0-9]"), "").lowercase()
        } else {
            number.replace(Regex("[^0-9]"), "")
        }
        
        // 30-second window is enough to bridge the gap between Notification and CallLog
        val window = timestampMillis / 30000
        return "call_event|$identity|$window"
    }

    private fun getMaxCallId(): Long {
        try {
            appContext.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls._ID),
                null,
                null,
                "${CallLog.Calls._ID} DESC LIMIT 1"
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    return cursor.getLong(0)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "$DEBUG_PREFIX failed to get max ID", e)
        }
        return -1L
    }

    private fun mapCallType(typeValue: Int): String {
        return when (typeValue) {
            CallLog.Calls.INCOMING_TYPE -> "Incoming"
            CallLog.Calls.OUTGOING_TYPE -> "Outgoing"
            CallLog.Calls.MISSED_TYPE -> "Missed"
            CallLog.Calls.VOICEMAIL_TYPE -> "Voicemail"
            CallLog.Calls.REJECTED_TYPE -> "Rejected"
            CallLog.Calls.BLOCKED_TYPE -> "Blocked"
            CallLog.Calls.ANSWERED_EXTERNALLY_TYPE -> "Answered Externally"
            else -> "Unknown"
        }
    }

    companion object {
        private const val TAG = "CallLogObserver"
        private const val DEBUG_PREFIX = "[CALL]"
        private const val PREFS_NAME = "call_log_observer"
        private const val KEY_LAST_PROCESSED_CALL_ID = "last_processed_call_id"
    }
}

data class CallLogEvent(
    val entryId: Long,
    val number: String,
    val cachedName: String?,
    val callType: String,
    val timestampMillis: Long,
    val durationSeconds: Long,
    val subscriptionId: Int?,
    val dedupeKey: String
)
