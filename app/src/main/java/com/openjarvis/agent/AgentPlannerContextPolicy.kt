package com.openjarvis.agent

/**
 * Builds a compact, evidence-grounded prompt context for the next-action planner.
 * The goal is never truncated; only supporting observations and older context are bounded.
 */
internal object AgentPlannerContextPolicy {
    const val DEFAULT_MAX_CHARS = 6000

    fun build(
        goal: String,
        verifiedSteps: List<Action>,
        screen: ScreenObservation,
        maxChars: Int = DEFAULT_MAX_CHARS
    ): String {
        val cleanGoal = goal.trim()
        if (cleanGoal.isEmpty()) return ""

        val output = StringBuilder("Goal: ").append(cleanGoal)
        val limit = maxOf(maxChars.coerceAtLeast(0), output.length)
        fun appendSection(label: String, value: String) {
            if (output.length >= limit || value.isBlank()) return
            val prefix = "\n$label\n"
            if (output.length + prefix.length >= limit) return
            output.append(prefix)
            val remaining = limit - output.length
            output.append(value.take(remaining))
        }

        val recentSteps = verifiedSteps.takeLast(3).mapIndexed { index, action ->
            val details = listOfNotNull(action.label, action.text, action.value, action.direction)
                .firstOrNull { it.isNotBlank() }
            "${index + 1}. ${action.action}${details?.let { ": $it" }.orEmpty()}"
        }.joinToString("\n")
        appendSection("Last verified steps:", recentSteps)

        appendSection("Current package:", screen.packageName.orEmpty())
        appendSection("Visible screen text:", screen.visibleText)
        appendSection("Grounded interactive controls:", screen.interactiveElements)
        return output.toString()
    }
}
