package com.notify2email.app.util

import android.util.Patterns

object EmailValidator {
    /**
     * Validates a single email address.
     */
    fun isValid(email: String?): Boolean {
        if (email.isNullOrBlank()) return false
        return Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()
    }

    /**
     * Validates a list of email addresses.
     * Returns true if all provided emails are valid.
     * Empty list is considered valid.
     */
    fun areAllValid(emails: List<String>): Boolean {
        return emails.all { it.isBlank() || isValid(it) }
    }

    /**
     * Normalizes a list of emails by trimming, filtering blanks, and removing duplicates.
     * deduplicates against primary email and other lists.
     */
    fun normalizeRecipients(
        emails: List<String>,
        primaryEmail: String,
        excludeFrom: List<String> = emptyList()
    ): List<String> {
        val normalizedPrimary = primaryEmail.trim().lowercase()
        val normalizedExclude = excludeFrom.map { it.trim().lowercase() }.toSet()
        
        return emails
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .filter { it.lowercase() != normalizedPrimary }
            .filter { it.lowercase() !in normalizedExclude }
            .distinctBy { it.lowercase() }
            .toList()
    }
}
