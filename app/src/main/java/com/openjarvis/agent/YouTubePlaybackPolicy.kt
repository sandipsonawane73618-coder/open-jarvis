package com.openjarvis.agent

/** Screen-grounded YouTube selection and player verification rules. */
internal object YouTubePlaybackPolicy {
    fun shouldPlayFirstResult(command: String): Boolean {
        val normalized = command.lowercase(java.util.Locale.ROOT)
        val hasPlay = normalized.contains("play") || normalized.contains("chalao") ||
            normalized.contains("bajao") || normalized.contains("चलाओ") || normalized.contains("बजाओ")
        return normalized.contains("youtube") && hasPlay
    }

    fun isNoResultsScreen(visibleText: String): Boolean {
        val text = visibleText.lowercase(java.util.Locale.ROOT)
        return listOf("no results found", "no results", "no videos found", "no matching videos",
            "कोई परिणाम नहीं", "कोई वीडियो नहीं मिला").any { text.contains(it) }
    }

    fun firstVideoTarget(observation: ScreenObservation): String? {
        if (observation.packageName != "com.google.android.youtube") return null
        return observation.interactiveElements.lineSequence()
            .filter { it.contains("clickable", ignoreCase = true) }
            .mapNotNull { line ->
                Regex("""text=(.+?)(?:\s+(?:desc=|hint=|id=|class=|bounds=)|$)""")
                    .find(line)?.groupValues?.get(1)?.trim()
                    ?: Regex("""desc=(.+?)(?:\s+(?:hint=|id=|class=|bounds=)|$)""")
                        .find(line)?.groupValues?.get(1)?.trim()
            }
            .firstOrNull { target ->
                target.isNotBlank() &&
                    !Regex("(?i)^(home|shorts|subscriptions|library|search|create|notifications|profile|account|settings|back|menu|navigation)$")
                        .matches(target.trim()) &&
                    !target.contains("shorts", ignoreCase = true) &&
                    !target.contains("advertisement", ignoreCase = true) &&
                    !target.contains("sponsored", ignoreCase = true)
            }
    }

    fun isPlayerVerified(
        packageName: String?,
        visibleText: String,
        playerControlsVisible: Boolean
    ): Boolean {
        if (packageName != "com.google.android.youtube" || !playerControlsVisible) return false
        val text = visibleText.lowercase(java.util.Locale.ROOT)
        val hasPlaybackControl = listOf("pause", "play/pause", "video player", "seek bar")
            .any { text.contains(it) }
        val hasVideoContext = text.isNotBlank() &&
            !text.contains("search results") &&
            !text.contains("no results")
        return hasPlaybackControl && hasVideoContext
    }

    fun noResultsReply(): String = "YouTube par koi matching video nahi mila."
}
