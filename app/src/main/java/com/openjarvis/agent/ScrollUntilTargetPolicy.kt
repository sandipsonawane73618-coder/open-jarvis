package com.openjarvis.agent

/**
 * Pure decision policy for bounded "scroll until target" behavior.
 * It only decides whether a target is already visible or another scroll is
 * permitted; the Android executor remains responsible for performing scrolls
 * and obtaining a fresh ScreenObservation after each one.
 */
internal object ScrollUntilTargetPolicy {
    enum class Decision { FOUND, SCROLL, EXHAUSTED, INVALID_TARGET }

    fun decide(
        target: String,
        screen: ScreenObservation,
        scrollCount: Int,
        maxScrolls: Int = DEFAULT_MAX_SCROLLS
    ): Decision {
        val normalizedTarget = normalize(target)
        if (normalizedTarget.isBlank()) return Decision.INVALID_TARGET

        val visibleEvidence = normalize(
            listOf(screen.visibleText, screen.interactiveElements).joinToString(" ")
        )
        if (containsTarget(visibleEvidence, normalizedTarget)) return Decision.FOUND

        val safeMaximum = maxScrolls.coerceAtLeast(0)
        return if (scrollCount.coerceAtLeast(0) < safeMaximum) {
            Decision.SCROLL
        } else {
            Decision.EXHAUSTED
        }
    }

    private fun containsTarget(evidence: String, target: String): Boolean {
        if (evidence.isBlank()) return false
        return Regex(
            "(^|\\s)" + Regex.escape(target) + "($|\\s)"
        ).containsMatchIn(evidence)
    }

    private fun normalize(value: String): String =
        value.lowercase()
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    const val DEFAULT_MAX_SCROLLS = 8
}
