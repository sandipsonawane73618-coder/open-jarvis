package com.openjarvis.agent

import java.text.Normalizer
import java.util.Locale

/**
 * Parses "open YouTube/Google and search X" commands (English, Hinglish,
 * Devanagari, Marathi). Pure Kotlin, JVM-testable. Returns the query or null.
 * Call parseYouTubeQuery first, then parseWebQuery, BEFORE splitting on
 * "aur/phir/ani" and BEFORE the LLM.
 */
object SearchCommandParser {

    private fun norm(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFC).lowercase(Locale.ROOT)

    private fun words(vararg w: String): Set<String> = w.map { norm(it) }.toSet()

    private val YOUTUBE_WORDS = words(
        "youtube", "youtub", "yt", "यूट्यूब", "यूटयूब", "युट्यूब", "यूट्युब"
    )

    private val WEB_WORDS = words(
        "google", "chrome", "browser", "गूगल", "क्रोम", "ब्राउज़र", "ब्राउजर"
    )

    private val OPEN_VERBS = words(
        "open", "kholo", "khol", "kholiye", "kholna", "launch", "start",
        "खोलो", "खोल", "खोलिए", "खोला", "उघडा"
    )

    private val CONNECTORS = words(
        "aur", "and", "phir", "fir", "then", "ani", "और", "फिर", "आणि",
        "par", "pe", "pr", "mein", "me", "on", "in", "at", "ko", "se",
        "पर", "पे", "में", "मे", "को", "से", "वर"
    )

    private val SEARCH_VERBS = words(
        "search", "serch", "srch", "find", "dhundo", "dhoondo", "dhundh", "dhoondh",
        "chalao", "chala", "play", "lagao", "laga", "sunao", "dikhao",
        "सर्च", "खोजो", "खोज", "ढूंढो", "ढूँढो", "चलाओ", "चला", "लगाओ",
        "सुनाओ", "दिखाओ", "शोधा", "शोध"
    )

    private val FILLERS = words(
        "karo", "kar", "kro", "kare", "karen", "kardo", "karna", "kijiye",
        "do", "dena", "de", "na", "please", "plz", "pls", "zara", "jara",
        "mujhe", "mera", "karke", "karake",
        "करो", "कर", "करा", "करना", "कीजिए", "दो", "दे", "ना", "करके",
        "प्लीज", "कृपया", "ज़रा", "जरा", "मुझे"
    )

    private val NOISE: Set<String> = OPEN_VERBS + CONNECTORS + SEARCH_VERBS + FILLERS

    private val PUNCTUATION = Regex("[\\p{Punct}।॥“”‘’]")
    private val SPACES = Regex("\\s+")

    fun parseYouTubeQuery(text: String): String? = extractQuery(text, YOUTUBE_WORDS)

    fun parseWebQuery(text: String): String? = extractQuery(text, WEB_WORDS)

    private fun tokenize(text: String): List<String> {
        val cleaned = norm(text)
            .replace("you tube", "youtube")
            .replace(norm("यू ट्यूब"), norm("यूट्यूब"))
            .replace(PUNCTUATION, " ")
            .replace(SPACES, " ")
            .trim()
        if (cleaned.isEmpty()) return emptyList()
        return cleaned.split(" ")
    }

    private fun extractQuery(text: String, platformWords: Set<String>): String? {
        val tokens = tokenize(text)
        if (tokens.none { it in platformWords }) return null
        if (tokens.none { it in SEARCH_VERBS }) return null

        val remaining = tokens.filterNot {
            it in platformWords || it in SEARCH_VERBS || it in OPEN_VERBS || it in FILLERS
        }.toMutableList()
        while (remaining.isNotEmpty() && remaining.first() in NOISE) {
            remaining.removeAt(0)
        }
        while (remaining.isNotEmpty() && remaining.last() in NOISE) {
            remaining.removeAt(remaining.size - 1)
        }
        val query = remaining.joinToString(" ").trim()
        return if (query.isEmpty()) null else query
    }
}
