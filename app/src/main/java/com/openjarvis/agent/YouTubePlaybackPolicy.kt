package com.openjarvis.agent

/** Screen-grounded YouTube selection and player verification rules. */
internal object YouTubePlaybackPolicy {
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
        val hasPlayerSurface = listOf("comments", "subscribe", "up next", "views", "description")
            .any { text.contains(it) }
        return hasPlaybackControl && hasPlayerSurface
    }

    fun noResultsReply(): String = "YouTube par koi matching video nahi mila."
}
