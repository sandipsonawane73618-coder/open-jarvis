package com.openjarvis.agent

/**
 * Routes unambiguous single-app launch requests locally, without spending an
 * Ollama inference. Supports English, Roman Hindi/Marathi and Devanagari verbs.
 */
internal object QuickCommandRouter {
    private val patterns = listOf(
        Regex("^(?:please\\s+)?(?:open|launch|start)\\s+(.+)$", RegexOption.IGNORE_CASE),
        Regex("^(.+?)\\s+(?:kholo|khol|khol do|ughad|ughaḍ|open karo|open kar)$", RegexOption.IGNORE_CASE),
        Regex("^(?:मुझे\\s+|मला\\s+)?(.+?)\\s+(?:खोलो|खोल दो|उघडा|उघड|उघडून दे)$")
    )

    fun parse(command: String): Action? {
        val normalized = command.trim().trimEnd('.', '!', '?')
        val label = patterns.firstNotNullOfOrNull { pattern ->
            pattern.matchEntire(normalized)?.groupValues?.getOrNull(1)?.trim()
        } ?: return null
        if (label.isBlank() || label.length > 50) return null

        // Do not mistake compound tasks for a simple app launch.
        val compound = Regex(
            "\\b(and|then|after|before|aur|phir|uske baad|ani|mag|nantar)\\b",
            RegexOption.IGNORE_CASE
        )
        if (compound.containsMatchIn(label)) return null
        if (label.any { it.code in 0x0900..0x097F } &&
            listOf("और", "फिर", "नंतर", "आणि", "मग").any { label.contains(it) }) return null

        return Action(action = Action.OPEN_APP, label = label)
    }
}
