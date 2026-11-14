package com.openjarvis.intelligence

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.provider.Telephony
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class ConnectedAppsReader(private val context: Context) {
    suspend fun contactNumber(name: String): String = withContext(Dispatchers.IO) {
        if (!RuntimePermissionRequestBroker.ensure(context, Manifest.permission.READ_CONTACTS)) {
            throw SecurityException(PermissionStatusMessage.denied("READ_CONTACTS"))
        }
        val wanted = name.trim()
        require(wanted.isNotBlank()) { "Contact name is empty." }
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection, null, null,
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} COLLATE NOCASE ASC"
        )?.use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numberIndex = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (cursor.moveToNext()) {
                val displayName = cursor.getString(nameIndex).orEmpty()
                if (displayName.equals(wanted, ignoreCase = true)) {
                    return@withContext cursor.getString(numberIndex).orEmpty()
                }
            }
        }
        throw IllegalStateException("Contact '$wanted' was not found in your contacts.")
    }

    suspend fun calendarEvents(dayOffset: Int): List<String> = withContext(Dispatchers.IO) {
        if (!RuntimePermissionRequestBroker.ensure(context, Manifest.permission.READ_CALENDAR)) {
            throw SecurityException(PermissionStatusMessage.denied("READ_CALENDAR"))
        }
        val day = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, dayOffset.coerceIn(0, 2))
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val start = day.timeInMillis
        day.add(Calendar.DAY_OF_YEAR, 1)
        val end = day.timeInMillis
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
            .appendPath(start.toString()).appendPath(end.toString()).build()
        val projection = arrayOf(CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN)
        val results = mutableListOf<Pair<Long, String>>()
        context.contentResolver.query(uri, projection, null, null, CalendarContract.Instances.BEGIN + " ASC")?.use { cursor ->
            val titleIndex = cursor.getColumnIndexOrThrow(CalendarContract.Instances.TITLE)
            val beginIndex = cursor.getColumnIndexOrThrow(CalendarContract.Instances.BEGIN)
            while (cursor.moveToNext()) {
                val title = cursor.getString(titleIndex).orEmpty().take(120)
                val begin = cursor.getLong(beginIndex)
                if (title.isNotBlank()) results += begin to title
            }
        }
        val format = SimpleDateFormat("h:mm a", Locale.getDefault())
        results.take(20).map { (time, title) -> "${format.format(Date(time))}: $title" }
    }

    suspend fun lastSmsSender(): String = withContext(Dispatchers.IO) {
        if (!RuntimePermissionRequestBroker.ensure(context, Manifest.permission.READ_SMS)) {
            throw SecurityException(PermissionStatusMessage.denied("READ_SMS"))
        }
        val projection = arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.DATE)
        context.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI, projection, null, null, Telephony.Sms.DATE + " DESC LIMIT 1"
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val address = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)).orEmpty()
                val date = cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Sms.DATE))
                if (address.isNotBlank()) {
                    val time = SimpleDateFormat("d MMM, h:mm a", Locale.getDefault()).format(Date(date))
                    return@withContext "Last SMS sender: $address ($time). Message content was not read or stored."
                }
            }
        }
        "No SMS was found in the inbox."
    }
}
