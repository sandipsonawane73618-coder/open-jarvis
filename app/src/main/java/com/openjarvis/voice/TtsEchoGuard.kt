package com.openjarvis.voice

import java.text.Normalizer

/** Suppresses only transcripts that reproduce nearly all of Jarvis's recent TTS output. */
internal class TtsEchoGuard(
    private val postSpeechWindowMs: Long = VoiceSessionTiming.TTS_ECHO_WINDOW_MS
) {
    private var normalizedSpokenText: String = ""
    private var ignoreUntilMs: Long = 0L

    fun onSpeechStarted(text: String) {
        normalizedSpokenText = normalize(text)
        ignoreUntilMs = Long.MAX_VALUE
    }

    fun onSpeechFinished(nowMs: Long) {
        if (normalizedSpokenText.isNotBlank()) {
            ignoreUntilMs = nowMs + postSpeechWindowMs
        }
    }

    fun shouldIgnore(transcript: String, nowMs: Long): Boolean {
        if (normalizedSpokenText.isBlank() || nowMs > ignoreUntilMs) {
            reset()
            return false
        }
        val candidate = normalize(transcript)
        if (candidate.isBlank()) return false

        val spokenTokens = normalizedSpokenText.split(" ")
        val candidateTokens = candidate.split(" ")
        // Short utterances are common commands ("time kya hua") and are too
        // ambiguous to classify as TTS echo, even when they overlap exactly.
        if (candidateTokens.size < MIN_ECHO_TOKENS) return false

        val overlap = longestCommonSubsequenceLength(candidateTokens, spokenTokens)
        val overlapRatio = overlap.toFloat() / maxOf(candidateTokens.size, spokenTokens.size)
        return overlapRatio >= MIN_ECHO_OVERLAP_RATIO
    }

    private fun longestCommonSubsequenceLength(first: List<String>, second: List<String>): Int {
        var previous = IntArray(second.size + 1)
        for (firstToken in first) {
            val current = IntArray(second.size + 1)
            for (index in second.indices) {
                current[index + 1] = if (firstToken == second[index]) {
                    previous[index] + 1
                } else {
                    maxOf(previous[index + 1], current[index])
                }
            }
            previous = current
        }
        return previous[second.size]
    }

    fun reset() {
        normalizedSpokenText = ""
        ignoreUntilMs = 0L
    }

    private fun normalize(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFC)
            .lowercase()
            .replace(Regex("[^\\p{L}\\p{N}\\p{M}]+"), " ")
            .trim()
            .replace(Regex("\\s+"), " ")

    private companion object {
        const val MIN_ECHO_TOKENS = 4
        const val MIN_ECHO_OVERLAP_RATIO = 0.8f
    }
}
