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
        assertEquals(48, results.size)
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
        assertTrue(results.map { it.actualRoute }.containsAll(listOf(CommandLabRoute.SKILL, CommandLabRoute.STEP, CommandLabRoute.SEARCH, CommandLabRoute.CHAT, CommandLabRoute.LOOP, CommandLabRoute.PIPELINE, CommandLabRoute.CLARIFY)))
    }
    private fun assertExtraCase(name: String) {
        val result = CommandLabSelfTest.run().single { it.case.name == name }
        assertEquals(name, result.case.expectedRoute, result.actualRoute)
        assertEquals(name, result.case.expectedResult, result.actualResult)
        assertTrue("$name: ${result.failReason}", result.passed)
    }

    @Test fun extraVolumeCaseReportsVolumeUpAndTorchUntouched() = assertExtraCase("Extra 1 volume up without torch")
    @Test fun extraWhatsAppCaseParsesMessageAndAvoidsBankingFalsePositive() = assertExtraCase("Extra 2 WhatsApp message without banking block")
    @Test fun extraYouTubeCaseNormalizesQueryAndRequiresVerifiedPlayback() = assertExtraCase("Extra 3 YouTube play Arijeet")
    @Test fun extraPlayWithoutQueryAsksTheExactClarification() = assertExtraCase("Extra 4 play without query clarifies")
    @Test fun extraBackPhraseRoutesToGlobalBackNotNavigation() = assertExtraCase("Extra 5 global back before navigation")
    @Test fun extraInstagramCaseUsesDeterministicPipelineWithoutLlm() = assertExtraCase("Extra 6 Instagram open then in-app search")
    @Test fun extraFactualQuestionUsesChatNotActionLoop() = assertExtraCase("Extra 7 India capital is chat")
    @Test fun extraVagueCommandClarifiesWithoutAnyAction() = assertExtraCase("Extra 8 vague command clarifies safely")

}
