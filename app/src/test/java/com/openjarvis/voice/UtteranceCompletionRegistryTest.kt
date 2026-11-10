package com.openjarvis.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UtteranceCompletionRegistryTest {
    @Test fun finishingOldUtteranceCannotConsumeNewUtteranceCallback() {
        val registry = UtteranceCompletionRegistry()
        var oldCompleted = 0
        var newCompleted = 0

        registry.register("old") { oldCompleted++ }
        registry.register("new") { newCompleted++ }

        registry.take("old")?.invoke()

        assertEquals(1, oldCompleted)
        assertEquals(0, newCompleted)
        assertNull(registry.take("old"))
        registry.take("new")?.invoke()
        assertEquals(1, newCompleted)
    }

    @Test fun drainCompletesEveryPendingCallbackExactlyOnce() {
        val registry = UtteranceCompletionRegistry()
        var first = 0
        var second = 0
        registry.register("first") { first++ }
        registry.register("second") { second++ }

        registry.drain().forEach { it() }

        assertEquals(1, first)
        assertEquals(1, second)
        assertEquals(0, registry.drain().size)
    }

    @Test fun clearDiscardsCallbacksWhenEngineIsReleased() {
        val registry = UtteranceCompletionRegistry()
        var completed = 0
        registry.register("active") { completed++ }

        registry.clear()

        assertNull(registry.take("active"))
        assertEquals(0, completed)
    }
}
