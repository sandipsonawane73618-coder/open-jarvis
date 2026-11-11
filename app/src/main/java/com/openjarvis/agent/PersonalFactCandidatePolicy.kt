package com.openjarvis.agent

/** Detects only clear first-person profile statements; never persists anything itself. */
internal object PersonalFactCandidatePolicy {
    enum class Consent { YES, NO, OTHER }

    fun consent(input: String): Consent = when (input.trim().lowercase()) {
        "yes", "yeah", "yep", "haan", "han", "ha", "हो", "होय", "हो हो", "होय नक्की" -> Consent.YES
        "no", "nope", "nahi", "nahin", "नहीं", "नाही", "mat", "नको" -> Consent.NO
        else -> Consent.OTHER
    }

    fun extract(input: String): String? {
        val text = input.trim().replace(Regex("\\s+"), " ")
        if (text.length !in 8..180) return null
        val actionWords = Regex("\\b(open|launch|start|send|message|dial|search|play|set|turn|click|tap|scroll|navigate|kholo|khol|bhejo|chalu)\\b", RegexOption.IGNORE_CASE)
        if (actionWords.containsMatchIn(text) && !text.startsWith("call me ", ignoreCase = true)) return null
        val patterns = listOf(
            Regex("^(?:my name is|call me) .+", RegexOption.IGNORE_CASE),
            Regex("^mera naam .+ hai$", RegexOption.IGNORE_CASE),
            Regex("^i (?:like|love|prefer) .+", RegexOption.IGNORE_CASE),
            Regex("^mujhe .+ (?:pasand hai|achha lagta hai)$", RegexOption.IGNORE_CASE),
            Regex("^मुझे .+ पसंद है$"),
            Regex("^मला .+ आवडते$"),
            Regex("^i work as .+", RegexOption.IGNORE_CASE),
            Regex("^my (?:job|work) is .+", RegexOption.IGNORE_CASE),
            Regex("^i am (?:an? )?(?:engineer|technician|developer|teacher|doctor|student|mechanic|electrician)\\b.*$", RegexOption.IGNORE_CASE),
            Regex("^main .+ kaam karta hoon$", RegexOption.IGNORE_CASE),
            Regex("^मेरा काम .+$"),
            Regex("^माझे काम .+$")
        )
        return text.takeIf { candidate -> patterns.any { it.matches(candidate) } }
    }
}
