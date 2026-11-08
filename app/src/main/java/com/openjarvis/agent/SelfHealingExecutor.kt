package com.openjarvis.agent

import android.content.Context
import com.openjarvis.accessibility.JarvisAccessibilityService
import kotlinx.coroutines.delay

/**
 * Fail-closed action recovery.
 *
 * A UI action is attempted once. If acknowledgement is lost, replaying a tap or
 * text entry could submit/send twice. The model is never allowed to invent and
 * execute an alternate action. Only an explicit bounded WAIT is repeatable.
 */
class SelfHealingExecutor(private val context: Context) {

    suspend fun executeWithHealing(
        action: Action,
        context: ExecutionContext,
        attempt: Int = 1
    ): ActionResult {
        val maxAttempts = SelfHealingSafetyPolicy.maxAttempts(action)
        val firstAttempt = attempt.coerceAtLeast(1).coerceAtMost(maxAttempts)

        for (currentAttempt in firstAttempt..maxAttempts) {
            val result = tryExecuteAction(action)
            if (result is ActionResult.Success) return result
            if (currentAttempt >= maxAttempts) return result
            if (action.action == Action.WAIT) {
                delay(SelfHealingSafetyPolicy.boundedWaitMs(action))
            }
        }
        return ActionResult.Failed("Action could not be completed safely")
    }

    private suspend fun tryExecuteAction(action: Action): ActionResult {
        return try {
            when (action.action) {
                Action.WAIT -> {
                    delay(SelfHealingSafetyPolicy.boundedWaitMs(action))
                    ActionResult.Success("wait completed")
                }
                Action.TAP, Action.CLICK -> {
                    val target = action.text ?: action.hint
                    if (target.isNullOrBlank()) ActionResult.Failed("tap target missing")
                    else if (JarvisAccessibilityService.instance?.tapByText(target) == true)
                        ActionResult.Success("tap acknowledged")
                    else ActionResult.Failed("tap was not acknowledged")
                }
                Action.TYPE, Action.TYPE_TEXT -> {
                    val value = action.value
                    if (value.isNullOrEmpty()) ActionResult.Failed("text value missing")
                    else if (JarvisAccessibilityService.instance?.typeText(value) == true)
                        ActionResult.Success("text entry acknowledged")
                    else ActionResult.Failed("text entry was not acknowledged")
                }
                Action.OPEN_APP -> {
                    val service = JarvisAccessibilityService.instance
                    val opened = when {
                        service == null -> false
                        !action.packageName.isNullOrBlank() -> service.openAppByPackage(action.packageName)
                        !action.label.isNullOrBlank() -> service.openAppByLabel(action.label)
                        else -> false
                    }
                    if (opened) ActionResult.Success("app launch acknowledged")
                    else ActionResult.Failed("app launch was not acknowledged")
                }
                Action.PRESS_BACK, Action.BACK -> {
                    val service = JarvisAccessibilityService.instance
                    if (service != null) {
                        service.pressBack()
                        ActionResult.Success("back action dispatched")
                    } else ActionResult.Failed("accessibility service unavailable")
                }
                else -> ActionResult.Failed("unsupported action; no automatic recovery attempted")
            }
        } catch (_: Exception) {
            ActionResult.Failed("action failed; no automatic replay attempted")
        }
    }

    data class ExecutionContext(
        val expectedState: String?,
        val phaseId: String,
        val previousPhaseOutcome: String?,
        val screenBefore: String
    )

    sealed class ActionResult {
        data class Success(val message: String) : ActionResult()
        data class Failed(val reason: String) : ActionResult()
    }
}
