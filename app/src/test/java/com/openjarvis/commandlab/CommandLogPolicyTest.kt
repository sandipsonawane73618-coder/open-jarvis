package com.openjarvis.commandlab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandLogPolicyTest {
    @Test
    fun sensitiveCredentialsAreNeverKeptInCommandText() {
        assertEquals("[REDACTED: sensitive content]", CommandLogPolicy.redact("my OTP is 482913"))
        assertEquals("[REDACTED: sensitive content]", CommandLogPolicy.redact("password: secret123"))
        assertEquals("[REDACTED: sensitive content]", CommandLogPolicy.redact("UPI PIN 1234"))
    }

    @Test
    fun contactDetailsAreRedactedFromSharedText() {
        assertTrue(CommandLogPolicy.redact("call 9876543210").orEmpty().contains("[REDACTED: phone]"))
        assertTrue(CommandLogPolicy.redact("mail me at jarvis@example.com").orEmpty().contains("[REDACTED: email]"))
    }

    @Test
    fun onlyLastHundredEntriesAreRetained() {
        val entries = (1..140).map {
            CommandLogEntry(it.toString(), it.toLong(), "command", null, "en", "step", "typed")
        }
        val retained = CommandLogPolicy.keepLast100(entries)
        assertEquals(100, retained.size)
        assertEquals("41", retained.first().id)
        assertEquals("140", retained.last().id)
    }
}
