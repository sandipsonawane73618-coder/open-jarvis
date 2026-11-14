package com.openjarvis.agent

import kotlinx.coroutines.withTimeoutOrNull

/**
 * Gives the online CommandBrain attempt one bounded window. A timeout returns null so
 * AgentCore can continue through its existing offline parsers/local-model fallback.
 */
internal object CommandBrainAttemptPolicy {
    const val TIMEOUT_MS: Long = 1_500L

    suspend fun <T> withinDeadline(
        timeoutMs: Long = TIMEOUT_MS,
        block: suspend () -> T
    ): T? {
        require(timeoutMs > 0L) { "timeoutMs must be positive" }
        return withTimeoutOrNull(timeoutMs) { block() }
    }
}
