package com.openjarvis.voice

/** Deterministic locale selection shared by speech recognition and speech output. */
internal object SpeechLanguage {
    val recognitionLocales: List<String> = listOf("hi-IN", "mr-IN", "en-IN")

    fun preferredRecognitionLocale(deviceLanguage: String): String = when (deviceLanguage.lowercase()) {
        "mr" -> "mr-IN"
        "en" -> "en-IN"
        else -> "hi-IN"
    }

    /** Try the preferred locale first, then the other configured Indian locales. */
    fun recognitionFallbacks(preferredLocale: String): List<String> =
        listOf(preferredLocale).plus(recognitionLocales.filterNot { it == preferredLocale })

    fun languageForText(text: String): String {
        val hasDevanagari = text.any { it in '\u0900'..'\u097F' }
        if (!hasDevanagari) return "en"
        val marathiMarkers = listOf(
            "आहे", "आणि", "मला", "काय", "झाले", "झालं", "नाही",
            "दाखवा", "उघडा", "करायचे", "माझे", "तुम्ही", "सांगा", "नको"
        )
        return if (marathiMarkers.any { text.contains(it) }) "mr" else "hi"
    }
}
