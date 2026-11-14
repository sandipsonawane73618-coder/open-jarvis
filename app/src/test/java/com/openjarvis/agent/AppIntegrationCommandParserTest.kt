package com.openjarvis.agent

import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppIntegrationCommandParserTest {
    @Test
    fun instagramSearchExtractsOnlyTheSearchPhrase() {
        assertEquals(
            "Shah Rukh Khan",
            AppIntegrationCommandParser.parseInstagramSearch(
                "Instagram kholo aur Shah Rukh Khan search karo"
            )
        )
        assertEquals(
            "Virat Kohli",
            AppIntegrationCommandParser.parseInstagramSearch("insta par Virat Kohli search karo")
        )
    }

    @Test
    fun openingInstagramAloneIsNotMisclassifiedAsSearch() {
        assertNull(AppIntegrationCommandParser.parseInstagramSearch("Instagram kholo"))
    }

    @Test
    fun telegramMessageCreatesDraftAndNeverAuthorizesAutomaticSend() {
        val request = AppIntegrationCommandParser.parseTelegramMessage(
            "Telegram par Rahul ko message bhejo: Hello kal milte hain"
        )
        assertEquals("Rahul", request?.contactName)
        assertEquals("Hello kal milte hain", request?.message)
        assertFalse(request?.sendImmediately ?: true)
    }

    @Test
    fun telegramMessageRejectsMissingRecipientOrBody() {
        assertNull(AppIntegrationCommandParser.parseTelegramMessage("Telegram par message bhejo: Hello"))
        assertNull(AppIntegrationCommandParser.parseTelegramMessage("Telegram par Rahul ko message bhejo:"))
    }

    @Test
    fun calendarEventRequiresExplicitIsoDateAndTimeAndParsesDuration() {
        val request = AppIntegrationCommandParser.parseCalendarEvent(
            "Calendar event Team sync on 2026-11-14 at 15:30 for 45 minutes"
        )
        assertEquals("Team sync", request?.title)
        assertEquals(LocalDateTime.parse("2026-11-14T15:30:00"), request?.start)
        assertEquals(45, request?.durationMinutes)
    }

    @Test
    fun calendarEventWithoutAnExplicitDateAndTimeIsNotGuessed() {
        assertNull(AppIntegrationCommandParser.parseCalendarEvent("Calendar event Team sync tomorrow"))
    }

    @Test
    fun existingWhatsappAndYoutubeRoutesRemainAvailable() {
        val message = MessageCommandParser.parse("Rahul ko WhatsApp pe hello")
        assertEquals("Rahul", message?.contactName)
        assertFalse(message?.sendNow ?: true)
        assertEquals("arijeet singh", SearchCommandParser.parseYouTubeQuery(
            "YouTube par arijeet singh search karo"
        ))
        assertTrue(StepIntentParser.parse("Telegram kholo") is StepIntent.AppLaunch)
    }
}
