package com.openjarvis.ui

import java.text.Normalizer

/** Exact voice controls; these phrases are intercepted before command planning. */
object MuteCommandPolicy {
    fun isMuteCommand(command: String): Boolean =
        normalize(command) in setOf("chup ho jao", "jarvis chup ho jao", "चुप हो जाओ", "जार्विस चुप हो जाओ")

    fun isUnmuteCommand(command: String): Boolean =
        normalize(command) in setOf("ab bolo", "अब बोलो")

    private fun normalize(command: String): String =
        Normalizer.normalize(command, Normalizer.Form.NFC)
            .lowercase()
            .replace(Regex("[^\\p{L}\\p{N}\\p{M}]+"), " ")
            .trim()
            .replace(Regex("\\s+"), " ")
}
