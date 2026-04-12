package com.notify2email.app.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object TimeUtils {
    /**
     * Formats a timestamp into a human-readable string: yyyy-MM-dd HH:mm
     * This function is locale-safe and crash-proof.
     */
    fun formatDateTime(timestampMillis: Long): String {
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            sdf.format(Date(timestampMillis))
        } catch (e: Exception) {
            "Unknown time"
        }
    }
}
