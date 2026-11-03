package com.openjarvis.graphify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalysisEngineTest {

    @Test
    fun `sequence hash is stable for identical input`() {
        val first = AnalysisEngine.hashSequence("open app → send message → close app")
        val second = AnalysisEngine.hashSequence("open app → send message → close app")

        assertEquals(first, second)
        assertEquals(16, first.length)
    }

    @Test
    fun `different sequences produce different hashes`() {
        val first = AnalysisEngine.hashSequence("open app → send message")
        val second = AnalysisEngine.hashSequence("open app → make call")

        assertNotEquals(first, second)
    }

    @Test
    fun `levenshtein distance handles exact and fuzzy contact names`() {
        assertEquals(0, AnalysisEngine.levenshteinDistance("john doe", "john doe"))
        assertEquals(2, AnalysisEngine.levenshteinDistance("john doe", "jhon doe"))
        assertTrue(AnalysisEngine.levenshteinDistance("john doe", "jon doe") <= 2)
    }
}
