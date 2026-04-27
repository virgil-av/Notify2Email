package com.notify2email.app.domain.repository

import com.notify2email.app.domain.model.Event
import com.notify2email.app.domain.model.EventType
import kotlinx.coroutines.flow.Flow

interface EventRepository {
    fun observeEvents(): Flow<List<Event>>
    suspend fun getEvents(): List<Event>
    suspend fun saveEvent(event: Event)
    suspend fun getLastEvent(): Event?
    suspend fun getSentCount(type: EventType): Int
    suspend fun deleteEvent(id: String)
    suspend fun deleteAllEvents()
    fun observeOldestQueueTime(): Flow<Long?>
    suspend fun hasRecentCall(withinMillis: Long): Boolean
}
