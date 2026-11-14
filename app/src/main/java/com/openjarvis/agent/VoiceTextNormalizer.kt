package com.openjarvis.agent

import java.text.Normalizer
import java.util.Locale

/**
 * Deterministic, offline speech-text normalization. Only known Devanagari
 * transliterations are rewritten; unknown names and words remain untouched.
 * The original utterance must be retained by callers for display and logs.
 */
object VoiceTextNormalizer {
    private val aliases = mapOf(
        "ओपन" to "open", "ओपेन" to "open",
        "क्लोज" to "close",
        "एंड" to "and", "एन्ड" to "and",
        "प्ले" to "play", "प्लै" to "play",
        "पॉज" to "pause", "स्टॉप" to "stop",
        "सोंग" to "song", "सॉन्ग" to "song",
        "सर्च" to "search", "बैक" to "back",
        "स्क्रीन" to "screen", "होम" to "home",
        "मैप" to "map", "मैप्स" to "maps",
        "कॉल" to "call", "मैसेज" to "message", "मेसेज" to "message",
        "टेक्स्ट" to "text", "टाइमर" to "timer", "अलार्म" to "alarm",
        "वॉल्यूम" to "volume", "टॉर्च" to "torch", "फ्लैशलाइट" to "flashlight",
        "फॉलोअर्स" to "followers", "मीनिंग" to "meaning",
        "यूट्यूब" to "youtube", "यूटयूब" to "youtube", "युट्यूब" to "youtube",
        "व्हाट्सएप" to "whatsapp", "वॉट्सएप" to "whatsapp",
        "व्हॉट्सएप" to "whatsapp", "व्हॉट्सऍप" to "whatsapp", "व्हॉट्सॲप" to "whatsapp",
        "इंस्टा" to "instagram", "इंस्टाग्राम" to "instagram",
        "टेलीग्राम" to "telegram", "क्रोम" to "chrome", "कैमरा" to "camera",
        "सेटिंग" to "settings", "सेटिंग्स" to "settings",
        "गैलरी" to "gallery", "स्पॉटिफाई" to "spotify", "जीमेल" to "gmail",
        "जार्विस" to "jarvis", "जारविस" to "jarvis", "जर्विस" to "jarvis",
        "जरवेस" to "jarvis", "एचआरवी" to "jarvis", "हे" to "hey"
    )
    private val wakeAliases = setOf(
        "jarvis", "hey jarvis", "hi jarvis", "हे जरवेस", "जरवेस",
        "जार्विस", "जारविस", "जर्विस", "हे जार्विस", "एचआरवी"
    )
    private val spaces = Regex("\\s+")
    private val punctuation = Regex("[\\p{Punct}।॥“”‘’]+")

    fun normalize(input: String): String {
        val source = Normalizer.normalize(input, Normalizer.Form.NFC)
            .replace(punctuation, " ")
            .replace(spaces, " ")
            .trim()
        if (source.isEmpty()) return ""
        return source.split(" ").joinToString(" ") { token ->
            aliases[token.lowercase(Locale.ROOT)] ?: token
        }.replace(spaces, " ").trim()
    }

    fun stripWakePhrase(input: String): String {
        var value = Normalizer.normalize(input, Normalizer.Form.NFC).trim()
        if (value.isEmpty()) return value
        val patterns = listOf(
            Regex("(?i)^(?:hey|hi)\\s+jarvis(?:[,.:;!?।-]?\\s*|$)"),
            Regex("(?i)^jarvis(?:[,.:;!?।-]?\\s*|$)"),
            Regex("^(?:हे\\s*)?(?:जरवेस|जार्विस|जारविस|जर्विस|एचआरवी)(?:[,.:;!?।-]?\\s*|$)")
        )
        var changed: Boolean
        do {
            changed = false
            for (pattern in patterns) {
                val next = value.replaceFirst(pattern, "").trim()
                if (next != value) {
                    value = next
                    changed = true
                    break
                }
            }
        } while (changed)
        return value
    }

    fun isBareWakePhrase(input: String): Boolean =
        normalize(stripWakePhrase(input)).isBlank() &&
            input.trim().isNotBlank() &&
            (normalize(input).split(" ").all { it in setOf("jarvis", "hey") })

    /** Match installed labels and spoken aliases without changing the installed label. */
    fun canonicalAppLabel(label: String): String {
        val normalized = normalize(label).lowercase(Locale.ROOT)
        return when (normalized) {
            "yt", "you tube" -> "youtube"
            "wa", "whats app" -> "whatsapp"
            "insta", "ig" -> "instagram"
            "google maps" -> "maps"
            "google chrome" -> "chrome"
            "gmail", "google mail" -> "gmail"
            else -> normalized
        }
    }

    fun appLabelsMatch(installedLabel: String, spokenLabel: String): Boolean {
        val installed = canonicalAppLabel(installedLabel)
        val spoken = canonicalAppLabel(spokenLabel)
        return installed == spoken || installed.startsWith(spoken) || spoken.startsWith(installed)
    }
}
