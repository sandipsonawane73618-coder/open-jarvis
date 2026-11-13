package com.openjarvis.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClarificationWorkflowPolicyTest {
    @Test
    fun usesRouterGeneratedQuestionInsteadOfFixedPrompt() {
        val result = ClarificationWorkflowPolicy.resolve(
            routerResponse = """{"route":"ask_clarify","question":"Kya aap YouTube par kuch chalana chahte ho?"}""",
            currentCommand = "kuch bhi"
        )

        assertTrue(result is ClarificationWorkflowPolicy.Decision.Ask)
        val ask = result as ClarificationWorkflowPolicy.Decision.Ask
        assertEquals("Kya aap YouTube par kuch chalana chahte ho?", ask.question)
        assertEquals(1, ask.pending.round)
        assertFalse(ask.question == ClarificationWorkflowPolicy.OFFLINE_FALLBACK)
    }

    @Test
    fun usesFixedPromptOnlyWhenRouterIsUnavailable() {
        val result = ClarificationWorkflowPolicy.resolve(
            routerResponse = null,
            currentCommand = "kuch bhi"
        )

        assertTrue(result is ClarificationWorkflowPolicy.Decision.Ask)
        val ask = result as ClarificationWorkflowPolicy.Decision.Ask
        assertEquals(ClarificationWorkflowPolicy.OFFLINE_FALLBACK, ask.question)
        assertEquals(1, ask.pending.round)
    }

    @Test
    fun allowsAtMostTwoClarificationRounds() {
        val first = ClarificationWorkflowPolicy.resolve(
            """{"route":"ask_clarify","question":"Kya chalana hai?"}""",
            "kuch bhi"
        ) as ClarificationWorkflowPolicy.Decision.Ask
        val second = ClarificationWorkflowPolicy.resolve(
            """{"route":"ask_clarify","question":"Kaunsa song?"}""",
            "music",
            first.pending
        ) as ClarificationWorkflowPolicy.Decision.Ask
        val third = ClarificationWorkflowPolicy.resolve(
            """{"route":"ask_clarify","question":"Kis singer ka?"}""",
            "kuch bhi",
            second.pending
        )

        assertEquals(2, second.pending.round)
        assertEquals(ClarificationWorkflowPolicy.Decision.Exhausted, third)
    }

    @Test
    fun resolvedAnswerRetainsOriginalGoalAndNeverExecutesAnythingItself() {
        val first = ClarificationWorkflowPolicy.resolve(
            """{"route":"ask_clarify","question":"Kya aap YouTube par kuch chalana chahte ho?"}""",
            "kuch bhi"
        ) as ClarificationWorkflowPolicy.Decision.Ask
        val resolved = ClarificationWorkflowPolicy.resolve(
            """{"route":"action","command":"YouTube par Arijit Singh ka song play karo"}""",
            "YouTube par Arijit Singh ka song",
            first.pending
        )

        assertTrue(resolved is ClarificationWorkflowPolicy.Decision.Continue)
        val command = (resolved as ClarificationWorkflowPolicy.Decision.Continue).command
        assertTrue(command.contains("kuch bhi"))
        assertTrue(command.contains("Arijit Singh"))
    }

    @Test
    fun malformedRouterOutputFallsBackToClarificationNotUnknownCommandError() {
        val result = ClarificationWorkflowPolicy.resolve(
            "not-json",
            "kuch bhi"
        )

        assertTrue(result is ClarificationWorkflowPolicy.Decision.Ask)
        assertFalse((result as ClarificationWorkflowPolicy.Decision.Ask).question.contains("Samajh nahi aaya"))
    }
}
