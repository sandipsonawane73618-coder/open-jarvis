package com.openjarvis.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class AppIntegrationExecutionPolicyTest {
    @Test
    fun instagramSearchTargetsInstalledInstagramAndEncodesQuery() {
        val plan = AppIntegrationExecutionPolicy.instagramSearch("Shah Rukh Khan")
        assertEquals("com.instagram.android", plan?.packageName)
        assertEquals("android.intent.action.VIEW", plan?.action)
        assertEquals(
            "https://www.instagram.com/explore/search/keyword/?q=Shah+Rukh+Khan",
            plan?.uri
        )
    }

    @Test
    fun telegramMessageOpensShareDraftAndNeverSendsAutomatically() {
        val plan = AppIntegrationExecutionPolicy.telegramMessage("Hello Rahul", "Rahul")
        assertEquals("android.intent.action.SEND", plan?.action)
        assertEquals("org.telegram.messenger", plan?.packageName)
        assertEquals("Rahul", plan?.recipientName)
        assertEquals("text/plain", plan?.mimeType)
        assertEquals("Hello Rahul", plan?.text)
        assertTrue(plan?.requiresUserSelection == true)
        assertFalse(plan?.automaticSend ?: true)
    }

    @Test
    fun calendarEventCreatesInsertRequestWithExplicitStartAndDuration() {
        val start = LocalDateTime.parse("2026-11-14T15:30:00")
        val plan = AppIntegrationExecutionPolicy.calendarEvent("Team sync", start, 45)
        assertEquals("android.intent.action.INSERT", plan?.action)
        assertEquals("content://com.android.calendar/events", plan?.uri)
        assertEquals("Team sync", plan?.title)
        assertEquals(start, plan?.start)
        assertEquals(45, plan?.durationMinutes)
        assertTrue(plan?.requiresUserConfirmation == true)
    }

    @Test
    fun youtubeSearchUsesYoutubeAppTargetAndSearchUri() {
        val plan = AppIntegrationExecutionPolicy.youtubeSearch("lofi music")
        assertEquals("com.google.android.youtube", plan?.packageName)
        assertEquals("android.intent.action.VIEW", plan?.action)
        assertEquals(
            "https://www.youtube.com/results?search_query=lofi+music",
            plan?.uri
        )
    }

    @Test
    fun appLaunchResolvesOnlySupportedKnownPackageNames() {
        assertEquals("com.whatsapp", AppIntegrationExecutionPolicy.appPackage("whatsapp"))
        assertEquals("com.instagram.android", AppIntegrationExecutionPolicy.appPackage("instagram"))
        assertEquals("com.google.android.youtube", AppIntegrationExecutionPolicy.appPackage("youtube"))
        assertEquals("org.telegram.messenger", AppIntegrationExecutionPolicy.appPackage("telegram"))
        assertEquals("com.google.android.calendar", AppIntegrationExecutionPolicy.appPackage("calendar"))
        assertNull(AppIntegrationExecutionPolicy.appPackage("unknown app"))
    }

    @Test
    fun invalidOrBlankRequestsAreRejectedWithoutCreatingPlans() {
        assertNull(AppIntegrationExecutionPolicy.instagramSearch("  "))
        assertNull(AppIntegrationExecutionPolicy.telegramMessage(""))
        assertNull(AppIntegrationExecutionPolicy.calendarEvent(" ", LocalDateTime.now(), 30))
        assertNull(AppIntegrationExecutionPolicy.calendarEvent("Meeting", LocalDateTime.now(), 0))
        assertNull(AppIntegrationExecutionPolicy.calendarEvent("Meeting", LocalDateTime.now(), 1441))
        assertNull(AppIntegrationExecutionPolicy.youtubeSearch(" "))
    }
}
