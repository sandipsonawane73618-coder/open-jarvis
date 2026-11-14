package com.openjarvis.agent

import java.util.Locale

/** Routes empty input, vague utterances, and commands before the skill/action planner. */
internal object UnknownCommandPolicy {
    const val FALLBACK = "Samajh nahi aaya, dobara bolo."
    const val CLARIFY_PROMPT = "Aap kya karna chahte ho? Thoda detail mein batao."

    private val fillerWords = setOf(
        "ok", "okay", "haan", "han", "hmm", "hm", "acha", "achha", "theek", "thik",
        "yes", "huh", "hmmm"
    )
    private val vaguePhrases = setOf(
        "kuch bhi", "kuch bhi karo", "kuch bhi kar", "blah blah", "blah",
        "jo bhi", "whatever", "anything", "kuch toh", "kuch to"
    )

    private fun normalize(command: String): String =
        command.lowercase(Locale.ROOT)
            .replace(Regex("[^\\p{L}\\p{N}\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    /** Stop only empty, one-character, or pure-filler input. */
    fun shouldStopBeforePlanner(command: String): Boolean {
        val text = normalize(command)
        if (text.isBlank() || text.length <= 1) return true
        val words = text.split(" ")
        return words.all { it in fillerWords }
    }

    fun shouldAskClarification(command: String): Boolean {
        val text = normalize(command)
        return text in vaguePhrases
    }

    /**
     * Vague utterances are diverted to clarification before any action planner.
     * The action-planning callback is invoked only for non-empty, non-filler,
     * non-vague commands.
     */
    fun dispatch(
        command: String,
        onStop: () -> Unit,
        onAskClarify: () -> Unit,
        onActionPlan: () -> Unit
    ) {
        when {
            shouldStopBeforePlanner(command) -> onStop()
            shouldAskClarification(command) -> onAskClarify()
            else -> onActionPlan()
        }
    }
}
