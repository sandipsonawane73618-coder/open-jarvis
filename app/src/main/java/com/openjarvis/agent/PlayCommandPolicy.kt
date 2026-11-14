package com.openjarvis.agent

import java.util.Locale

/** Deterministic clarification for an unqualified play command when YouTube is not foreground. */
internal object PlayCommandPolicy {
    private val missingQuery = Regex(
        "^(?:please\\s+)?(?:play(?:\\s+(?:karo|a|the))?(?:\\s+song)?|chalao|चलाओ)(?:\\s+please)?$",
        RegexOption.IGNORE_CASE
    )

    fun shouldAskForQuery(command: String, youtubeForeground: Boolean): Boolean {
        if (youtubeForeground) return false
        val normalized = VoiceTextNormalizer.normalize(command).trim().lowercase(Locale.ROOT)
            .replace(Regex("\\s+"), " ")
        return missingQuery.matches(normalized)
    }
}