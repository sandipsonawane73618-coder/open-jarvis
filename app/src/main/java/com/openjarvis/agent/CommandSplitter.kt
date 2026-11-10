package com.openjarvis.agent

import java.util.Locale

/**
 * Splits one long utterance into ordered steps.
 * Strong sequencers always split. Weak connectors split only when both sides
 * parse as commands (or the right side is a message continuation).
 * After a message-text delimiter nothing is split: the rest is message text.
 * The pipeline is deliberately bounded to five steps.
 */
object CommandSplitter {
    private val PUNCT = Regex("^[\\p{Punct}।॥]+|[\\p{Punct}।॥]+$")
    private val STRONG: List<List<String>> = listOf(
        listOf("uske", "baad"), listOf("उसके", "बाद"), listOf("and", "then"),
        listOf("phir"), listOf("fir"), listOf("then"), listOf("फिर"), listOf("नंतर")
    )
    private val WEAK = setOf("aur", "and", "और", "आणि")
    private val TEXT_DELIMS = setOf("ki", "कि", "that", "saying", "say")
    private val MESSAGE_WORDS = setOf(
        "message", "msg", "sms", "whatsapp", "मैसेज", "मेसेज", "संदेश", "एसएमएस", "व्हाट्सएप"
    )
    private const val MAX_STEPS = 5

    private fun n(w: String): String = w.replace(PUNCT, "").lowercase(Locale.ROOT)

    private fun canParseCommandSequence(text: String, canParse: (String) -> Boolean): Boolean {
        if (canParse(text)) return true
        val parts = text.trim().split(Regex("\\s+"))
        for (i in parts.indices) {
            val word = n(parts[i])
            if (word in WEAK && i > 0 && i < parts.lastIndex) {
                val left = parts.subList(0, i).joinToString(" ")
                val right = parts.subList(i + 1, parts.size).joinToString(" ")
                if (canParse(left) && canParseCommandSequence(right, canParse)) return true
            }
        }
        return false
    }

    fun split(text: String, canParse: (String) -> Boolean = { true }): List<String> {
        val words = text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        val steps = ArrayList<String>()
        val cur = ArrayList<String>()
        var inText = false

        fun flush() {
            val s = cur.joinToString(" ").trim().trim(',', ';')
            if (s.isNotEmpty() && steps.size < MAX_STEPS) steps.add(s)
            cur.clear()
            inText = false
        }

        var i = 0
        while (i < words.size) {
            val w = n(words[i])
            if (!inText && steps.size < MAX_STEPS - 1) {
                val strong = STRONG.firstOrNull { seq ->
                    seq.indices.all { k -> i + k < words.size && n(words[i + k]) == seq[k] }
                }
                if (strong != null) {
                    flush()
                    i += strong.size
                    continue
                }
                if (w in WEAK && cur.isNotEmpty()) {
                    val rest = words.subList(i + 1, words.size).joinToString(" ")
                    if (canParse(cur.joinToString(" ")) && canParse(rest)) {
                        flush()
                        i++
                        continue
                    }
                }
            }
            if (!inText && w in TEXT_DELIMS && cur.any { n(it) in MESSAGE_WORDS }) inText = true
            cur.add(words[i])
            val commaBoundary = words[i].matches(Regex(".*[,，;；]$"))
            if (!inText && commaBoundary && cur.isNotEmpty() && steps.size < MAX_STEPS - 1) {
                val left = cur.joinToString(" ").trim().trim(',', ';')
                val leftTokens = left.split(Regex("\\s+")).map(::n)
                val isUnfinishedMessage = leftTokens.any { it in MESSAGE_WORDS } &&
                    leftTokens.any { it in setOf("bhejo", "bhej", "send", "भेजो", "भेज") } &&
                    leftTokens.none { it in TEXT_DELIMS }
                val rest = words.subList(i + 1, words.size).joinToString(" ")
                if (!isUnfinishedMessage && canParse(left) && canParseCommandSequence(rest, canParse)) {
                    flush()
                    i++
                    continue
                }
            }
            i++
        }
        flush()
        return steps
    }
}
