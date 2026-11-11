package com.openjarvis.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionRetryPolicyTest {
    @Test fun retriesOnlyIdempotentObservationActions() {
        assertEquals(2, ActionRetryPolicy.maxAttempts(Action(Action.WAIT, text = "Loaded")))
        assertEquals(2, ActionRetryPolicy.maxAttempts(Action(Action.READ_SCREEN)))
        assertEquals(2, ActionRetryPolicy.maxAttempts(Action(Action.EXTRACT_TEXT)))
        assertTrue(ActionRetryPolicy.shouldRetry(Action(Action.WAIT), 1))
        assertFalse(ActionRetryPolicy.shouldRetry(Action(Action.WAIT), 2))
    }

    @Test fun neverRetriesActionsThatMayCauseSideEffects() {
        val sideEffectActions = listOf(
            Action(Action.CLICK, text = "Send"),
            Action(Action.TYPE_TEXT, value = "hello"),
            Action(Action.OPEN_APP, label = "WhatsApp"),
            Action(Action.BACK),
            Action(Action.HOME),
            Action(Action.SCROLL, direction = "down"),
            Action(Action.AI_PROMPT, prompt = "do work")
        )
        sideEffectActions.forEach { action ->
            assertEquals(action.action, 1, ActionRetryPolicy.maxAttempts(action))
            assertFalse(ActionRetryPolicy.shouldRetry(action, 1))
        }
    }
}
