package com.openjarvis.graphify

import com.openjarvis.graphify.nodes.PatternNode
import com.openjarvis.graphify.nodes.TaskNode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryContextBuilderTest {
    private val now = 1_800_000_000_000L

    @Test
    fun returnsOnlyRelevantSuccessfulTaskCategories() {
        val tasks = listOf(
            TaskNode(command = "Open YouTube", result = "success", timestamp = now - 1_000L),
            TaskNode(command = "Set a timer", result = "success", timestamp = now - 2_000L),
            TaskNode(command = "Open Settings", result = "failed: not found", timestamp = now - 3_000L),
            TaskNode(command = "Open Instagram", result = "success", timestamp = now - 4_000L)
        )

        val result = MemoryContextBuilder.build("YouTube kholo", tasks, emptyList(), now)

        assertTrue(result.contains("video search"))
        assertFalse(result.contains("timer"))
        assertFalse(result.contains("Instagram"))
        assertFalse(result.contains("Open YouTube"))
    }

    @Test
    fun excludesContactAndMessageHistoryAndSensitiveQueries() {
        val tasks = listOf(
            TaskNode(
                command = "WhatsApp message Amit phone 9876543210",
                result = "success",
                timestamp = now - 1_000L
            )
        )

        assertTrue(
            MemoryContextBuilder.build("Open WhatsApp", tasks, emptyList(), now).isEmpty()
        )
        assertTrue(
            MemoryContextBuilder.build("WhatsApp message Amit", tasks, emptyList(), now).isEmpty()
        )
    }

    @Test
    fun ignoresExpiredTaskHistory() {
        val tasks = listOf(
            TaskNode(
                command = "Open YouTube",
                result = "success",
                timestamp = now - 31L * 24L * 60L * 60L * 1000L
            )
        )

        assertTrue(
            MemoryContextBuilder.build("YouTube search", tasks, emptyList(), now).isEmpty()
        )
    }

    @Test
    fun includesOnlyRecentConfidentRelevantWorkflowCategories() {
        val patterns = listOf(
            PatternNode(
                sequenceHash = "a",
                sequenceString = "open settings then wifi",
                confidence = 0.85f,
                lastSeen = now - 1_000L
            ),
            PatternNode(
                sequenceHash = "b",
                sequenceString = "open settings then bluetooth",
                confidence = 0.45f,
                lastSeen = now - 1_000L
            )
        )

        val result = MemoryContextBuilder.build("Open settings and wifi", emptyList(), patterns, now)

        assertTrue(result.contains("device settings"))
        assertTrue(result.contains("wifi"))
        assertFalse(result.contains("bluetooth"))
    }

    @Test
    fun unrelatedAndUnclassifiedCommandsReceiveNoMemory() {
        val tasks = listOf(
            TaskNode(command = "Open YouTube", result = "success", timestamp = now - 1_000L)
        )

        assertTrue(
            MemoryContextBuilder.build("Tell me a joke", tasks, emptyList(), now).isEmpty()
        )
    }

    @Test
    fun outputIsBounded() {
        val tasks = (1..30).map {
            TaskNode(
                command = "Open YouTube search browser settings timer alarm maps camera",
                result = "success",
                timestamp = now - it * 1_000L
            )
        }

        val result = MemoryContextBuilder.build(
            "Open YouTube search browser settings timer alarm maps camera",
            tasks,
            emptyList(),
            now
        )

        assertTrue(result.length <= MemoryContextBuilder.MAX_CONTEXT_CHARS)
    }
}
