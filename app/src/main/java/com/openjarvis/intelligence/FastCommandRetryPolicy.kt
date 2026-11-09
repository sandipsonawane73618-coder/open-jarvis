package com.openjarvis.intelligence

/**
 * Fast commands may launch apps, change navigation state, mutate memory, or open system UI.
 * Their completion can be ambiguous after an exception, so none is replayed automatically.
 */
internal object FastCommandRetryPolicy {
    fun maxAttempts(command: FastCommandParser.Command): Int = 1

    fun shouldRetry(command: FastCommandParser.Command, attempt: Int): Boolean =
        attempt < maxAttempts(command)
}
