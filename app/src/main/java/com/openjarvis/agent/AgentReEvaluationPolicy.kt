package com.openjarvis.agent

/**
 * OpenDroid-inspired re-evaluation gate.
 *
 * A new plan is allowed only after a failed navigation/observation step and only
 * when every completed step was also non-transactional. Clicks, text entry,
 * app launches, and other potentially side-effecting actions are never replayed.
 */
internal data class ActionExecutionResult(
    val succeeded: Boolean,
    val completedActions: List<Action>,
    val failedAction: Action? = null,
    val failure: String? = null
)

internal object AgentReEvaluationPolicy {
    private val replanSafeActions = setOf(
        Action.WAIT,
        Action.WAIT_FOR,
        Action.READ_SCREEN,
        Action.EXTRACT_TEXT,
        Action.SCROLL,
        Action.BACK,
        Action.PRESS_BACK,
        Action.HOME,
        Action.PRESS_HOME
    )

    fun canReplan(completedActions: List<Action>, failedAction: Action): Boolean =
        failedAction.action in replanSafeActions &&
            completedActions.all { it.action in replanSafeActions }

    fun isAcceptableReplan(
        actions: List<Action>,
        completedActions: List<Action>,
        failedAction: Action
    ): Boolean {
        if (actions.isEmpty()) return false
        if (actions.any { it.action == Action.ERROR || it.action == Action.READ_SCREEN || it.action == Action.EXTRACT_TEXT }) {
            return false
        }
        val previousSteps = completedActions + failedAction
        return actions.none { candidate -> previousSteps.any { previous -> sameStep(candidate, previous) } }
    }

    private fun sameStep(left: Action, right: Action): Boolean =
        left.action == right.action &&
            left.text == right.text &&
            left.value == right.value &&
            left.direction == right.direction &&
            left.label == right.label
}
