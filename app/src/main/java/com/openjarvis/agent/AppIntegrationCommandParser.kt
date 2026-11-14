package com.openjarvis.agent

import java.time.LocalDateTime

/**
 * Deterministic, side-effect-free command extraction for supported app integrations.
 *
 * This parser only creates structured drafts. In particular, Telegram messages are
 * never authorized for automatic sending; execution and user confirmation remain
 * separate responsibilities.
 */
object AppIntegrationCommandParser {
    data class TelegramMessageRequest(
        val contactName: String,
        val message: String,
        val sendImmediately: Boolean = false
    )

    data class CalendarEventRequest(
        val title: String,
        val start: LocalDateTime,
        val durationMinutes: Int
    )

    private val instagramSearchPatterns = listOf(
        Regex(
            """^\s*(?:instagram|insta)(?:\s+(?:kholo|open))?(?:\s+(?:par|pe))?\s+(?:aur\s+)?(.+?)\s+search\s+(?:karo|kro|kar)\s*$""",
            RegexOption.IGNORE_CASE
        ),
        Regex(
            """^\s*(?:instagram|insta)\s+search\s+(?:for\s+)?(.+?)\s*$""",
            RegexOption.IGNORE_CASE
        )
    )

    fun parseInstagramSearch(command: String): String? {
        val normalized = command.trim().replace(Regex("""\s+"""), " ")
        val match = instagramSearchPatterns.firstNotNullOfOrNull { it.matchEntire(normalized) }
            ?: return null
        return match.groupValues[1].trim()
            .removeSuffix(" please")
            .trim()
            .takeIf { it.isNotBlank() }
    }

    private val telegramPattern = Regex(
        """^\s*telegram\s+(?:(?:par|pe|on)\s+)?(.+?)\s+(?:ko|la)\s+message\s+(?:bhejo|bhej|send|pathav|pathva)\s*:\s*(.*?)\s*$""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )

    fun parseTelegramMessage(command: String): TelegramMessageRequest? {
        val match = telegramPattern.matchEntire(command.trim()) ?: return null
        val recipient = match.groupValues[1].trim()
        val body = match.groupValues[2].trim()
        if (recipient.isBlank() || body.isBlank()) return null
        return TelegramMessageRequest(
            contactName = recipient,
            message = body,
            sendImmediately = false
        )
    }

    private val calendarPattern = Regex(
        """^\s*calendar\s+event\s+(.+?)\s+on\s+(\d{4}-\d{2}-\d{2})\s+at\s+(\d{2}:\d{2})(?:\s+for\s+(\d{1,3})\s+minutes?)?\s*$""",
        RegexOption.IGNORE_CASE
    )

    fun parseCalendarEvent(command: String): CalendarEventRequest? {
        val match = calendarPattern.matchEntire(command.trim()) ?: return null
        val title = match.groupValues[1].trim()
        if (title.isBlank()) return null

        return try {
            val start = LocalDateTime.parse("${match.groupValues[2]}T${match.groupValues[3]}:00")
            val duration = match.groupValues[4].takeIf { it.isNotBlank() }?.toInt() ?: 60
            if (duration !in 1..1440) return null
            CalendarEventRequest(title, start, duration)
        } catch (_: Exception) {
            null
        }
    }
}
