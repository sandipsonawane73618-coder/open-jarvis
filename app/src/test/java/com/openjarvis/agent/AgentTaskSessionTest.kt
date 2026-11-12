package com.openjarvis.agent

import org.junit.Assert.*
import org.junit.Test

class AgentTaskSessionTest {
    private val first = Action(action = Action.CLICK, text = "Search")

    @Test fun sessionStartsWithEightActionBudgetAndFortyFiveSecondDeadline() {
        val session = AgentTaskSession("Open YouTube and play first video", 100)
        assertEquals(8, session.remainingActions)
        assertEquals(45_100L, session.deadlineAtMs)
        assertTrue(session.canContinue(100))
    }

    @Test fun completedActionsAreRecordedWithoutMutatingPriorSession() {
        val session = AgentTaskSession("tap Search", 0)
        val next = session.markCompleted(first, "screen-b")
        assertTrue(session.completedActions.isEmpty())
        assertEquals(listOf(first), next.completedActions)
        assertEquals(7, next.remainingActions)
        assertNull(next.pendingAction)
    }

    @Test fun deadlineStopsTheSession() {
        val session = AgentTaskSession("task", 10, deadlineAtMs = 20)
        assertTrue(session.canContinue(19))
        assertFalse(session.canContinue(20))
        assertTrue(session.isExpired(20))
    }

    @Test fun actionLimitStopsTheSession() {
        val session = AgentTaskSession("task", 0, completedActions = List(8) { Action(action = Action.BACK) })
        assertEquals(0, session.remainingActions)
        assertFalse(session.canContinue(1))
    }

    @Test fun confirmationPausesUntilExplicitResolution() {
        val session = AgentTaskSession("send message", 0).requireConfirmation(first)
        assertFalse(session.canContinue(1))
        assertEquals(first, session.awaitingConfirmation)
        val approved = session.resolveConfirmation(true)
        assertTrue(approved.canContinue(1))
        assertEquals(first, approved.pendingAction)
        assertNull(approved.awaitingConfirmation)
    }

    @Test fun declinedConfirmationDoesNotScheduleAction() {
        val declined = AgentTaskSession("delete", 0).requireConfirmation(first).resolveConfirmation(false)
        assertNull(declined.pendingAction)
        assertEquals("User declined confirmation", declined.lastFailure)
        assertFalse(declined.canContinue(1))
    }

    @Test fun finishPreventsFurtherActions() {
        val finished = AgentTaskSession("task", 0).finish()
        assertTrue(finished.isComplete)
        assertFalse(finished.canContinue(1))
    }
}
