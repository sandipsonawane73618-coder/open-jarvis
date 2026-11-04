package com.openjarvis.ui

import java.text.Normalizer

/**
 * Exact, deterministic mute phrases. These are handled before the LLM so a
 * short "be quiet" command never reaches the action planner.
 */
object MuteCommandPolicy {
    private val mutePhrases = setOf(
        "jarvis chup ho jao",
        "chup ho jao",
        "jarvis mute ho jao",
        "mute ho jao",
        "shant bas jarvis",
        "shant baitho jarvis",
        "gap bas bhai",
        "bas kar jarvis",
        "jarvis silent mode",
        "silent mode jarvis",
        "jarvis chup",
        "चुप हो जाओ",
        "जार्विस चुप हो जाओ",
        "शांत बैठो",
        "शांत बस जार्विस",
        "बस कर जार्विस"
    )

    fun isMuteCommand(command: String): Boolean {
        val normalized = Normalizer.normalize(command, Normalizer.Form.NFC).lowercase()
            // Keep Unicode combining marks: Devanagari matras are not L/N characters.
            .replace(Regex("[^\\p{L}\\p{N}\\p{M}]+"), " ")
            .trim()
            .replace(Regex("\\s+"), " ")
        return normalized in mutePhrases
    }
}
