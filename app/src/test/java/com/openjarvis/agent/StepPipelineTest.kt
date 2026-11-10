package com.openjarvis.agent

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StepPipelineTest {

    @Test
    fun twoStepsSucceedAndContextCarriesForward() = runBlocking {
        val observed = mutableListOf<StepContext>()
        val handler = object : StepHandler {
            override val name = "fake"
            override suspend fun handle(step: String, ctx: StepContext): StepOutcome? {
                observed += ctx
                return if (step == "first") {
                    StepOutcome(true, "first ok", StepContext(app = "whatsapp", contact = "Rahul", messageText = "hello"))
                } else if (step == "second") {
                    StepOutcome(true, "second ok", ctx)
                } else null
            }
        }

        val result = StepPipeline(listOf(handler)).run(listOf("first", "second"))

        assertTrue(result.allOk)
        assertEquals(2, result.traces.size)
        assertEquals("whatsapp", observed[1].app)
        assertEquals("Rahul", observed[1].contact)
        assertEquals("hello", observed[1].messageText)
    }

    @Test
    fun resumesFromPriorTurnContextAndRecordsLastSuccessfulStep() = runBlocking {
        var observed: StepContext? = null
        val handler = object : StepHandler {
            override val name = "ui"
            override suspend fun handle(step: String, ctx: StepContext): StepOutcome? {
                observed = ctx
                return StepOutcome(
                    true,
                    "group opened",
                    ctx.copy(groupName = "Pune Service")
                )
            }
        }

        val prior = StepContext(
            app = "whatsapp",
            lastCommand = "WhatsApp kholo",
            lastResult = "WhatsApp opened"
        )
        val result = StepPipeline(listOf(handler)).run(
            listOf("Pune Service group kholo"),
            prior
        )

        assertTrue(result.allOk)
        assertEquals("whatsapp", observed?.app)
        assertEquals("WhatsApp kholo", observed?.lastCommand)
        assertEquals("Pune Service", result.finalContext.groupName)
        assertEquals("Pune Service group kholo", result.finalContext.lastCommand)
        assertEquals("group opened", result.finalContext.lastResult)
        assertTrue(result.finalContext.pendingSteps.isEmpty())
    }

    @Test
    fun failedStepPreservesContextAndRemainingWorkForResume() = runBlocking {
        val handler = object : StepHandler {
            override val name = "ui"
            override suspend fun handle(step: String, ctx: StepContext): StepOutcome =
                StepOutcome(false, "search control not found", ctx.copy(app = "whatsapp"))
        }
        val result = StepPipeline(listOf(handler)).run(
            listOf("open group search", "type Pune Service", "open result"),
            StepContext(app = "whatsapp")
        )

        assertFalse(result.allOk)
        assertEquals(listOf("open group search", "type Pune Service", "open result"), result.finalContext.pendingSteps)
        assertEquals("whatsapp", result.finalContext.app)
        assertEquals("search control not found", result.finalContext.lastResult)
    }

    @Test
    fun failureStopsPipelineAndMarksRemainingStepsSkipped() = runBlocking {
        val handler = object : StepHandler {
            override val name = "fake"
            override suspend fun handle(step: String, ctx: StepContext): StepOutcome? =
                if (step == "fail") StepOutcome(false, "intent failed", ctx)
                else StepOutcome(true, "ok", ctx)
        }

        val result = StepPipeline(listOf(handler)).run(listOf("first", "fail", "third"))

        assertFalse(result.allOk)
        assertEquals(listOf("first", "fail"), result.traces.map { it.step })
        assertEquals(listOf("third"), result.skipped)
        assertEquals(listOf("fail", "third"), result.finalContext.pendingSteps)
        assertFalse(result.traces.last().ok)
    }

    @Test
    fun noHandlerProducesUnhandledTrace() = runBlocking {
        val result = StepPipeline(emptyList()).run(listOf("unknown"))

        assertFalse(result.allOk)
        assertEquals("unhandled", result.traces.single().route)
        assertEquals("Samajh nahi aaya: unknown", result.traces.single().reply)
    }

    @Test
    fun recordsSkillFastLearnedAndLlmFallbackRouteOrder() = runBlocking {
        val attempted = mutableListOf<String>()
        fun route(routeName: String, accepts: Boolean) = object : StepHandler {
            override val name = routeName
            override suspend fun handle(step: String, ctx: StepContext): StepOutcome? {
                attempted += routeName
                return if (accepts) StepOutcome(true, "$routeName handled", ctx) else null
            }
        }

        val result = StepPipeline(
            listOf(
                route("skill", false),
                route("fast", false),
                route("learned", false),
                route("llm", true)
            )
        ).run(listOf("unknown but strong-sequenced step"))

        assertEquals(listOf("skill", "fast", "learned", "llm"), attempted)
        assertEquals("llm", result.traces.single().route)
        assertTrue(result.allOk)
    }

    @Test
    fun failureMessageIncludesStepPositionAndSkippedSteps() = runBlocking {
        val handler = object : StepHandler {
            override val name = "fast"
            override suspend fun handle(step: String, ctx: StepContext): StepOutcome =
                if (step == "second") StepOutcome(false, "torch unavailable", ctx)
                else StepOutcome(true, "ok", ctx)
        }

        val result = StepPipeline(listOf(handler)).run(listOf("first", "second", "third"))

        assertEquals(
            "Step 2/3 failed: torch unavailable. Skipped steps: 3. third",
            result.failureMessage(3)
        )
    }

    @Test
    fun recognizesContinuationCommandsInEnglishHindiAndMarathi() {
        listOf("continue", "Continue karo", "aage badho", "next", "retry", "phir se karo",
            "आगे करो", "जारी रखो", "पुढे चला", "पुन्हा करा")
            .forEach { assertTrue("$it should resume pending work", CommandRouter.isContinueCommand(it)) }
        listOf("WhatsApp kholo", "Pune Service group kholo", "Hi type karo", "time kya hua")
            .forEach { assertFalse("$it is not a continuation command", CommandRouter.isContinueCommand(it)) }
    }

    @Test
    fun successfulRunHasNoFailureMessage() = runBlocking {
        val handler = object : StepHandler {
            override val name = "skill"
            override suspend fun handle(step: String, ctx: StepContext): StepOutcome =
                StepOutcome(true, "ok", ctx)
        }
        val result = StepPipeline(listOf(handler)).run(listOf("one", "two"))
        assertEquals(null, result.failureMessage(2))
    }

    @Test
    fun firstMatchingHandlerWinsAndOrderIsRespected() = runBlocking {
        val called = mutableListOf<String>()
        fun handler(id: String, accepts: Boolean) = object : StepHandler {
            override val name = id
            override suspend fun handle(step: String, ctx: StepContext): StepOutcome? {
                called += id
                return if (accepts) StepOutcome(true, id, ctx) else null
            }
        }

        val result = StepPipeline(listOf(handler("first", false), handler("second", true), handler("third", true)))
            .run(listOf("command"))

        assertTrue(result.allOk)
        assertEquals(listOf("first", "second"), called)
        assertEquals("second", result.traces.single().route)
    }
}
