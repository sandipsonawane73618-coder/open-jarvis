package com.openjarvis.voice

/** Deterministic locale selection shared by speech recognition and speech output. */
internal object SpeechLanguage {
    val recognitionLocales: List<String> = listOf("hi-IN", "en-IN", "mr-IN")

    fun preferredRecognitionLocale(deviceLanguage: String): String = when (deviceLanguage.lowercase()) {
        "mr" -> "mr-IN"
        "en" -> "en-IN"
        else -> "hi-IN"
    }

    fun languageForText(text: String): String {
        val hasDevanagari = text.any { it in '\u0900'..'\u097F' }
        if (!hasDevanagari) {
            return when (MaharashtraSpeechProfile.detectDialect(text)) {
                MaharashtraSpeechProfile.Dialect.HINGLISH -> "hi"
                else -> "en"
            }
        }
        return when (MaharashtraSpeechProfile.detectDialect(text)) {
            MaharashtraSpeechProfile.Dialect.STANDARD_MARATHI,
            MaharashtraSpeechProfile.Dialect.VARHADI,
            MaharashtraSpeechProfile.Dialect.MALVANI,
            MaharashtraSpeechProfile.Dialect.AHIRANI_KHANDESHI -> "mr"
            else -> "hi"
        }
    }
}
