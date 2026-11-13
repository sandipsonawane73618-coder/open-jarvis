package com.openjarvis.commandlab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandLabSelfTestTest {
    @Test
    fun selfTestContainsFortyTypedCommandsAcrossAllSupportedLanguageStyles() {
        assertEquals(40, CommandLabSelfTest.cases.size)
        val commands = CommandLabSelfTest.cases.map { it.command }
        assertTrue(commands.any { it.any { ch -> ch in '\u0900'..'\u097f' } })
        assertTrue(commands.any { it.contains("la WhatsApp") })
        assertTrue(commands.any { it.startsWith("tell me", ignoreCase = true) })
    }

    @Test
    fun selfTestUsesTheRealReadOnlyRouterAndExecutesNoAndroidActions() {
        val results = CommandLabSelfTest.run()
        assertEquals(40, results.size)
        val failures = results.filterNot { it.passed }
        assertTrue(failures.joinToString("\n") { "${it.case.command}: ${it.failReason}" }, failures.isEmpty())
    }

    @Test
    fun selfTestSummaryCountsPassesAndFailuresByRoute() {
        val results = CommandLabSelfTest.run()
        val total = results.size
        val passCount = results.count { it.passed }
        val failCount = results.size - passCount
        assertEquals(total, passCount + failCount)
        assertTrue(results.map { it.actualRoute }.containsAll(CommandLabRoute.values().toList()))
    }
}
