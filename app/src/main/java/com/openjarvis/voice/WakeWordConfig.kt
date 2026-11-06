package com.openjarvis.voice

object WakeWordConfig {
    const val PREFERENCE_KEY = "picovoice_access_key"
    const val DISPLAY_PHRASE = "Jarvis"
    const val ENGINE_KEYWORD = "JARVIS"
    const val ACCESS_KEY_MIN_LENGTH = 30
    const val SENSITIVITY_PREFS = "wake_word_settings"
    const val SENSITIVITY_KEY = "wake_word_sensitivity"
    val KEYWORD_PHRASES = listOf("JARVIS", "HEY JARVIS", "JARVISS", "JARVEES", "JARVIZ")
    const val EXPECTED_KEYWORD_TOKEN_LINES = 5

    /** Validate the generated BPE asset, not the human-readable phrase list. */
    fun isValidTokenizedKeywordAsset(lines: List<String>): Boolean {
        val tokens = lines.map { it.trim() }.filter { it.isNotEmpty() }
        if (tokens.size < EXPECTED_KEYWORD_TOKEN_LINES) return false
        return tokens.none { tokenLine ->
            KEYWORD_PHRASES.any { phrase -> tokenLine.equals(phrase, ignoreCase = true) }
        }
    }

    enum class Sensitivity(val title: String, val threshold: Float, val keywordBoost: Float) {
        LOW("Low", 0.28f, 1.2f),
        MEDIUM("Medium", 0.10f, 2.5f),
        HIGH("High", 0.05f, 3.0f);

        companion object {
            fun fromPreference(value: String?): Sensitivity =
                values().firstOrNull { it.name.equals(value, ignoreCase = true) } ?: MEDIUM
        }
    }

    /**
     * Picovoice keys are opaque tokens. Remove accidental formatting pasted from
     * notes/quotes, but never print the token itself.
     */
    fun normalizeAccessKey(raw: String?): String = raw.orEmpty()
        .filterNot { it.isWhitespace() || it == '"' || it == '\'' }

    fun validateAccessKey(raw: String?): String? {
        val key = normalizeAccessKey(raw)
        return when {
            key.isEmpty() -> "Add your Picovoice AccessKey in Settings."
            key.length < ACCESS_KEY_MIN_LENGTH ->
                "Picovoice AccessKey lambi hoti hai (console.picovoice.ai se copy karo). Aapne chhota text daala hai."
            else -> null
        }
    }

    fun accessKeyDiagnostic(raw: String?): String {
        val key = normalizeAccessKey(raw)
        return "keyPrefix=${key.take(4)}, keyLength=${key.length}"
    }

    // Overlay layout dimensions in dp; leave room for fixed action buttons on ~360dp screens.
    const val OVERLAY_WIDTH_DP = 328
    const val ACTION_BUTTON_WIDTH_DP = 56
}
