package com.notify2email.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class EventHistoryStore(context: Context) {

    private val preferences = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun append(record: EventRecord) {
        val existingItems = getEvents()
        val updated = JSONArray()
        updated.put(record.toJson())
        existingItems.forEach { updated.put(it.toJson()) }
        preferences.edit()
            .putString(KEY_EVENTS, updated.toString())
            .apply()
    }

    fun getEvents(): List<EventRecord> {
        val array = readJsonArray()
        return buildList {
            for (index in 0 until array.length()) {
                val json = array.optJSONObject(index) ?: continue
                add(EventRecord.fromJson(json))
            }
        }
    }

    fun deleteEvent(id: String) {
        val updated = JSONArray()
        getEvents()
            .filterNot { it.id == id }
            .forEach { updated.put(it.toJson()) }

        preferences.edit().putString(KEY_EVENTS, updated.toString()).apply()
    }

    fun clearEvents() {
        preferences.edit().putString(KEY_EVENTS, JSONArray().toString()).apply()
    }

    fun getSummary(): DashboardSummary {
        val events = getEvents()
        return DashboardSummary(
            isServiceRunning = preferences.getBoolean(KEY_SERVICE_RUNNING, false),
            smsSent = events.count { it.type == EventType.SMS && it.status == EventStatus.SENT },
            callsSent = events.count { it.type == EventType.CALL && it.status == EventStatus.SENT },
            notificationsSent = events.count { it.type == EventType.NOTIFICATION && it.status == EventStatus.SENT },
            lastEvent = events.maxByOrNull { it.timestampMillis }
        )
    }

    fun setServiceRunning(isRunning: Boolean) {
        preferences.edit().putBoolean(KEY_SERVICE_RUNNING, isRunning).apply()
    }

    private fun readJsonArray(): JSONArray {
        val raw = preferences.getString(KEY_EVENTS, null)
        return if (raw.isNullOrBlank()) JSONArray() else JSONArray(raw)
    }

    companion object {
        private const val PREFS_NAME = "event_history"
        private const val KEY_EVENTS = "events"
        private const val KEY_SERVICE_RUNNING = "service_running"
    }
}

data class DashboardSummary(
    val isServiceRunning: Boolean,
    val smsSent: Int,
    val callsSent: Int,
    val notificationsSent: Int,
    val lastEvent: EventRecord?
)

data class EventRecord(
    val id: String,
    val type: EventType,
    val contentPreview: String,
    val timestampMillis: Long,
    val status: EventStatus
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("type", type.name)
            put("contentPreview", contentPreview)
            put("timestampMillis", timestampMillis)
            put("status", status.name)
        }
    }

    companion object {
        fun fromJson(json: JSONObject): EventRecord {
            return EventRecord(
                id = json.optString("id"),
                type = EventType.valueOf(json.optString("type", EventType.NOTIFICATION.name)),
                contentPreview = json.optString("contentPreview"),
                timestampMillis = json.optLong("timestampMillis"),
                status = EventStatus.valueOf(json.optString("status", EventStatus.SKIPPED.name))
            )
        }
    }
}

enum class EventType {
    SMS,
    CALL,
    NOTIFICATION
}

enum class EventStatus {
    SENT,
    FAILED,
    SKIPPED
}
