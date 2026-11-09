package com.openjarvis.voice

object WakeWordConfig {
    const val DISPLAY_PHRASE = "Jarvis / Hey Jarvis / Hi Jarvis"
    const val SENSITIVITY_PREFS = "wake_word_settings"
    const val SENSITIVITY_KEY = "wake_word_sensitivity"

    enum class Sensitivity(val title: String, val threshold: Float) {
        LOW("Low", 0.50f),
        MEDIUM("Medium", 0.35f),
        HIGH("High", 0.25f);

        companion object {
            fun fromPreference(value: String?): Sensitivity =
                values().firstOrNull { it.name.equals(value, ignoreCase = true) } ?: HIGH
        }
    }

    // Overlay layout dimensions in dp; leave room for fixed action buttons on ~360dp screens.
    const val OVERLAY_WIDTH_DP = 328
    const val ACTION_BUTTON_WIDTH_DP = 56
}
