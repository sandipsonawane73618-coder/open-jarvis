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
    @Test fun everyTerminalRouteOutcomeCompletesItsPendingCommandLogEntry() {
        val pending = listOf(
            CommandLogEntry("done", 1L, "volume up", null, "hi", "skill", "voice"),
            CommandLogEntry("error", 2L, "torch on", null, "hi", "skill", "voice"),
            CommandLogEntry("clarify", 3L, "play karo", null, "hi", "clarify", "voice"),
            CommandLogEntry("shutdown", 4L, "search", null, "en", "search", "typed")
        )
        val afterDone = CommandLogPolicy.complete(pending, "done", "Volume badha diya")
        val afterError = CommandLogPolicy.complete(afterDone, "error", null, "Torch permission denied")
        val afterClarify = CommandLogPolicy.complete(afterError, "clarify", "Kya play karun?")
        val afterShutdown = CommandLogPolicy.completeAllPending(afterClarify, "Overlay service stopped")
        assertFalse(CommandLogPolicy.hasPending(afterShutdown))
        assertEquals("Volume badha diya", afterShutdown.first { it.id == "done" }.result)
        assertEquals("Torch permission denied", afterShutdown.first { it.id == "error" }.failReason)
        assertEquals("Kya play karun?", afterShutdown.first { it.id == "clarify" }.result)
        assertEquals("Overlay service stopped", afterShutdown.first { it.id == "shutdown" }.failReason)
    }

}
