package com.openjarvis.agent

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class NextActionPlannerTest {
    private val planner = NextActionPlanner()
    private val screen = ScreenObservation(
        "com.google.android.youtube", "Home", "[#1 clickable] text=VideoOne bounds=0,0,10,10", 1
    )

    @Test fun deterministicActionWinsWithoutCallingLlm() = runBlocking {
        var called = false
        val result = planner.chooseNextAction("Search button tap karo", screen,
            { _, _ -> Action(action = Action.CLICK, text = "Search") },
            { called = true; Result.success("""[{"action":"back"}]""") })
        assertEquals("Search", result.getOrThrow().text)
        assertFalse(called)
    }

    @Test fun llmMustReturnExactlyOneJsonAction() = runBlocking {
        val one = planner.chooseNextAction("screen par button tap karo", screen, { _, _ -> null },
            { Result.success("""[{"action":"click","target":"Search"}]""") })
        assertEquals(Action.CLICK, one.getOrThrow().action)
        val many = planner.chooseNextAction("screen par button tap karo", screen, { _, _ -> null },
            { Result.success("""[{"action":"back"},{"action":"home"}]""") })
        assertTrue(many.isFailure)
        assertTrue(many.exceptionOrNull()?.message.orEmpty().contains("exactly one"))
    }

    @Test fun invalidAndUnsafeActionsAreRejected() = runBlocking {
        val invalid = planner.chooseNextAction("tap button", screen, { _, _ -> null },
            { Result.success("not json") })
        assertTrue(invalid.isFailure)
        val send = planner.chooseNextAction("send message", screen, { _, _ -> null },
            { Result.success("""[{"action":"click","target":"Send"}]""") })
        assertTrue(send.isSuccess)
        assertEquals("Send", send.getOrThrow().text)
    }

    @Test fun modelGeneratedSendTapRequiresRunnerConfirmationBeforeExecution() = runBlocking {
        val runner = AgentLoopRunner()
        listOf("Send", "Send message").forEach { target ->
            var executions = 0
            val planned = planner.chooseNextAction("send message", screen, { _, _ -> null },
                { Result.success("""[{"action":"click","target":"$target"}]""") })
            val action = planned.getOrThrow()
            assertTrue(AgentPlanPolicy.hasMessageSendTap(listOf(action)))
            assertFalse(AgentPlanPolicy.isExecutablePlan(listOf(action), "send message"))
            val payload = SendConfirmationPayload("contact-1", "Dad", "3210", "Hello Dad")
            val whatsapp = screen.copy(
                packageName = "com.whatsapp",
                visibleText = "Dad",
                interactiveElements = "[#1 editable] text=Hello Dad hint=Message class=EditText bounds=0,0,1,1"
            )
            val result = runner.run(
                AgentTaskSession("send to Dad", 0),
                nowMs = { 1 },
                observe = { whatsapp },
                plan = { _, _ -> Result.success(action) },
                execute = { executions++; true },
                resolveSendPayload = { _, _ -> payload },
                verify = { a, before, after, accepted -> ActionVerifier.verify(a, before, after, accepted, a.packageName) }
            )
            assertEquals(AgentLoopStatus.NEEDS_CONFIRMATION, result.status)
            assertEquals(0, executions)
        }
    }

    @Test fun firstVideoGoalIsYoutubeSpecificAndNeverMaps() {
        assertTrue(AgentLoopCommandPolicy.isYouTubeFirstVideoGoal("YouTube pe pehla video play karo"))
        assertFalse(AgentLoopCommandPolicy.isYouTubeFirstVideoGoal("Maps par pehla video play karo"))
        assertFalse(AgentLoopCommandPolicy.shouldUseLoop("Pune ka route Maps par dikhao"))
        assertTrue(AgentLoopCommandPolicy.shouldUseLoop("YouTube pe pehla video play karo"))
    }

    @Test fun deterministicResolverSelectsFirstVisibleYoutubeVideo() {
        val action = DeterministicUiActionResolver.resolve("YouTube pe pehla video play karo", screen)
        assertEquals(Action.CLICK, action?.action)
        assertEquals("VideoOne", action?.text)
    }

    @Test fun deterministicResolverFindsSearchButton() {
        val observation = screen.copy(interactiveElements = "[#3 clickable] text=Search bounds=0,0,10,10")
        val action = DeterministicUiActionResolver.resolve("Search button tap karo", observation)
        assertEquals("Search", action?.text)
    }
}
