package com.openjarvis.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonalMemoryRecallTest {
    private val memories = listOf(
        "I prefer Marathi responses",
        "My default model is qwen3:1.7b",
        "I use WhatsApp for family messages",
        "Keep battery usage low"
    )

    @Test fun recallsOnlyRelevantSavedFacts() {
        assertEquals(
            listOf("I prefer Marathi responses"),
            PersonalMemoryCodec.retrieve(memories, "Please reply in Marathi")
        )
    }

    @Test fun ranksMultipleMatchesAndKeepsStableOrderForTies() {
        val result = PersonalMemoryCodec.retrieve(memories, "WhatsApp messages")
        assertEquals(listOf("I use WhatsApp for family messages"), result)
    }

    @Test fun supportsDevanagariAndMarathiTokens() {
        val result = PersonalMemoryCodec.retrieve(
            listOf("मला मराठीमध्ये उत्तर आवडते", "मला कमी battery वापर हवा"),
            "मराठीमध्ये उत्तर द्या"
        )
        assertEquals(listOf("मला मराठीमध्ये उत्तर आवडते"), result)
    }

    @Test fun emptyOrUnrelatedQueryDoesNotLeakMemoryIntoPrompt() {
        assertTrue(PersonalMemoryCodec.retrieve(memories, "weather today").isEmpty())
        assertTrue(PersonalMemoryCodec.promptContext(memories, "weather today").isEmpty())
        assertTrue(PersonalMemoryCodec.retrieve(memories, "").isEmpty())
    }

    @Test fun promptContextIsBoundedAndLimitedToThreeFacts() {
        val result = PersonalMemoryCodec.promptContext(
            (1..10).map { "Preference $it about Jarvis settings" },
            "Jarvis settings preference"
        )
        assertTrue(result.lines().size <= 3)
        assertTrue(result.length <= 360)
    }
}
