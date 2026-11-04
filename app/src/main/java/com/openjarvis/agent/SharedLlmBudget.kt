package com.openjarvis.agent

/** A single monotonic deadline shared by plan, validation repair and replanning requests. */
internal object SharedLlmBudget {
    const val TOTAL_MS = 45_000L

    fun remainingMs(startedAtMs: Long, nowMs: Long, totalMs: Long = TOTAL_MS): Long =
        (totalMs - (nowMs - startedAtMs)).coerceAtLeast(0L)

    fun canStartRequest(startedAtMs: Long, nowMs: Long, totalMs: Long = TOTAL_MS): Boolean =
        remainingMs(startedAtMs, nowMs, totalMs) > 0L
}
