package com.openjarvis.voice

import com.openjarvis.agent.CommandRouter
import com.openjarvis.agent.ConversationIntentPolicy
import com.openjarvis.agent.PlayCommandPolicy
import com.openjarvis.agent.UnknownCommandPolicy

/**
 * Selects a usable transcript from Android's ordered recognition hypotheses.
 * A parser-recognized command/chat/clarification outranks a higher-confidence
 * transcript that Jarvis cannot route. Confidence is used within each pool.
 */
internal object RecognitionCandidateSelector {
    const val MIN_CONFIDENCE = 0.15f
    const val MAX_CANDIDATES = 5

    fun select(matches: List<String>?, confidenceScores: FloatArray? = null): String? {
        if (matches.isNullOrEmpty()) return null
        val candidates = matches.take(MAX_CANDIDATES).mapIndexedNotNull { index, candidate ->
            candidate.trim().takeIf { it.isNotEmpty() }?.let { Candidate(index, it) }
        }
        if (candidates.isEmpty()) return null
        val parsable = candidates.filter { isParserParsable(it.text) }
        val pool = if (parsable.isNotEmpty()) parsable else candidates

        var best: Candidate? = null
        var bestScore = 0f
        pool.forEach { candidate ->
            val score = confidenceScores
                ?.getOrNull(candidate.index)
                ?.takeIf { it.isFinite() && it > 0f }
            if (best == null || (score != null && score > bestScore)) {
                best = candidate
                bestScore = score ?: 0f
            }
        }
        if (bestScore > 0f && bestScore < MIN_CONFIDENCE) return null
        return best?.text
    }

    private data class Candidate(val index: Int, val text: String)

    private fun isParserParsable(text: String): Boolean =
        CommandRouter.classify(text) != CommandRouter.Route.NONE ||
            ConversationIntentPolicy.isChatRequest(text) ||
            UnknownCommandPolicy.shouldAskClarification(text) ||
            PlayCommandPolicy.shouldAskForQuery(text, youtubeForeground = false)
}
