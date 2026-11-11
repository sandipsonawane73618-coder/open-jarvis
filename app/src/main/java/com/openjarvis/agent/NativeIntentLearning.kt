package com.openjarvis.agent

/**
 * Executes a typed native intent and persists it only after execution completes.
 * Exceptions deliberately propagate; a failed action must never become a learned shortcut.
 */
internal object NativeIntentLearning {
    suspend fun <T> executeAndLearn(
        intent: StepIntent,
        execute: suspend (StepIntent) -> T,
        learn: (StepIntent) -> Unit
    ): T {
        val result = execute(intent)
        learn(intent)
        return result
    }
}
