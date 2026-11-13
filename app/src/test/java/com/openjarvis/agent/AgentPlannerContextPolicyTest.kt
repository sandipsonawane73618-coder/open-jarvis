package com.openjarvis.agent

import org.junit.Assert.*
import org.junit.Test

class AgentPlannerContextPolicyTest {
    private fun action(label: String) = Action(action = Action.CLICK, text = label)
    private fun screen() = ScreenObservation(
        packageName = "com.example.app",
        visibleText = "Settings Search Privacy",
        interactiveElements = "[#1 clickable] text=Search\n[#2 clickable] desc=Privacy",
        observedAtMs = 10L
    )

    @Test fun contextAlwaysPreservesTheUserGoal() {
        val goal = "Open Settings and find Privacy"
        val context = AgentPlannerContextPolicy.build(goal, emptyList(), screen())
        assertTrue(context.contains(goal))
    }

    @Test fun contextIncludesOnlyTheThreeMostRecentVerifiedSteps() {
        val steps = listOf("old-1", "old-2", "old-3", "recent-1", "recent-2", "recent-3")
            .map(::action)
        val context = AgentPlannerContextPolicy.build("Find Privacy", steps, screen())
        assertFalse(context.contains("old-1"))
        assertFalse(context.contains("old-2"))
        assertFalse(context.contains("old-3"))
        assertTrue(context.contains("recent-1"))
        assertTrue(context.contains("recent-2"))
        assertTrue(context.contains("recent-3"))
    }

    @Test fun contextIncludesGroundedInteractiveElements() {
        val context = AgentPlannerContextPolicy.build("Find Privacy", emptyList(), screen())
        assertTrue(context.contains("text=Search"))
        assertTrue(context.contains("desc=Privacy"))
    }

    @Test fun contextIsBoundedWithoutTruncatingTheGoal() {
        val goal = "Find the privacy setting"
        val hugeScreen = ScreenObservation("com.example.app", "x".repeat(5000), "y".repeat(5000), 10L)
        val context = AgentPlannerContextPolicy.build(goal, emptyList(), hugeScreen, maxChars = 1200)
        assertTrue(context.contains(goal))
        assertTrue(context.length <= 1200)
    }

    @Test fun blankGoalIsRejected() {
        val context = AgentPlannerContextPolicy.build("  ", emptyList(), screen())
        assertTrue(context.isEmpty())
    }
}
