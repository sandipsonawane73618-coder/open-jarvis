package com.openjarvis.voice

/**
 * Selects a usable transcript from Android's ordered recognition hypotheses.
 * Confidence scores are preferred when supplied; ties and missing scores retain
 * recognizer order. Blank candidates are never returned.
 */
internal object RecognitionCandidateSelector {
    fun select(matches: List<String>?, confidenceScores: FloatArray? = null): String? {
        if (matches.isNullOrEmpty()) return null

        var bestText: String? = null
        var bestScore = Float.NEGATIVE_INFINITY
        matches.forEachIndexed { index, candidate ->
            val text = candidate.trim()
            if (text.isNotEmpty()) {
                val score = confidenceScores
                    ?.getOrNull(index)
                    ?.takeIf { it.isFinite() }
                    ?: Float.NEGATIVE_INFINITY
                if (bestText == null || score > bestScore) {
                    bestText = text
                    bestScore = score
                }
            }
        }
        return bestText
    }
}
