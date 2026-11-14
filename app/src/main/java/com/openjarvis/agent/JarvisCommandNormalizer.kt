package com.openjarvis.agent

/**
 * Removes the spoken wake-word only when it appears at the beginning of a
 * command. Words inside search queries, contact names, and message bodies are
 * preserved.
 */
internal object JarvisCommandNormalizer {
    private val LEADING_WAKE_WORD = Regex(
        "^\\s*(?:(?:hey|hi)\\s+)?(?:jarvis|जार्विस)(?:\\s*[,،:;.!?।-]\\s*|\\s+|$)",
        RegexOption.IGNORE_CASE
    )

    fun stripWakeWord(command: String): String =
        VoiceTextNormalizer.stripWakePhrase(command)
}
