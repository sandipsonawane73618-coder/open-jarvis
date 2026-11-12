package com.openjarvis.voice

import java.text.Normalizer
import java.util.Locale

/**
 * Maharashtra-first vocabulary hints for on-device speech recognition.
 * Hints bias supported recognizers; they do not replace the installed model's language packs.
 */
internal object MaharashtraSpeechProfile {
    enum class Dialect {
        STANDARD_MARATHI, VARHADI, MALVANI, AHIRANI_KHANDESHI, HINDI, HINGLISH, ENGLISH, UNKNOWN
    }

    private val varhadiMarkers = setOf("मले", "तुले", "कायले", "जायले", "नाई", "कशाले")
    private val malvaniMarkers = setOf("माका", "तुका", "आसा", "किदें", "न्हय", "खंय", "कितें")
    private val ahiraniMarkers = setOf("माले", "कायले", "हाय", "नाय", "कठे", "कसले")

    val recognitionHints: List<String> = listOf(
        "मला मदत कर", "माझं ऐक", "फोन उघड", "अॅप उघड", "उघडा", "उघड ना",
        "बंद कर", "बंद करा", "पुन्हा सुरू कर", "मागे जा", "वर स्क्रोल कर",
        "खाली स्क्रोल कर", "इथे टॅप कर", "हे टाइप कर", "मेसेज पाठव",
        "व्हॉट्सॲप उघड", "यूट्यूबवर गाणं लाव", "इन्स्टाग्रामवर शोध",
        "फोन बंद करा", "होय", "नको", "रद्द कर",
        "मले सांग", "तुले काय पाहिजे", "कायले उघडत नाही", "जायले दे",
        "नाई होत", "कशाले थांबलास", "मले व्हॉट्सॲप उघडून दे",
        "माका सांग", "तुका काय जाय", "खंय आसा", "किदें करता",
        "माका फोन लाव", "व्हॉट्सॲप उघड", "गाणं लाव",
        "माले सांग", "तुले काय पाहिजे", "कठे जायचं", "कायले होत नाय",
        "माले फोन लाव", "अॅप उघडून दे", "मेसेज धाड",
        "फोन खोलो", "जरा ऐप खोलो", "मेरे को मैसेज भेजो", "song play karo",
        "screen par tap karo", "scroll down", "open WhatsApp and send message"
    ).distinct()

    fun detectDialect(text: String): Dialect {
        val normalized = normalize(text)
        if (normalized.isBlank()) return Dialect.UNKNOWN
        val tokens = normalized.split(' ').toSet()
        fun score(markers: Set<String>) = markers.count(tokens::contains)
        val scores = listOf(
            Dialect.VARHADI to score(varhadiMarkers),
            Dialect.MALVANI to score(malvaniMarkers),
            Dialect.AHIRANI_KHANDESHI to score(ahiraniMarkers)
        )
        val best = scores.maxByOrNull { it.second }
        if (best != null && best.second >= 2) return best.first
        if (text.any { it in '\u0900'..'\u097F' }) {
            val marathiMarkers = setOf("आहे", "आणि", "मला", "माझे", "तुम्ही", "सांगा", "नको", "झालं")
            return if (marathiMarkers.any(tokens::contains)) Dialect.STANDARD_MARATHI else Dialect.HINDI
        }
        return when {
            tokens.any { it in setOf("karo", "kholo", "mala", "majha", "tula", "sang", "aahe", "nako") } -> Dialect.HINGLISH
            tokens.any { it in setOf("please", "open", "close", "play", "search", "send", "scroll") } -> Dialect.ENGLISH
            else -> Dialect.UNKNOWN
        }
    }

    private fun normalize(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFC).lowercase(Locale.ROOT)
            .replace(Regex("[^\\p{L}\\p{N}\\p{M}]+"), " ")
            .trim().replace(Regex("\\s+"), " ")
}
