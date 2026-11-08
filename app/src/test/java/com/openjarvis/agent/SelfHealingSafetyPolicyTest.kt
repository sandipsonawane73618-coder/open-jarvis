package com.openjarvis.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SelfHealingSafetyPolicyTest {
    @Test fun onlyWaitCanBeRetried() {
        assertEquals(2, SelfHealingSafetyPolicy.maxAttempts(Action(Action.WAIT)))
        assertEquals(2, SelfHealingSafetyPolicy.maxAttempts(Action(Action.WAIT_FOR, text = "Ready")))
        listOf(
            Action(Action.TAP, text = "Send"),
            Action(Action.TYPE, value = "hello"),
            Action(Action.OPEN_APP, packageName = "com.whatsapp"),
            Action(Action.PRESS_BACK),
            Action(Action.CLICK, text = "Submit")
        ).forEach { action ->
            assertEquals(action.action, 1, SelfHealingSafetyPolicy.maxAttempts(action))
        }
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
