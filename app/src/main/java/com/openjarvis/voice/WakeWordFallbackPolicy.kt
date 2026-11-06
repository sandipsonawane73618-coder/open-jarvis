package com.openjarvis.voice

/** Decides whether a live Sherpa listener has failed to recognize speech and may fall back. */
object WakeWordFallbackPolicy {
    enum class Decision { WAIT, FALLBACK_TO_PORCUPINE, NO_FALLBACK_KEY }

    const val DEFAULT_TIMEOUT_MS = 45_000L
    const val MIN_ACTIVE_FRAMES = 10L

    fun evaluate(
        nowMs: Long,
        startedAtMs: Long,
        lastDetectionAtMs: Long?,
        framesProcessed: Long,
        silentFrames: Long,
        hasValidAccessKey: Boolean,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS
    ): Decision {
        if (startedAtMs <= 0L || nowMs - startedAtMs < timeoutMs) return Decision.WAIT
        if (lastDetectionAtMs != null && nowMs - lastDetectionAtMs < timeoutMs) return Decision.WAIT
        val activeFrames = (framesProcessed - silentFrames).coerceAtLeast(0L)
        if (activeFrames < MIN_ACTIVE_FRAMES) return Decision.WAIT
        return if (hasValidAccessKey) Decision.FALLBACK_TO_PORCUPINE else Decision.NO_FALLBACK_KEY
    }
}
