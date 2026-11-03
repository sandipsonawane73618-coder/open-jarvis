package com.openjarvis.voice

object WakeWordConfig {
    const val PREFERENCE_KEY = "picovoice_access_key"
    const val DISPLAY_PHRASE = "Hey Jarvis"
    const val ENGINE_KEYWORD = "JARVIS"

    fun normalizeAccessKey(raw: String?): String = raw?.trim().orEmpty()
}
