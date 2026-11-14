package com.openjarvis.agent

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrollUntilTargetRunnerTest {
    private fun screen(text: String) = ScreenObservation("com.example.app", text, "", 1L)

    @Test fun scrollsUntilTargetAppearsThenStopsImmediately() = runBlocking {
        var observations = 0
        var scrolls = 0
        val result = ScrollUntilTargetRunner.run(
            "Privacy",
            observe = { observations++; screen(if (observations == 4) "Privacy settings" else "Settings page $observations") },
            scroll = { scrolls++; true }
        )
        assertTrue(result.found)
        assertEquals(3, scrolls)
        assertEquals(4, observations)
    }

    @Test fun stopsAfterEightScrollsWithHonestFailure() = runBlocking {
        var scrolls = 0
        val result = ScrollUntilTargetRunner.run(
            "Privacy",
            observe = { screen("Settings page") },
            scroll = { scrolls++; true }
        )
        assertFalse(result.found)
        assertEquals(8, scrolls)
        assertTrue(result.reason.contains("8"))
        assertTrue(result.reason.contains("Privacy"))
    }

    @Test fun doesNotScrollWhenTargetIsAlreadyVisible() = runBlocking {
        var scrolls = 0
        val result = ScrollUntilTargetRunner.run(
            "Privacy",
            observe = { screen("Privacy settings") },
            scroll = { scrolls++; true }
        )
        assertTrue(result.found)
        assertEquals(0, scrolls)
    }

    @Test fun reportsFailureWhenNoScrollableContainerAcceptsAction() = runBlocking {
        var scrolls = 0
        val result = ScrollUntilTargetRunner.run(
            "Privacy",
            observe = { screen("Settings page") },
            scroll = { scrolls++; false }
        )
        assertFalse(result.found)
        assertEquals(1, scrolls)
        assertTrue(result.reason.contains("scroll"))
    }
}
