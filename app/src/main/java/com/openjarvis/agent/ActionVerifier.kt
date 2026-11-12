package com.openjarvis.agent

/** Verifies an action against a fresh screen observation; action acceptance alone is insufficient. */
internal object ActionVerifier {
    data class Verification(val verified: Boolean, val reason: String)

    fun verify(
        action: Action,
        before: ScreenObservation,
        after: ScreenObservation,
        executionAccepted: Boolean,
        expectedPackage: String? = action.packageName
    ): Verification {
        if (!executionAccepted) return Verification(false, "Android did not accept the action")
        val changed = before.fingerprint != after.fingerprint
        return when (action.action) {
            Action.OPEN_APP -> {
                val expected = expectedPackage
                if (expected.isNullOrBlank()) Verification(false, "Target app package is unresolved")
                else if (after.packageName == expected) Verification(true, "Target app is foreground")
                else Verification(false, "Target app did not become foreground")
            }
            Action.TYPE, Action.TYPE_TEXT -> {
                val value = action.value.orEmpty()
                if (value.isNotEmpty() && after.visibleText.contains(value)) {
                    Verification(true, "Typed text is visible")
                } else Verification(false, "Typed text could not be verified on screen")
            }
            Action.WAIT, Action.WAIT_FOR -> {
                val expected = action.text.orEmpty()
                if (expected.isNotBlank() && after.visibleText.contains(expected, ignoreCase = true)) {
                    Verification(true, "Expected text is visible")
                } else Verification(false, "Expected text is not visible")
            }
            Action.CLICK, Action.TAP -> {
                val target = action.text.orEmpty()
                val targetStillVisible = target.isNotBlank() &&
                    (after.visibleText.contains(target, true) || after.interactiveElements.contains(target, true))
                if (changed || !targetStillVisible) Verification(true, "Screen changed after tap")
                else Verification(false, "Tap was accepted but the screen did not visibly change")
            }
            Action.SCROLL, Action.BACK, Action.PRESS_BACK, Action.HOME, Action.PRESS_HOME,
            Action.PRESS_RECENTS -> if (changed) {
                Verification(true, "Screen changed after navigation action")
            } else Verification(false, "Screen did not change after navigation action")
            Action.READ_SCREEN, Action.EXTRACT_TEXT -> Verification(false, "Observation is not an executable action")
            Action.ERROR -> Verification(false, action.message ?: "Planner returned an error")
            else -> if (changed) Verification(true, "Fresh screen differs from previous observation")
                else Verification(false, "No observable result after action")
        }
    }
}
