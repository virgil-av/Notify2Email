package com.notify2email.app.storage.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface EventDao {
    @Query("SELECT * FROM events ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<EventEntity>>

    @Query("SELECT * FROM events ORDER BY timestamp DESC")
    suspend fun getAll(): List<EventEntity>

    @Query("SELECT * FROM events ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatest(): EventEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: EventEntity)

    @Query("DELETE FROM events WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM events")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM events WHERE type = :type AND sentStatus = :sentStatus")
    suspend fun countByTypeAndStatus(type: String, sentStatus: String): Int
}
