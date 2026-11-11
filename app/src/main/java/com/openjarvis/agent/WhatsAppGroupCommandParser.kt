package com.openjarvis.agent

import java.text.Normalizer
import java.util.Locale

/**
 * Extracts explicit WhatsApp group and follow-up typing references.
 * This parser never sends a message; it only supplies context to the visible-UI planner.
 */
internal object WhatsAppGroupCommandParser {
    private val groupTokens = setOf("group", "ग्रुप", "समूह")
    private val openActions = setOf("kholo", "khol", "open", "खोलो", "खोल", "उघडा", "उघड")
    private val typeSuffix = Regex(
        "^(.+?)\\s+(?:likho|likh do|type karo|type|write|लिखो|लिख दो|लिहा|लिहून टाका)$",
        RegexOption.IGNORE_CASE
    )
    private val trailingPunctuation = Regex("[\\p{Punct}।॥]+$")
    private val whitespace = Regex("\\s+")

    fun parseGroupName(command: String): String? {
        val cleaned = Normalizer.normalize(command, Normalizer.Form.NFKC)
            .trim()
            .replace(trailingPunctuation, "")
            .trim()
        val tokens = cleaned.split(whitespace).filter { it.isNotBlank() }
        if (tokens.size < 3) return null

        val action = tokens.last().lowercase(Locale.ROOT)
        val groupToken = tokens[tokens.lastIndex - 1].lowercase(Locale.ROOT)
        if (action !in openActions || groupToken !in groupTokens) return null

        val nameTokens = tokens.dropLast(2).toMutableList()
        // Accept both "Family group open" and "Family WhatsApp group open".
        if (nameTokens.lastOrNull()?.equals("whatsapp", ignoreCase = true) == true) {
            nameTokens.removeAt(nameTokens.lastIndex)
        }
        val name = nameTokens.joinToString(" ").trim()
        return name.takeIf { it.length in 2..80 }
    }

    fun parseFollowUpText(command: String, app: String?, groupName: String?): String? {
        if (app?.contains("whatsapp", ignoreCase = true) != true || groupName.isNullOrBlank()) return null
        val cleaned = Normalizer.normalize(command, Normalizer.Form.NFKC).trim()
        val match = typeSuffix.matchEntire(cleaned) ?: return null
        return match.groupValues[1].trim().takeIf { it.isNotBlank() && it.length <= 2000 }
    }
}
