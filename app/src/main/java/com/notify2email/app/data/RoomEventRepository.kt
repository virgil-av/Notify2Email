package com.notify2email.app.data

import com.notify2email.app.domain.model.Event
import com.notify2email.app.domain.model.EventType
import com.notify2email.app.domain.model.SentStatus
import com.notify2email.app.domain.repository.EventRepository
import com.notify2email.app.storage.room.EventDao
import com.notify2email.app.storage.room.toDomain
import com.notify2email.app.storage.room.toEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomEventRepository(
    private val eventDao: EventDao,
    private val queueDao: com.notify2email.app.storage.room.QueuedEventDao
) : EventRepository {

    override fun observeEvents(): Flow<List<Event>> {
        return eventDao.observeAll().map { entities -> entities.map { it.toDomain() } }
    }

    override suspend fun getEvents(): List<Event> {
        return eventDao.getAll().map { it.toDomain() }
    }

    override suspend fun saveEvent(event: Event) {
        eventDao.insert(event.toEntity())
    }

    override suspend fun getLastEvent(): Event? {
        return eventDao.getLatest()?.toDomain()
    }

    override suspend fun getSentCount(type: EventType): Int {
        return eventDao.countByTypeAndStatus(type.name, SentStatus.SENT.name)
    }

    override suspend fun deleteEvent(id: String) {
        eventDao.deleteById(id)
    }

    override suspend fun deleteAllEvents() {
        eventDao.deleteAll()
    }

    override fun observeOldestQueueTime(): Flow<Long?> {
        return queueDao.observeOldestEnqueuedAt()
    }
}
