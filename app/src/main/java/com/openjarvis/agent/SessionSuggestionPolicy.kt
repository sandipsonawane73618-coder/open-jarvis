package com.openjarvis.agent

import java.util.Locale

/** Pure policy for a single, user-approved proactive suggestion per app/wake session. */
internal object SessionSuggestionPolicy {
    fun choose(
        sessionId: String,
        alreadyShown: Boolean,
        memoryNotes: List<String>,
        hourOfDay: Int
    ): String? {
        if (sessionId.isBlank() || alreadyShown || hourOfDay !in 0..23) return null
        val note = memoryNotes.firstOrNull { it.isNotBlank() }?.trim()?.take(100) ?: return null
        val timePrefix = when (hourOfDay) {
            in 5..11 -> "Aaj subah"
            in 12..16 -> "Aaj dopahar"
            in 17..21 -> "Aaj shaam"
            else -> "Aaj"
        }
        return "$timePrefix aapka $note hai, reminder lagau?"
    }

    fun mayAct(reply: String): Boolean =
        reply.trim().lowercase(Locale.ROOT) in setOf("yes", "yes please", "haan", "ha", "हो", "होय")
}
