package com.openjarvis.agent

import android.content.Context
import android.util.Log

/**
 * Live fail-closed recovery gate used by AgentCore after an action failure.
 * It never replays UI side effects. AgentCore may retry only WAIT/WAIT_FOR
 * through SelfHealingSafetyPolicy because observing a condition is idempotent.
 */
class SelfHealingExecutor(private val context: Context) {
    fun failureDisposition(action: Action, reason: String): String {
        val disposition = if (action.action == Action.WAIT || action.action == Action.WAIT_FOR) {
            "A bounded condition-check retry is permitted by policy."
        } else {
            "No automatic replay: the action may already have taken effect."
        }
        Log.w("JarvisSelfHealing", "action=${action.action} disposition=$disposition reason=${reason.take(180)}")
        return disposition
    }
}
