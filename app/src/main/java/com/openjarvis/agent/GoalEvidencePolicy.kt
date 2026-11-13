package com.openjarvis.agent

/**
 * Fail-closed goal evidence check. A planner cannot complete a task from a screen
 * change alone: every meaningful target term must be visible in the fresh snapshot.
 */
internal object GoalEvidencePolicy {
    private val nonTargetWords = setOf(
        "open", "launch", "start", "show", "go", "to", "the", "please",
        "khol", "kholo", "kholna", "dikhao", "dikhaye", "dikha", "jao", "jाओ",
        "app", "application", "screen", "page", "and", "in",
        "on", "my", "for", "finish", "complete", "task", "it", "this", "that"
    )

    fun verify(goal: String, screen: ScreenObservation): GoalVerification {
        val evidence = normalize(listOf(screen.visibleText, screen.interactiveElements).joinToString(" "))
        if (screen.observedAtMs <= 0L || evidence.isBlank()) {
            return GoalVerification(false, "Fresh screen has no visible evidence for the requested goal.")
        }

        val normalizedGoal = normalize(goal)
        val targetTerms = normalizedGoal.split(Regex("\\s+"))
            .filter { it.length > 2 && it !in nonTargetWords }
            .distinct()
        if (targetTerms.isEmpty()) {
            return GoalVerification(false, "Goal has no distinctive target that can be verified on screen.")
        }

        val missing = targetTerms.filterNot { term ->
            Regex("(^|[^a-z0-9])" + Regex.escape(term) + "([^a-z0-9]|$)")
                .containsMatchIn(evidence)
        }
        return if (missing.isEmpty()) {
            GoalVerification(true, "Fresh screen contains visible evidence for: ${targetTerms.joinToString(" ")}")
        } else {
            GoalVerification(false, "Goal evidence is missing from the fresh screen: ${missing.joinToString(", ")}")
        }
    }

    private fun normalize(value: String): String =
        value.lowercase()
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
}
