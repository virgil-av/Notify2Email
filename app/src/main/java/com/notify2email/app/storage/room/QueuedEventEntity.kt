package com.notify2email.app.storage.room

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "queued_events")
data class QueuedEventEntity(
    @PrimaryKey
    val id: String,
    val type: String,
    val enabledKey: String,
    val sourceTag: String,
    val identity: String,
    val contentPreview: String,
    val detailBody: String,
    val timestampMillis: Long,
    val enqueuedAtMillis: Long,
    val dedupeKey: String?,
    val customTypeLabel: String? = null
)
