package com.notify2email.app.storage.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LogEntryDao {
    @Query("SELECT * FROM logs ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<LogEntryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: LogEntryEntity)

    @Query("DELETE FROM logs")
    suspend fun clearAll()
}
