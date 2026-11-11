package com.openjarvis.agent

/**
 * Retries only idempotent observation actions.
 *
 * UI clicks and text entry can trigger irreversible side effects (send, submit,
 * purchase, toggle) even when the accessibility acknowledgement is lost. Retrying
 * those actions can duplicate work, so they are deliberately single-attempt.
 */
internal object ActionRetryPolicy {
    fun maxAttempts(action: Action): Int = when (action.action) {
        Action.WAIT, Action.WAIT_FOR, Action.READ_SCREEN, Action.EXTRACT_TEXT -> 2
        else -> 1
    }

    fun shouldRetry(action: Action, attempt: Int): Boolean =
        attempt < maxAttempts(action)
}
