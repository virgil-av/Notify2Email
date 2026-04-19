package com.notify2email.app.storage.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface QueuedEventDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: QueuedEventEntity)

    @Query("SELECT * FROM queued_events ORDER BY enqueuedAtMillis ASC")
    suspend fun getAllOrdered(): List<QueuedEventEntity>

    @Query("SELECT MIN(enqueuedAtMillis) FROM queued_events")
    suspend fun getOldestEnqueuedAt(): Long?

    @Query("SELECT COUNT(*) FROM queued_events")
    suspend fun getCount(): Int

    @Query(
        "SELECT COUNT(*) FROM queued_events WHERE type = :type AND dedupeKey = :dedupeKey AND timestampMillis >= :sinceMillis"
    )
    suspend fun countRecentDuplicates(
        type: String,
        dedupeKey: String,
        sinceMillis: Long
    ): Int

    @Query("DELETE FROM queued_events WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Query("DELETE FROM queued_events WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT MIN(enqueuedAtMillis) FROM queued_events")
    fun observeOldestEnqueuedAt(): Flow<Long?>
}
