package com.openjarvis.commandlab

import org.junit.Assert.*
import org.junit.Test

class AgentAppMatrixSelfTestTest {
    @Test
    fun matrixCoversAllTenRequiredAppTypes() {
        val results = AgentAppMatrixSelfTest.run()
        assertEquals(
            setOf("messaging", "video", "music", "browser", "settings", "notes", "maps", "shopping", "mail", "camera"),
            results.map { it.appType }.toSet()
        )
        assertEquals(10, results.size)
    }

    @Test
    fun everyFakeAppUsesTheSameGroundedSearchAndInputActions() {
        val results = AgentAppMatrixSelfTest.run()
        val failures = results.filterNot { it.passed }
        assertTrue(failures.joinToString("\n") { "${it.appType}: ${it.reason}" }, failures.isEmpty())
        results.forEach { result ->
            assertEquals(Action.CLICK, result.searchAction?.action)
            assertEquals("Search", result.searchAction?.text)
            assertEquals(Action.TYPE_TEXT, result.typeAction?.action)
            assertEquals("Jarvis test query", result.typeAction?.value)
            assertEquals("Search", result.typeAction?.text)
        }
    }

    @Test
    fun matrixReportsIndividualAppResultsWithoutExecutingRealActions() {
        val results = AgentAppMatrixSelfTest.run()
        assertEquals(10, results.count { it.passed } + results.count { !it.passed })
        assertTrue(results.all { it.packageName.startsWith("com.example.fake.") })
        assertTrue(results.all { it.reason.isNotBlank() })
    }
}
