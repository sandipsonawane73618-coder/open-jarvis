package com.openjarvis.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentResultSpeechTest {
    @Test fun speaksConversationAndClarificationResults() {
        assertEquals("Hello! How can I assist you today?", AgentResultSpeech.textForDone("Hello! How can I assist you today?"))
        assertEquals("Kisko call karun?", AgentResultSpeech.textForDone("Kisko call karun?"))
    }
    @Test fun usesFallbackOnlyForBlankResult() {
        assertEquals("Command completed.", AgentResultSpeech.textForDone("  "))
    }
    @Test fun contactFollowUpWindowAllowsOneNameForThirtySeconds() {
        val window = ContactFollowUpWindow()
        window.start(1_000L)
        assertTrue(window.consume(30_999L))
        assertFalse(window.consume(31_000L))
    }
    @Test fun contactFollowUpExpiresAndCanBeUsedOnlyOnce() {
        val window = ContactFollowUpWindow()
        window.start(1_000L)
        assertFalse(window.consume(31_001L))
        assertFalse(window.consume(2_000L))
    }
}
