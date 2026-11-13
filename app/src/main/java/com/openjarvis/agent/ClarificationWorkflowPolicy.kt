package com.openjarvis.agent

import org.json.JSONException
import org.json.JSONObject

/**
 * Parses the LLM's clarification-router envelope. This policy never executes
 * actions; it can only ask a question, return a merged command to AgentCore,
 * or stop after the bounded clarification budget.
 */
internal object ClarificationWorkflowPolicy {
    const val OFFLINE_FALLBACK = "Aap kya karna chahte ho? Thoda detail mein batao."
    const val MAX_ROUNDS = 2

    data class Pending(
        val originalCommand: String,
        val question: String,
        val round: Int
    )

    sealed interface Decision {
        data class Ask(val question: String, val pending: Pending) : Decision
        data class Continue(val command: String) : Decision
        data object Exhausted : Decision
    }

    fun resolve(
        routerResponse: String?,
        currentCommand: String,
        pending: Pending? = null
    ): Decision {
        val original = (pending?.originalCommand ?: currentCommand).cleanCommand()
        val current = currentCommand.cleanCommand()
        val nextRound = (pending?.round ?: 0) + 1
        val root = routerResponse
            ?.trim()
            ?.takeIf { it.isNotBlank() && it.length <= 2_000 }
            ?.let { raw -> runCatching { JSONObject(raw) }.getOrNull() }

        if (root == null) return askFallback(original, pending, nextRound)

        return when (root.optString("route").trim().lowercase()) {
            "ask_clarify" -> {
                if (pending != null && pending.round >= MAX_ROUNDS) return Decision.Exhausted
                val question = root.optString("question").cleanQuestion()
                if (question.isBlank()) askFallback(original, pending, nextRound)
                else Decision.Ask(question, Pending(original, question, nextRound.coerceAtMost(MAX_ROUNDS)))
            }
            "action" -> {
                val routedCommand = root.optString("command").cleanCommand()
                if (pending == null || routedCommand.isBlank()) {
                    askFallback(original, pending, nextRound)
                } else {
                    Decision.Continue(listOf(original, routedCommand).distinct().joinToString(" "))
                }
            }
            else -> askFallback(original, pending, nextRound)
        }
    }

    private fun askFallback(
        original: String,
        pending: Pending?,
        nextRound: Int
    ): Decision {
        if (pending != null && pending.round >= MAX_ROUNDS) return Decision.Exhausted
        return Decision.Ask(
            OFFLINE_FALLBACK,
            Pending(original, OFFLINE_FALLBACK, nextRound.coerceAtMost(MAX_ROUNDS))
        )
    }

    private fun String.cleanCommand(): String =
        replace(Regex("[\\u0000-\\u001F]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(500)

    private fun String.cleanQuestion(): String =
        replace(Regex("[\\u0000-\\u001F]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(180)
}
