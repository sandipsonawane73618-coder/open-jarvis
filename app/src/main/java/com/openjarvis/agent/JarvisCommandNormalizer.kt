package com.openjarvis.agent

/**
 * Removes a spoken wake alias only at the beginning; names and message/search text stay intact.
 */
internal object JarvisCommandNormalizer {
    private val LEADING_WAKE_WORD = Regex(
        "^\\s*(?:(?:hey|hi|हे)\\s+)?(?:jarvis|जार्विस|जरवेस|जार्वीस|एचआरवी|hrv)(?:\\s*[,،:;.!?।-]\\s*|\\s+|$)",
        RegexOption.IGNORE_CASE
    )

    fun stripWakeWord(command: String): String =
        command.replaceFirst(LEADING_WAKE_WORD, "").trim()
}
