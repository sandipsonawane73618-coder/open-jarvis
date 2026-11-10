package com.openjarvis.agent

/** Keeps spoken feedback aligned with the actual task result. */
internal object AgentResultSpeech {
    fun textForDone(result: String): String = result.trim().ifBlank { "Command completed." }
}

/** Single-use time window for the contact-name follow-up after "call karo na". */
internal class ContactFollowUpWindow(private val durationMs: Long = 30_000L) {
    private var expiresAtMs: Long = 0L
    fun start(nowMs: Long) { expiresAtMs = nowMs + durationMs }
    fun consume(nowMs: Long): Boolean {
        val valid = expiresAtMs > 0L && nowMs <= expiresAtMs
        expiresAtMs = 0L
        return valid
    }
}
