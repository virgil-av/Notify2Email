package com.notify2email.app.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat

class ContactNameResolver(private val context: Context) {
    fun resolve(phoneNumber: String?): String? = runCatching {
        if (phoneNumber.isNullOrBlank()) {
            return@runCatching null
        }
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
        
        if (!granted) {
            return@runCatching null
        }
        
        val uri = android.net.Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            android.net.Uri.encode(phoneNumber)
        )
        
        context.contentResolver.query(
            uri,
            arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                return@runCatching cursor.getString(0)
            }
        }
        null
    }.getOrNull()
}
