package com.openjarvis.intelligence

/**
 * Splits compound utterances without losing the text of each step.
 * Search and call parsers should be applied to a complete step before generic parsing.
 */
object CommandSplitter {
    data class Step(val index: Int, val text: String, val inheritedContext: String? = null)

    private val separators = Regex(
        """\s+(?:aur|phir|fir|uske\s+baad|ani|and|then|और|फिर|उसके\s+बाद|आणि)\s+""",
        RegexOption.IGNORE_CASE
    )

    fun split(command: String, maxSteps: Int = MAX_STEPS): List<Step>? {
        if (maxSteps !in 1..MAX_STEPS) return null
        val normalized = command.trim().replace(Regex("\\s+"), " ")
        if (normalized.isBlank()) return null
        val parts = normalized.split(separators).map(String::trim)
        if (parts.any(String::isBlank) || parts.size > maxSteps) return null
        var context: String? = null
        return parts.mapIndexed { index, part ->
            val inherited = context
            // Keep app/contact/query as explicit context for elliptical follow-up steps.
            if (APP_CONTEXT.containsMatchIn(part)) context = APP_CONTEXT.find(part)?.value
            else if (CONTACT_CONTEXT.containsMatchIn(part)) context = CONTACT_CONTEXT.find(part)?.value
            else if (QUERY_CONTEXT.containsMatchIn(part)) context = QUERY_CONTEXT.find(part)?.groupValues?.getOrNull(1)
            Step(index = index, text = part, inheritedContext = inherited)
        }
    }

    private val APP_CONTEXT = Regex("""(?i)\b(?:youtube|chrome|google|whatsapp|spotify|settings)\b""")
    private val CONTACT_CONTEXT = Regex("""(?i)\b(?:to|ko|la)\s+([\p{L}][\p{L} .'-]{1,40})""")
    private val QUERY_CONTEXT = Regex("""(?i)\b(?:search|khojo|dhundo)\s+(.+)$""")

    const val MAX_STEPS = 5
}
