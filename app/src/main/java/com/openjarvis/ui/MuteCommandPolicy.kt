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
        "chup ho ja",
        "jarvis mute ho jao",
        "mute ho jao",
        "mute ho ja",
        "shant bas jarvis",
        "shant bas bhai",
        "shant baitho jarvis",
        "shant baitho",
        "shant ho ja",
        "gap bas bhai",
        "bas bhai",
        "bas kar jarvis",
        "jarvis silent mode",
        "silent mode jarvis",
        "jarvis chup",
        "चुप हो जाओ",
        "जार्विस चुप हो जाओ",
        "शांत बैठो",
        "शांत बस",
        "शांत बस जार्विस",
        "बस कर जार्विस",
        "गप्प बस",
        "गप्प बसा"
    )

    fun isUnmuteCommand(command: String): Boolean {
        val normalized = normalize(command)
        return normalized in setOf(
            "unmute", "unmute jarvis", "jarvis unmute", "unmute ho ja", "unmute ho jao",
            "jarvis unmute ho ja", "jarvis unmute ho jao", "awaaz chalu karo",
            "आवाज़ चालू करो", "आवाज चालू करो", "पुन्हा सुरू हो", "आवाज सुरू कर"
        )
    }

    fun isMuteCommand(command: String): Boolean {
        val normalized = normalize(command)
        return normalized in mutePhrases
    }

    private fun normalize(command: String): String {
        return Normalizer.normalize(command, Normalizer.Form.NFC).lowercase()
            // Keep Unicode combining marks: Devanagari matras are not L/N characters.
            .replace(Regex("[^\\p{L}\\p{N}\\p{M}]+"), " ")
            .trim()
            .replace(Regex("\\s+"), " ")
    }

}
