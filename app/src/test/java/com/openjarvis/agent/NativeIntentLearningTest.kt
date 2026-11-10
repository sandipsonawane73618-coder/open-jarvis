package com.openjarvis.agent

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeIntentLearningTest {
    @Test
    fun successfulExecutionPersistsIntentExactlyOnce() = runTest {
        val intent = StepIntent.Torch(true)
        var executions = 0
        val learned = mutableListOf<StepIntent>()

        val result = NativeIntentLearning.executeAndLearn(
            intent,
            execute = {
                executions++
                "Torch turned on"
            },
            learn = { learned += it }
        )

        assertEquals("Torch turned on", result)
        assertEquals(1, executions)
        assertEquals(listOf(intent), learned)
    }

    @Test
    fun failedExecutionDoesNotPersistIntent() = runTest {
        val intent = StepIntent.Message("whatsapp", "Amit", "Hello")
        var learnCalls = 0

        val error = runCatching {
            NativeIntentLearning.executeAndLearn(
                intent,
                execute = { throw IllegalStateException("composer unavailable") },
                learn = { learnCalls++ }
            )
        }.exceptionOrNull()

        assertEquals("composer unavailable", error?.message)
        assertEquals(0, learnCalls)
    }

    @Test
    fun learningFailureDoesNotHideSuccessfulExecutionResult() = runTest {
        val intent = StepIntent.Timer(30)
        var executionCompleted = false

        val error = runCatching {
            NativeIntentLearning.executeAndLearn(
                intent,
                execute = {
                    executionCompleted = true
                    "Timer started"
                },
                learn = { throw IllegalStateException("storage unavailable") }
            )
        }.exceptionOrNull()

        assertTrue(executionCompleted)
        assertEquals("storage unavailable", error?.message)
    }
}
