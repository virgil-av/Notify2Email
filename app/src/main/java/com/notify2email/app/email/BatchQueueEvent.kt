package com.notify2email.app.email

import com.notify2email.app.domain.model.EventType

data class BatchQueueEvent(
    val eventType: EventType,
    val enabledKey: String,
    val sourceTag: String,
    val identity: String,
    val contentPreview: String,
    val detailBody: String,
    val timestampMillis: Long,
    val dedupeKey: String? = null,
    val customTypeLabel: String? = null
)
