package com.openjarvis.agent

import java.text.Normalizer
import java.util.Locale

/** name == null means "call" was said but no contact name was given. */
data class CallRequest(val name: String?)

/**
 * Parses call commands and strips filler words, so "call karo na" is NOT
 * treated as a contact named "karo na". Returns null if the text is not a
 * call command or contains a phone number (the existing number-dial path
 * handles that). Pure Kotlin, JVM-testable.
 */
object ContactCommandParser {

    private fun norm(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFC).lowercase(Locale.ROOT)

    private fun words(vararg w: String): Set<String> = w.map { norm(it) }.toSet()

    private val CALL_WORDS = words(
        "call", "dial", "phone", "ring", "कॉल", "कोल", "फोन", "फ़ोन"
    )

    private val FILLERS = words(
        "karo", "kar", "kro", "kare", "karen", "kardo", "karna", "karni", "kijiye",
        "do", "dena", "de", "na", "please", "plz", "pls", "zara", "jara", "jaldi",
        "abhi", "now", "mujhe", "mera", "meri", "hume", "lagao", "laga", "lagana",
        "lagado", "lao", "hai", "hain", "hu", "chahiye",
        "करो", "कर", "करा", "करना", "कीजिए", "दो", "दे", "ना", "प्लीज", "कृपया",
        "ज़रा", "जरा", "जल्दी", "अभी", "मुझे", "मेरा", "मेरी", "लगाओ", "लगा",
        "लावा", "है", "हैं", "चाहिए", "मला"
    )

    private val CONNECTORS = words(
        "ko", "se", "ka", "ki", "ke", "to", "को", "से", "का", "की", "के", "ला"
    )

    private val NOISE: Set<String> = CALL_WORDS + FILLERS + CONNECTORS

    private val PUNCTUATION = Regex("[\\p{Punct}।॥“”‘’]")
    private val SPACES = Regex("\\s+")

    fun parse(text: String): CallRequest? {
        val cleaned = norm(text).replace(PUNCTUATION, " ").replace(SPACES, " ").trim()
        if (cleaned.isEmpty()) return null
        val tokens = cleaned.split(" ")
        if (tokens.none { it in CALL_WORDS }) return null

        val remaining = tokens.toMutableList()
        while (remaining.isNotEmpty() && remaining.first() in NOISE) {
            remaining.removeAt(0)
        }
        while (remaining.isNotEmpty() && remaining.last() in NOISE) {
            remaining.removeAt(remaining.size - 1)
        }
        if (remaining.joinToString("").count { it.isDigit() } >= 3) return null

        val name = remaining.joinToString(" ").trim()
        return CallRequest(if (name.isEmpty()) null else name)
    }
}
