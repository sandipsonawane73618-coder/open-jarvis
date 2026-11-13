package com.openjarvis.agent

/** Immutable state for one general UI-agent task; legacy StepPipeline state is independent. */
internal data class AgentTaskSession(
    val goal: String,
    val startedAtMs: Long,
    val deadlineAtMs: Long = startedAtMs + DEFAULT_DEADLINE_MS,
    val maxActions: Int = DEFAULT_MAX_ACTIONS,
    val completedActions: List<Action> = emptyList(),
    val pendingAction: Action? = null,
    val lastObservationFingerprint: String? = null,
    val lastFailure: String? = null,
    val awaitingConfirmation: Action? = null,
    val awaitingConfirmationPayload: SendConfirmationPayload? = null,
    val confirmationDeclined: Boolean = false,
    val isComplete: Boolean = false
) {
    init {
        require(goal.isNotBlank())
        require(maxActions in 1..DEFAULT_MAX_ACTIONS)
        require(deadlineAtMs >= startedAtMs)
    }

    val remainingActions: Int get() = (maxActions - completedActions.size).coerceAtLeast(0)
    fun isExpired(nowMs: Long): Boolean = nowMs >= deadlineAtMs
    fun canContinue(nowMs: Long): Boolean = !isComplete && !confirmationDeclined &&
        lastFailure == null && awaitingConfirmation == null && !isExpired(nowMs) && remainingActions > 0
    fun withObservation(fingerprint: String) = copy(lastObservationFingerprint = fingerprint)
    fun withNextAction(action: Action) = copy(pendingAction = action, lastFailure = null)
    fun markCompleted(action: Action, observationFingerprint: String) = copy(
        completedActions = completedActions + action, pendingAction = null,
        lastObservationFingerprint = observationFingerprint, lastFailure = null
    )
    fun markFailed(action: Action?, reason: String) =
        copy(pendingAction = null, lastFailure = reason.ifBlank { "Action failed" })
    fun requireConfirmation(action: Action, payload: SendConfirmationPayload? = null) = copy(
        pendingAction = null, awaitingConfirmation = action, awaitingConfirmationPayload = payload
    )
    fun resolveConfirmation(approved: Boolean) = copy(
        pendingAction = if (approved) awaitingConfirmation else null,
        awaitingConfirmation = null,
        awaitingConfirmationPayload = null,
        lastFailure = if (approved) null else "User declined confirmation",
        confirmationDeclined = !approved
    )
    fun finish() = copy(isComplete = true, pendingAction = null)

    companion object {
        const val DEFAULT_MAX_ACTIONS = 8
        const val DEFAULT_DEADLINE_MS = 45_000L
    }
}
