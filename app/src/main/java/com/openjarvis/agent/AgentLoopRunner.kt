package com.openjarvis.agent
import kotlinx.coroutines.withTimeoutOrNull

internal enum class AgentLoopStatus { COMPLETED, NEEDS_CONFIRMATION, FAILED, LIMIT_REACHED, CANCELLED }
internal data class AgentLoopRunResult(val status: AgentLoopStatus, val session: AgentTaskSession, val message: String)
internal data class GoalVerification(val verified: Boolean, val reason: String)

/** One action per cycle, followed by a fresh screen observation and verification. */
internal class AgentLoopRunner {
    suspend fun run(
        initialSession: AgentTaskSession,
        approvedConfirmation: Boolean = false,
        nowMs: () -> Long = System::currentTimeMillis,
        observe: suspend () -> ScreenObservation,
        plan: suspend (ScreenObservation, AgentTaskSession) -> Result<Action>,
        execute: suspend (Action) -> Boolean,
        verify: (Action, ScreenObservation, ScreenObservation, Boolean) -> ActionVerifier.Verification,
        verifyGoal: ((String, ScreenObservation) -> GoalVerification)? = null
    ): AgentLoopRunResult {
        val remainingMs = initialSession.deadlineAtMs - nowMs()
        if (remainingMs <= 0L) return failed(initialSession, "Task deadline exceeded (45 seconds).")
        val result = withTimeoutOrNull(remainingMs) {
            runWithinDeadline(initialSession, approvedConfirmation, nowMs, observe, plan, execute, verify, verifyGoal)
        }
        return result ?: failed(initialSession, "Task timed out after 45 seconds; completed actions were not replayed.")
    }

    private suspend fun runWithinDeadline(
        initial: AgentTaskSession, approvedConfirmation: Boolean, nowMs: () -> Long,
        observe: suspend () -> ScreenObservation,
        plan: suspend (ScreenObservation, AgentTaskSession) -> Result<Action>,
        execute: suspend (Action) -> Boolean,
        verify: (Action, ScreenObservation, ScreenObservation, Boolean) -> ActionVerifier.Verification,
        verifyGoal: ((String, ScreenObservation) -> GoalVerification)?
    ): AgentLoopRunResult {
        var session = initial
        val pending = session.awaitingConfirmation
        if (pending != null) {
            if (!approvedConfirmation) return AgentLoopRunResult(AgentLoopStatus.NEEDS_CONFIRMATION, session, confirmationMessage(pending))
            session = session.resolveConfirmation(true)
            if (ActionRiskPolicy.isPaymentAction(pending)) return failed(session, BankingSafetyPolicy.userGuidance(BankingSafetyPolicy.BLOCKED_TRANSACTION))
            val before = observe()
            val accepted = runCatching { execute(pending) }.getOrDefault(false)
            val after = observe()
            // Check the user's goal against every fresh screen, but allow intermediate
            // screens while the planner still has more steps to perform.
            verifyGoal?.invoke(session.goal, after)
            val checked = verify(pending, before, after, accepted)
            if (!checked.verified) return failed(session.markFailed(pending, checked.reason), checked.reason)
            session = session.markCompleted(pending, after.fingerprint)
        } else if (approvedConfirmation) return failed(session, "There is no pending action awaiting confirmation.")

        while (session.canContinue(nowMs())) {
            val before = observe()
            session = session.withObservation(before.fingerprint)
            val action = plan(before, session).getOrElse { error ->
                return failed(session, error.message?.takeIf { it.isNotBlank() } ?: "Could not choose the next action.")
            }
            if (action.action == Action.COMPLETE) {
                if (session.completedActions.isEmpty()) return failed(session, "Planner declared completion before any action was verified.")
                val goalCheck = verifyGoal?.invoke(session.goal, before)
                    ?: return failed(session, "Independent goal verification is unavailable; refusing to report success.")
                if (!goalCheck.verified) return failed(session, goalCheck.reason)
                return AgentLoopRunResult(AgentLoopStatus.COMPLETED, session.finish(),
                    "Goal completed; ${session.completedActions.size} action(s) verified.")
            }
            if (ActionRiskPolicy.requiresConfirmation(action)) {
                val waiting = session.requireConfirmation(action)
                return AgentLoopRunResult(AgentLoopStatus.NEEDS_CONFIRMATION, waiting, confirmationMessage(action))
            }
            if (ActionRiskPolicy.isPaymentAction(action) || BankingSafetyPolicy.failureRule(session.goal, listOf(action)) != null) {
                return failed(session, BankingSafetyPolicy.userGuidance(BankingSafetyPolicy.BLOCKED_TRANSACTION))
            }
            session = session.withNextAction(action)
            var verified: ActionVerifier.Verification? = null
            var attempts = 0
            val maxAttempts = ActionRetryPolicy.maxAttempts(action)
            while (attempts < maxAttempts) {
                attempts++
                val accepted = runCatching { execute(action) }.getOrDefault(false)
                val after = observe()
                // Evaluate goal evidence after each action; intermediate false results
                // are not terminal because the planner may need another action.
                verifyGoal?.invoke(session.goal, after)
                verified = verify(action, before, after, accepted)
                if (verified.verified) { session = session.markCompleted(action, after.fingerprint); break }
                if (!ActionRetryPolicy.shouldRetry(action, attempts)) break
            }
            if (verified?.verified != true) {
                val reason = verified?.reason ?: "Action did not verify."
                return failed(session.markFailed(action, reason), reason)
            }
        }
        return if (session.remainingActions == 0) AgentLoopRunResult(
            AgentLoopStatus.LIMIT_REACHED, session,
            "Reached the ${session.maxActions}-action limit; goal is not confirmed complete."
        ) else failed(session, "Task stopped before goal completion.")
    }
    private fun confirmationMessage(action: Action): String {
        val target = action.text ?: action.hint ?: action.action
        return "Confirmation required before '$target'. Say 'yes confirm' to proceed or 'no cancel' to stop."
    }
    private fun failed(session: AgentTaskSession, message: String) = AgentLoopRunResult(AgentLoopStatus.FAILED, session, message)
}
