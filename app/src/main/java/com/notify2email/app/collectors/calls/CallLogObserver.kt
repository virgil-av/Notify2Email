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
    private var lastProcessedEntryId: Long = preferences.getLong(KEY_LAST_PROCESSED_CALL_ID, -1L)
    private val processingMutex = Mutex()

    fun register() {
        Log.i(TAG, "$DEBUG_PREFIX observer registered on ${CallLog.Calls.CONTENT_URI}")
        appContext.contentResolver.registerContentObserver(
            CallLog.Calls.CONTENT_URI,
            true,
            this
        )
        // Perform initial catch-up scan
        observerScope.launch {
            processNewCalls(CallLog.Calls.CONTENT_URI)
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

        Log.i(TAG, "$DEBUG_PREFIX observer triggered (uri=$uri)")
        observerScope.launch {
            container.logRepository.addLog("SYSTEM: Call Log change detected.")
        }

        processNewCalls(uri ?: CallLog.Calls.CONTENT_URI)
    }

    private fun processNewCalls(uri: Uri) {
        val container = appContext.appContainer

        // 1. Check global service status
        if (!container.serviceStateRepository.isServiceRunning()) {
            Log.d(TAG, "$DEBUG_PREFIX skipping because service is not running")
            observerScope.launch {
                container.logRepository.addLog("$DEBUG_PREFIX ignored: service state is OFF.")
            }
            return
        }

        // 2. Check feature enablement
        if (!container.smtpConfigProvider.isFeatureEnabled(SmtpConfigProvider.KEY_CALL_LOGS_ENABLED)) {
            Log.d(TAG, "$DEBUG_PREFIX skipping because call log feature is disabled")
            observerScope.launch {
                container.logRepository.addLog("$DEBUG_PREFIX ignored: Call feature is disabled in settings.")
            }
            return
        }

        observerScope.launch {
            processingMutex.withLock {
                try {
                    // Give the system a moment to finish writing the log entry
                    delay(1500)

                    if (ContextCompat.checkSelfPermission(
                            appContext,
                            android.Manifest.permission.READ_CALL_LOG
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        Log.w(TAG, "$DEBUG_PREFIX skipping read because READ_CALL_LOG is not granted")
                        container.logRepository.addLog(
                            "$DEBUG_PREFIX error: READ_CALL_LOG permission not granted."
                        )
                        return@withLock
                    }

                    var currentLastId = lastProcessedEntryId
                    var newCalls = queryNewCalls(uri, currentLastId)

                    // Retry once if empty, some devices are slow to write
                    if (newCalls.isEmpty() && currentLastId != -1L) {
                        Log.d(TAG, "$DEBUG_PREFIX no new calls found, retrying in 2 seconds...")
                        delay(2000)
                        newCalls = queryNewCalls(uri, currentLastId)
                    }

                    if (newCalls.isEmpty()) {
                        if (currentLastId == -1L) {
                            Log.d(TAG, "$DEBUG_PREFIX no history found, seeding baseline")
                            seedLastProcessedId(uri)
                        } else {
                            Log.d(TAG, "$DEBUG_PREFIX no new entries found after retry")
                        }
                        return@withLock
                    }

                    Log.i(TAG, "$DEBUG_PREFIX found ${newCalls.size} new calls to process")

                    for (call in newCalls) {
                        processSingleCall(call)

                        // Update the tracker
                        lastProcessedEntryId = call.entryId
                        preferences.edit()
                            .putLong(KEY_LAST_PROCESSED_CALL_ID, lastProcessedEntryId)
                            .apply()
                    }

                } catch (error: Exception) {
                    Log.e(TAG, "$DEBUG_PREFIX failed to process call log change", error)
                    container.logRepository.addLog(
                        "$DEBUG_PREFIX error while processing call log change: ${error.message ?: error.javaClass.simpleName}"
                    )
                }
            }
        }
    }

    private suspend fun seedLastProcessedId(uri: Uri) {
        val projection = arrayOf(CallLog.Calls._ID)
        val sortOrder = "${CallLog.Calls._ID} DESC LIMIT 1"

        try {
            appContext.contentResolver.query(
                uri,
                projection,
                null,
                null,
                sortOrder
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    lastProcessedEntryId = cursor.getLong(cursor.getColumnIndexOrThrow(CallLog.Calls._ID))
                    preferences.edit()
                        .putLong(KEY_LAST_PROCESSED_CALL_ID, lastProcessedEntryId)
                        .apply()
                    Log.i(TAG, "$DEBUG_PREFIX seeded lastProcessedEntryId with $lastProcessedEntryId")
                } else {
                    // Log is empty, that's fine
                    lastProcessedEntryId = 0L
                    Log.i(TAG, "$DEBUG_PREFIX call log is empty, baseline set to 0")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "$DEBUG_PREFIX failed to seed ID", e)
        }
    }

    private suspend fun processSingleCall(call: CallLogEvent) {
        Log.i(
            TAG,
            "$DEBUG_PREFIX call detected for ${call.number.ifBlank { "unknown number" }} at ${call.timestampMillis} (SIM: ${call.subscriptionId ?: "Unknown"})"
        )
        appContext.appContainer.logRepository.addLog(
            "$DEBUG_PREFIX call captured for ${call.number.ifBlank { "unknown number" }} ${if (call.subscriptionId != null) "on SIM ${call.subscriptionId}" else ""}."
        )

        val simLabel = if (call.subscriptionId != null) " [SIM ${call.subscriptionId}]" else ""
        val contactInfo = if (!call.cachedName.isNullOrBlank()) {
            "${call.cachedName} (${call.number})$simLabel"
        } else {
            "${call.number.ifBlank { "Unknown Number" }}$simLabel"
        }

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
        val projection = mutableListOf(
            CallLog.Calls._ID,
            CallLog.Calls.NUMBER,
            CallLog.Calls.TYPE,
            CallLog.Calls.DATE,
            CallLog.Calls.DURATION,
            CallLog.Calls.CACHED_NAME
        )

        // Add subscription ID for dual SIM/eSIM support if available
        val subIdColumn = "subscription_id"
        projection.add(subIdColumn)

        val selection: String
        val selectionArgs: Array<String>

        if (sinceId == -1L) {
            // First run: catch up with anything in the last 10 minutes to avoid missing
            // the call that might have triggered the app startup/service start.
            val tenMinutesAgo = System.currentTimeMillis() - (10 * 60 * 1000)
            selection = "${CallLog.Calls.DATE} > ? AND ${CallLog.Calls.TYPE} IN (?, ?, ?, ?)"
            selectionArgs = arrayOf(
                tenMinutesAgo.toString(),
                CallLog.Calls.MISSED_TYPE.toString(),
                CallLog.Calls.INCOMING_TYPE.toString(),
                CallLog.Calls.OUTGOING_TYPE.toString(),
                CallLog.Calls.REJECTED_TYPE.toString()
            )
            Log.d(TAG, "$DEBUG_PREFIX first run, looking back 10 mins")
        } else {
            selection = "${CallLog.Calls._ID} > ? AND ${CallLog.Calls.TYPE} IN (?, ?, ?, ?)"
            selectionArgs = arrayOf(
                sinceId.toString(),
                CallLog.Calls.MISSED_TYPE.toString(),
                CallLog.Calls.INCOMING_TYPE.toString(),
                CallLog.Calls.OUTGOING_TYPE.toString(),
                CallLog.Calls.REJECTED_TYPE.toString()
            )
        }

        val sortOrder = "${CallLog.Calls._ID} ASC"

        try {
            appContext.contentResolver.query(
                uri,
                projection.toTypedArray(),
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
            dedupeKey = buildCallDedupeKey(number, typeValue, date, durationSeconds, subscriptionId)
        )
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

    private fun buildCallDedupeKey(
        number: String,
        callType: Int,
        timestampMillis: Long,
        durationSeconds: Long,
        subscriptionId: Int?
    ): String {
        val raw = "$number|$callType|$timestampMillis|$durationSeconds|$subscriptionId"
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(raw.toByteArray(Charsets.UTF_8))
            .joinToString(separator = "") { byte -> "%02x".format(byte) }
        return "$number|$timestampMillis|$digest"
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
