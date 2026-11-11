package com.openjarvis.voice

/**
 * Selects a usable transcript from Android's ordered recognition hypotheses.
 * Positive confidence scores are preferred when supplied. Zero and negative
 * scores are treated as unavailable; transcript meaning is validated downstream.
 * Blank candidates are never returned.
 */
internal object RecognitionCandidateSelector {
    const val MIN_CONFIDENCE = 0.15f

    fun select(matches: List<String>?, confidenceScores: FloatArray? = null): String? {
        if (matches.isNullOrEmpty()) return null

        var bestText: String? = null
        var bestPositiveScore = 0f
        matches.forEachIndexed { index, candidate ->
            val text = candidate.trim()
            if (text.isNotEmpty()) {
                val score = confidenceScores
                    ?.getOrNull(index)
                    ?.takeIf { it.isFinite() && it > 0f }
                if (bestText == null || (score != null && score > bestPositiveScore)) {
                    bestText = text
                    bestPositiveScore = score ?: 0f
                }
            }
        }
        if (bestPositiveScore > 0f && bestPositiveScore < MIN_CONFIDENCE) return null
        return bestText
    }
}
