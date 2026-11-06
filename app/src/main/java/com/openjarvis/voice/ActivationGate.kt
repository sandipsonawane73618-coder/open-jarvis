package com.openjarvis.voice

/**
 * One-shot authorization for a voice transcript. Wake-word and manual-mic activation
 * open a short command window; typed UI input does not use this gate.
 */
class ActivationGate(
    private val windowMs: Long = DEFAULT_WINDOW_MS,
    private val duplicateCooldownMs: Long = DEFAULT_DUPLICATE_COOLDOWN_MS,
    private val minimumTranscriptLength: Int = 3
) {
    enum class Source { WAKE_WORD, PUSH_TO_TALK, AUTO_VAD }

    data class Decision(val accepted: Boolean, val reason: String)

    private var activatedAtMs: Long? = null
    private var activationSource: Source? = null
    private var consumed = false
    private var lastAcceptedText: String? = null
    private var lastAcceptedAtMs: Long = Long.MIN_VALUE

    @Synchronized
    fun activate(source: Source, nowMs: Long = System.currentTimeMillis()) {
        activatedAtMs = nowMs
        activationSource = source
        consumed = false
    }

    @Synchronized
    fun validateTranscript(transcript: String, nowMs: Long = System.currentTimeMillis()): Decision {
        val text = transcript.trim()
        if (text.isEmpty()) return Decision(false, "empty transcript")
        if (text.length < minimumTranscriptLength) return Decision(false, "transcript too short")
        val startedAt = activatedAtMs ?: return Decision(false, "no explicit activation")
        val elapsed = nowMs - startedAt
        if (elapsed < 0L || elapsed > windowMs) {
            activatedAtMs = null
            activationSource = null
            consumed = true
            return Decision(false, "activation window expired")
        }
        if (consumed) return Decision(false, "activation already consumed")
        val normalized = text.lowercase().replace(Regex("\\s+"), " ")
        if (normalized == lastAcceptedText && nowMs - lastAcceptedAtMs in 0..duplicateCooldownMs) {
            consumed = true
            return Decision(false, "duplicate transcript cooldown")
        }
        consumed = true
        lastAcceptedText = normalized
        lastAcceptedAtMs = nowMs
        return Decision(true, "accepted via ${activationSource?.name ?: "activation"}")
    }

    @Synchronized
    fun reset() {
        activatedAtMs = null
        activationSource = null
        consumed = true
    }

    companion object {
        const val DEFAULT_WINDOW_MS = 10_000L
        const val DEFAULT_DUPLICATE_COOLDOWN_MS = 2_500L
    }
}
