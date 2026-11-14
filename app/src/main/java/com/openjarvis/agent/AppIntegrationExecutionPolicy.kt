package com.openjarvis.agent

import java.net.URLEncoder
import java.time.LocalDateTime

/**
 * Pure intent contracts for the supported app integrations.
 * Android execution is deliberately separate so these decisions stay JVM-testable.
 */
internal object AppIntegrationExecutionPolicy {
    internal data class Plan(
        val action: String,
        val packageName: String? = null,
        val uri: String? = null,
        val mimeType: String? = null,
        val text: String? = null,
        val title: String? = null,
        val start: LocalDateTime? = null,
        val durationMinutes: Int? = null,
        val requiresUserSelection: Boolean = false,
        val automaticSend: Boolean = false,
        val requiresUserConfirmation: Boolean = false
    )

    private fun encoded(value: String): String = URLEncoder.encode(value.trim(), "UTF-8")

    fun appPackage(label: String): String? = when (label.trim().lowercase()) {
        "whatsapp", "wa" -> "com.whatsapp"
        "instagram", "insta", "ig" -> "com.instagram.android"
        "youtube", "yt" -> "com.google.android.youtube"
        "telegram" -> "org.telegram.messenger"
        "calendar", "google calendar" -> "com.google.android.calendar"
        else -> null
    }

    fun instagramSearch(query: String): Plan? {
        if (query.isBlank()) return null
        return Plan(
            action = "android.intent.action.VIEW",
            packageName = "com.instagram.android",
            uri = "https://www.instagram.com/explore/search/keyword/?q=${encoded(query)}"
        )
    }

    fun telegramMessage(message: String): Plan? {
        if (message.isBlank()) return null
        return Plan(
            action = "android.intent.action.SEND",
            packageName = "org.telegram.messenger",
            mimeType = "text/plain",
            text = message.trim(),
            requiresUserSelection = true,
            automaticSend = false
        )
    }

    fun calendarEvent(title: String, start: LocalDateTime, durationMinutes: Int): Plan? {
        if (title.isBlank() || durationMinutes !in 1..1440) return null
        return Plan(
            action = "android.intent.action.INSERT",
            uri = "content://com.android.calendar/events",
            title = title.trim(),
            start = start,
            durationMinutes = durationMinutes,
            requiresUserConfirmation = true
        )
    }

    fun youtubeSearch(query: String): Plan? {
        if (query.isBlank()) return null
        return Plan(
            action = "android.intent.action.VIEW",
            packageName = "com.google.android.youtube",
            uri = "https://www.youtube.com/results?search_query=${encoded(query)}"
        )
    }
}
