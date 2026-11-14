package com.openjarvis.voice

/** Deterministic locale selection shared by speech recognition and speech output. */
internal object SpeechLanguage {
    val recognitionLocales: List<String> = listOf("hi-IN", "en-IN", "mr-IN")

    fun preferredRecognitionLocale(deviceLanguage: String): String = "hi-IN"

    fun languageForText(text: String): String {
        val hasDevanagari = text.any { it in '\u0900'..'\u097F' }
        if (!hasDevanagari) {
            val romanHindiMarkers = setOf("per", "par", "ko", "bhejo", "bhej", "karo", "kar", "kya", "hai", "hain", "mujhe", "aap", "ka", "ki", "ke", "mein", "me", "batao", "bolo", "kholo", "jao", "chahiye")
            val tokens = text.lowercase().split(Regex("[^a-z0-9]+")).filter { it.isNotBlank() }.toSet()
            return if (MaharashtraSpeechProfile.detectDialect(text) == MaharashtraSpeechProfile.Dialect.HINGLISH || tokens.any { it in romanHindiMarkers }) "hi" else "en"
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
