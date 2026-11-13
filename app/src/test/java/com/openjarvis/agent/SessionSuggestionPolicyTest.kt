package com.openjarvis.agent

import org.junit.Assert.*
import org.junit.Test

class SessionSuggestionPolicyTest {
    @Test fun offersAtMostOneUsefulSuggestionPerSession() {
        val first = SessionSuggestionPolicy.choose(
            sessionId = "s1", alreadyShown = false,
            memoryNotes = listOf("Bike service due today"),
            hourOfDay = 9
        )
        assertNotNull(first)
        assertTrue(first!!.contains("Bike service"))
        assertNull(SessionSuggestionPolicy.choose(
            sessionId = "s1", alreadyShown = true,
            memoryNotes = listOf("Pay electricity bill"),
            hourOfDay = 18
        ))
    }

    @Test fun suggestionRequiresExplicitYesBeforeAnyAction() {
        assertFalse(SessionSuggestionPolicy.mayAct("maybe"))
        assertFalse(SessionSuggestionPolicy.mayAct("no"))
        assertTrue(SessionSuggestionPolicy.mayAct("yes"))
        assertTrue(SessionSuggestionPolicy.mayAct("haan"))
    }
}
