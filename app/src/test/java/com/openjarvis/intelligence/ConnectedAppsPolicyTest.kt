package com.openjarvis.intelligence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** RED-first contract tests; reflection keeps this test source compilable on baseline. */
class ConnectedAppsPolicyTest {
    private fun singleton(name: String): Any {
        val type = Class.forName(name)
        return type.getField("INSTANCE").get(null)
    }

    private fun invoke(name: String, method: String, vararg args: Any?): Any? {
        val target = singleton(name)
        val candidate = target.javaClass.methods.firstOrNull {
            it.name == method && it.parameterTypes.size == args.size
        } ?: error("Missing policy method: " + name + "." + method)
        return candidate.invoke(target, *args)
    }

    @Test fun otpIsRedactedBeforeNotificationStorage() {
        val redacted = invoke("com.openjarvis.intelligence.NotificationPrivacyPolicy", "redact",
            "Your OTP is 482913. Do not share it.") as String
        assertFalse(redacted.contains("482913"))
        assertTrue(redacted.contains("[REDACTED]"))
    }

    @Test fun pinAndCardContentAreRedacted() {
        val redacted = invoke("com.openjarvis.intelligence.NotificationPrivacyPolicy", "redact",
            "UPI PIN 1234 card ending 4111 1111 1111 1111") as String
        assertFalse(redacted.contains("1234"))
        assertFalse(redacted.contains("4111 1111 1111 1111"))
        assertTrue(redacted.contains("[REDACTED]"))
    }

    @Test fun bankAndPaymentNotificationsAreNeverStored() {
        assertEquals(false, invoke("com.openjarvis.intelligence.NotificationPrivacyPolicy", "shouldStore", "com.phonepe.app"))
        assertEquals(false, invoke("com.openjarvis.intelligence.NotificationPrivacyPolicy", "shouldStore", "com.example.newbank.mobile"))
    }

    @Test fun notificationBufferKeepsOnlyNewestFifty() {
        @Suppress("UNCHECKED_CAST")
        val kept = invoke("com.openjarvis.intelligence.NotificationBufferPolicy", "keepLatest",
            (1L..60L).toList()) as List<Long>
        assertEquals(50, kept.size)
        assertEquals(11L, kept.first())
        assertEquals(60L, kept.last())
    }

    @Test fun notificationReadUtteranceRoutesToNotificationReaderNotChat() {
        assertEquals("NOTIFICATION_READ", invoke("com.openjarvis.intelligence.ConnectedAppCommandParser",
            "route", "koi naya message aaya?"))
    }

    @Test fun whatsappSenderAndReadAllUtterancesRouteToNotificationReader() {
        listOf("WhatsApp par kisne message kiya?", "notifications padho", "sab padh ke sunao").forEach { command ->
            assertEquals(command, "NOTIFICATION_READ",
                invoke("com.openjarvis.intelligence.ConnectedAppCommandParser", "route", command))
        }
    }

    @Test fun calendarParserDistinguishesTodayAndTomorrow() {
        assertEquals(1, invoke("com.openjarvis.intelligence.CalendarQueryParser", "dayOffset", "kal meeting kab hai?"))
        assertEquals(0, invoke("com.openjarvis.intelligence.CalendarQueryParser", "dayOffset", "aaj ke events"))
    }

    @Test fun permissionDenialReturnsHonestActionableMessage() {
        val message = invoke("com.openjarvis.intelligence.PermissionStatusMessage", "denied", "READ_CALENDAR") as String
        assertTrue(message.contains("permission", ignoreCase = true))
        assertTrue(message.contains("denied", ignoreCase = true) ||
            message.contains("allow", ignoreCase = true) || message.contains("enable", ignoreCase = true))
    }

    @Test fun notificationReplyRequiresExplicitExactPayloadApproval() {
        assertEquals(false, invoke("com.openjarvis.intelligence.NotificationReplyPolicy", "maySend",
            "Amit", "ok", "Amit", "ok", false))
        assertEquals(true, invoke("com.openjarvis.intelligence.NotificationReplyPolicy", "maySend",
            "Amit", "ok", "Amit", "ok", true))
        assertEquals(false, invoke("com.openjarvis.intelligence.NotificationReplyPolicy", "maySend",
            "Amit", "ok", "Amit", "not ok", true))
    }
}
