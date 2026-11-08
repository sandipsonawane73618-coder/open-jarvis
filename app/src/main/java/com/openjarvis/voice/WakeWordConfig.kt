package com.openjarvis.voice

object WakeWordConfig {
    const val DISPLAY_PHRASE = "Hey Jarvis / Hi Jarvis"
    const val SENSITIVITY_PREFS = "wake_word_settings"
    const val SENSITIVITY_KEY = "wake_word_sensitivity"
    const val ALLOW_BARE_JARVIS_KEY = "allow_bare_jarvis_experimental"

    enum class Sensitivity(val title: String, val threshold: Float) {
        LOW("Low", 0.50f),
        MEDIUM("Medium", 0.35f),
        HIGH("High", 0.25f);

        companion object {
            fun fromPreference(value: String?): Sensitivity =
                values().firstOrNull { it.name.equals(value, ignoreCase = true) } ?: HIGH
        }
    }

    /** Select only allowed wake phrases from the raw per-keyword ONNX scores. */
    fun selectCandidate(scores: Map<String, Float>, allowBareJarvis: Boolean): Pair<String, Float>? {
        val allowed = if (allowBareJarvis) listOf("Hey Jarvis", "Hi Jarvis", "Jarvis")
            else listOf("Hey Jarvis", "Hi Jarvis")
        return allowed.mapNotNull { phrase ->
            scores.entries.firstOrNull { it.key.trim().equals(phrase, ignoreCase = true) }
                ?.let { phrase to it.value }
        }.maxByOrNull { it.second }
    }

    // Overlay layout dimensions in dp; leave room for fixed action buttons on ~360dp screens.
    const val OVERLAY_WIDTH_DP = 328
    const val ACTION_BUTTON_WIDTH_DP = 56
}
