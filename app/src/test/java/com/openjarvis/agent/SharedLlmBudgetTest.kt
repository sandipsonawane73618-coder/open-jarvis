package com.openjarvis.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SharedLlmBudgetTest {
    @Test fun planRepairAndReplanShareOneDeadline() {
        val started = 10_000L
        assertEquals(45_000L, SharedLlmBudget.remainingMs(started, started))
        assertEquals(15_000L, SharedLlmBudget.remainingMs(started, 40_000L))
        assertEquals(0L, SharedLlmBudget.remainingMs(started, 55_000L))
        assertFalse(SharedLlmBudget.canStartRequest(started, 55_001L))
    }

    @Test fun budgetNeverBecomesNegative() {
        assertEquals(0L, SharedLlmBudget.remainingMs(1_000L, 99_000L))
        assertTrue(SharedLlmBudget.canStartRequest(1_000L, 1_001L))
    }
}
