package com.openjarvis.voice

/** Debounces repeated acoustic keyword events without changing model sensitivity. */
object WakeWordDetectionPolicy {
    const val DEFAULT_COOLDOWN_MS = 2_000L

    fun shouldAccept(
        nowMs: Long,
        lastAcceptedAtMs: Long,
        cooldownMs: Long = DEFAULT_COOLDOWN_MS
    ): Boolean {
        if (nowMs < 0L || cooldownMs < 0L) return false
        if (lastAcceptedAtMs <= 0L) return true
        val elapsed = nowMs - lastAcceptedAtMs
        return elapsed >= cooldownMs
    }
}
