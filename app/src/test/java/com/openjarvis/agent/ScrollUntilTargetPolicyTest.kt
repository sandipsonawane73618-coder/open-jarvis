package com.openjarvis.agent

import org.junit.Assert.assertEquals
import org.junit.Test

class ScrollUntilTargetPolicyTest {
    private fun screen(text: String = "", controls: String = "") =
        ScreenObservation("com.example.app", text, controls, 1L)

    @Test fun findsTargetInVisibleScreenTextWithoutScrolling() {
        assertEquals(
            ScrollUntilTargetPolicy.Decision.FOUND,
            ScrollUntilTargetPolicy.decide("Install", screen("App details Install"), 0)
        )
    }

    @Test fun findsTargetInInteractiveContentDescriptions() {
        assertEquals(
            ScrollUntilTargetPolicy.Decision.FOUND,
            ScrollUntilTargetPolicy.decide("Install", screen(controls = "[#2 clickable] desc=Install app"), 2)
        )
    }

    @Test fun continuesScrollingOnlyWhileTargetIsMissingAndBudgetRemains() {
        assertEquals(
            ScrollUntilTargetPolicy.Decision.SCROLL,
            ScrollUntilTargetPolicy.decide("Privacy", screen("Settings"), 3, maxScrolls = 8)
        )
    }

    @Test fun stopsAfterTheConfiguredScrollBudget() {
        assertEquals(
            ScrollUntilTargetPolicy.Decision.EXHAUSTED,
            ScrollUntilTargetPolicy.decide("Privacy", screen("Settings"), 8, maxScrolls = 8)
        )
    }

    @Test fun rejectsBlankTargetsInsteadOfScrollingBlindly() {
        assertEquals(
            ScrollUntilTargetPolicy.Decision.INVALID_TARGET,
            ScrollUntilTargetPolicy.decide("  ", screen("Settings"), 0)
        )
    }
}
