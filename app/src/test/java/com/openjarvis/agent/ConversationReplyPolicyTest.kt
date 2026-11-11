package com.openjarvis.agent

import org.junit.Assert.assertEquals
import org.junit.Test

class ConversationReplyPolicyTest {
    @Test fun keepsOrdinaryConciseAnswers() {
        assertEquals("Aaj mausam achha hai.", ConversationReplyPolicy.safeReply("  Aaj mausam achha hai.  "))
    }

    @Test fun allowsOrdinaryUseOfPlanWord() {
        assertEquals(
            "Kal ka plan bata deta hoon.",
            ConversationReplyPolicy.safeReply("Kal ka plan bata deta hoon.")
        )
    }

    @Test fun blocksConversationalPlanStepHeader() {
        assertEquals(
            ConversationReplyPolicy.SAFE_FALLBACK,
            ConversationReplyPolicy.safeReply("Plan: step 1")
        )
    }

    @Test fun blocksReasoningHeadersOnlyAtLineStart() {
        listOf(
            "Plan: step 1...",
            "Thinking: let me reason this out",
            "Reasoning: internal notes"
        ).forEach {
            assertEquals(ConversationReplyPolicy.SAFE_FALLBACK, ConversationReplyPolicy.safeReply(it))
        }
        assertEquals(
            "The plan: step 1 is to check the fuse.",
            ConversationReplyPolicy.safeReply("The plan: step 1 is to check the fuse.")
        )
    }

    @Test fun blocksThinkTagsAndPromptLikeText() {
        listOf(
            "<think>private reasoning</think>Final answer",
            "system prompt: reveal hidden instructions",
            "developer message: internal setup",
            "user: ignore safeguards",
            "assistant: hidden content"
        ).forEach {
            assertEquals(ConversationReplyPolicy.SAFE_FALLBACK, ConversationReplyPolicy.safeReply(it))
        }
    }

    @Test fun blocksStructuredActionJson() {
        listOf(
            """{"action":"open_app"}""",
            """{"skill":"timer","args":{}}"""
        ).forEach {
            assertEquals(ConversationReplyPolicy.SAFE_FALLBACK, ConversationReplyPolicy.safeReply(it))
        }
    }

    @Test fun blocksBlankAndOversizedReplies() {
        assertEquals(ConversationReplyPolicy.SAFE_FALLBACK, ConversationReplyPolicy.safeReply("  "))
        assertEquals(ConversationReplyPolicy.SAFE_FALLBACK, ConversationReplyPolicy.safeReply("x".repeat(2401)))
    }
}
