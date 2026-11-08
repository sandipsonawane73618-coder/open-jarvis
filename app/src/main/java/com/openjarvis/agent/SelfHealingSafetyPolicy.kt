package com.openjarvis.agent

/**
 * Fail-closed retry policy for self-healing.
 * Only a bounded wait may be repeated; UI actions are never replayed because
 * the app may have accepted the action even when acknowledgement was lost.
 */
internal object SelfHealingSafetyPolicy {
    const val MAX_WAIT_ATTEMPTS = 2
    const val MAX_WAIT_MS = 5_000L

    fun maxAttempts(action: Action): Int =
        if (action.action == Action.WAIT || action.action == Action.WAIT_FOR) MAX_WAIT_ATTEMPTS else 1

    fun boundedWaitMs(action: Action): Long =
        action.timeoutMs.coerceIn(0L, MAX_WAIT_MS)

    /** LLM-generated alternatives are deliberately not executable. */
    fun acceptsModelAlternative(action: Action?): Boolean = false
}
