package com.openjarvis.agent

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationIntentPolicyTest {
    @Test fun routesGeneralQuestionsToChat() {
        listOf(
            "India ka capital kya hai?",
            "India ka capital kya hai",
            "Aaj ka din kaisa hai?",
            "Mujhe ek joke sunao",
            "Ollama kya hai?",
            "Java aur Kotlin mein difference?",
            "What is Android?",
            "काय चाललंय?"
        ).forEach { assertTrue("$it should use chat", ConversationIntentPolicy.isChatRequest(it)) }
    }

    @Test fun leavesPhoneActionsForActionPipeline() {
        listOf(
            "YouTube kholo",
            "Open WhatsApp",
            "click Search",
            "WhatsApp mein message likho",
            "go back",
            "mute ho jao"
        ).forEach { assertFalse("$it should use action pipeline", ConversationIntentPolicy.isChatRequest(it)) }
    }

    @Test fun rejectsEmptyAndLongInputs() {
        assertFalse(ConversationIntentPolicy.isChatRequest(" "))
        assertFalse(ConversationIntentPolicy.isChatRequest("x".repeat(1201)))
    }
}
