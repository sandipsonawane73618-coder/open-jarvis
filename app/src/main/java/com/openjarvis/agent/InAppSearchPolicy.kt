package com.openjarvis.agent

/** Strict, deterministic verification for Instagram in-app search. */
internal object InAppSearchPolicy {
    private val resultMarkers = listOf("top", "accounts", "tags", "places", "results", "for you", "suggested")

    fun isVerified(packageName: String?, query: String, visibleText: String): Boolean {
        if (packageName != "com.instagram.android" || query.isBlank()) return false
        val normalized = visibleText.lowercase(java.util.Locale.ROOT)
            .replace(Regex("\\s+"), " ")
        val normalizedQuery = query.lowercase(java.util.Locale.ROOT)
            .replace(Regex("\\s+"), " ").trim()
        return normalized.contains(normalizedQuery) &&
            resultMarkers.any { marker -> normalized.contains(marker) }
    }
}