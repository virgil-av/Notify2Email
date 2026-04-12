package com.notify2email.app.storage.room

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "events")
data class EventEntity(
    @PrimaryKey
    val id: String,
    val type: String,
    val source: String,
    val content: String,
    val timestamp: Long,
    val sentStatus: String,
    val errorMessage: String? = null
)
