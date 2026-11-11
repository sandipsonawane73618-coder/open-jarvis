package com.openjarvis.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SelfHealingSafetyPolicyTest {
    @Test fun onlyWaitAndReadOnlyObservationsCanBeRetried() {
        assertEquals(2, SelfHealingSafetyPolicy.maxAttempts(Action(Action.WAIT)))
        assertEquals(2, SelfHealingSafetyPolicy.maxAttempts(Action(Action.WAIT_FOR, text = "Ready")))
        assertEquals(2, SelfHealingSafetyPolicy.maxAttempts(Action(Action.READ_SCREEN)))
        assertEquals(2, SelfHealingSafetyPolicy.maxAttempts(Action(Action.EXTRACT_TEXT)))
        listOf(
            Action(Action.TAP, text = "Send"),
            Action(Action.TYPE, value = "hello"),
            Action(Action.OPEN_APP, packageName = "com.whatsapp"),
            Action(Action.PRESS_BACK),
            Action(Action.CLICK, text = "Submit")
        ).forEach { action ->
            assertEquals(action.action, 1, SelfHealingSafetyPolicy.maxAttempts(action))
            assertFalse(action.action.toString(), SelfHealingSafetyPolicy.shouldRetry(action, "action failed"))
        }
    }

    @Test fun retryRequiresSafeFailureClassificationForAllReadOnlyObservations() {
        assertTrue(SelfHealingSafetyPolicy.shouldRetry(
            Action(Action.READ_SCREEN), "Could not read current screen (no accessible text)"
        ))
        assertTrue(SelfHealingSafetyPolicy.shouldRetry(
            Action(Action.EXTRACT_TEXT), "Could not extract text from current screen"
        ))
        assertTrue(SelfHealingSafetyPolicy.shouldRetry(
            Action(Action.WAIT), "Timed out waiting for visible text"
        ))
        assertTrue(SelfHealingSafetyPolicy.shouldRetry(
            Action(Action.WAIT_FOR, text = "Ready"), "Timed out waiting for visible text"
        ))
        assertFalse(SelfHealingSafetyPolicy.shouldRetry(
            Action(Action.READ_SCREEN), "Android accessibility service is disabled"
        ))
        assertFalse(SelfHealingSafetyPolicy.shouldRetry(
            Action(Action.EXTRACT_TEXT), "Unexpected internal failure"
        ))
    }

    @Test fun waitActionsDoNotRetryPermissionOrUnknownFailures() {
        listOf(Action.WAIT, Action.WAIT_FOR).forEach { kind ->
            val action = Action(kind, text = "Ready")
            assertFalse("$kind permission failure", SelfHealingSafetyPolicy.shouldRetry(
                action, "Android accessibility permission is disabled"
            ))
            assertFalse("$kind unknown failure", SelfHealingSafetyPolicy.shouldRetry(
                action, "Unexpected internal failure"
            ))
            assertFalse("$kind learned permission failure", SelfHealingSafetyPolicy.shouldRetryAfterLearning(
                action, "Android accessibility permission is disabled", 0
            ))
        }
    }

    @Test fun recurringUnresolvedObservationPausesAutomaticRetry() {
        val action = Action(Action.READ_SCREEN)
        val reason = "Could not read current screen (no accessible text)"
        assertTrue(SelfHealingSafetyPolicy.shouldRetryAfterLearning(action, reason, 2))
        assertFalse(SelfHealingSafetyPolicy.shouldRetryAfterLearning(action, reason, 3))
        assertFalse(SelfHealingSafetyPolicy.shouldRetryAfterLearning(action, reason, 5))
    }

    @Test fun verifiedRecoveryAllowsSafeRetryAgainAfterLearningReset() {
        val action = Action(Action.READ_SCREEN)
        val reason = "Could not read current screen (no accessible text)"
        assertTrue(SelfHealingSafetyPolicy.shouldRetryAfterLearning(action, reason, 0))
        assertTrue(SelfHealingSafetyPolicy.shouldRetryAfterLearning(action, reason, 2))
    }

    @Test fun learningGateNeverEnablesSideEffectReplay() {
        val action = Action(Action.TAP, text = "Send")
        assertFalse(SelfHealingSafetyPolicy.shouldRetryAfterLearning(action, "tap failed", 0))
        assertFalse(SelfHealingSafetyPolicy.shouldRetryAfterLearning(action, "tap failed", 2))
    }

    @Test fun unknownObservationFailureRemainsFailClosed() {
        val action = Action(Action.READ_SCREEN)
        assertFalse(SelfHealingSafetyPolicy.shouldRetryAfterLearning(action, "Unexpected internal failure", 0))
    }

    @Test fun waitDurationIsBounded() {
        assertEquals(0L, SelfHealingSafetyPolicy.boundedWaitMs(Action(Action.WAIT, timeoutMs = -100L)))
        assertEquals(5_000L, SelfHealingSafetyPolicy.boundedWaitMs(Action(Action.WAIT, timeoutMs = 90_000L)))
        assertEquals(1_200L, SelfHealingSafetyPolicy.boundedWaitMs(Action(Action.WAIT, timeoutMs = 1_200L)))
    }

    @Test fun modelAlternativesAreAlwaysRejected() {
        assertFalse(SelfHealingSafetyPolicy.acceptsModelAlternative(Action(Action.BACK)))
        assertFalse(SelfHealingSafetyPolicy.acceptsModelAlternative(Action(Action.OPEN_APP, packageName = "com.whatsapp")))
        assertFalse(SelfHealingSafetyPolicy.acceptsModelAlternative(null))
    }
}
