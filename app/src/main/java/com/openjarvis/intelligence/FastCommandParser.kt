package com.openjarvis.intelligence

internal object FastCommandParser {
    private val englishOpen = Regex("^(?:please\\s+)?(?:open|launch)\\s+(.+)$", RegexOption.IGNORE_CASE)
    private val hindiOpen = Regex("^(.+?)\\s+(?:kholo|khol do|open karo)$", RegexOption.IGNORE_CASE)

    fun openAppTarget(command: String): String? {
        val normalized = command.trim().replace(Regex("\\s+"), " ")
        if (normalized.isBlank()) return null
        val match = englishOpen.matchEntire(normalized) ?: hindiOpen.matchEntire(normalized) ?: return null
        return match.groupValues[1].trim().takeIf { it.isNotBlank() && it.length <= 60 }
    }
}
