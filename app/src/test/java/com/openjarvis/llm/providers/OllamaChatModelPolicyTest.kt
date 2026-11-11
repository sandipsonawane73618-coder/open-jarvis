package com.openjarvis.llm.providers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OllamaChatModelPolicyTest {
    @Test
    fun defaultRemainsQwen17B() {
        assertEquals("qwen3:1.7b", OllamaProvider.DEFAULT_MODEL)
        assertEquals(OllamaProvider.DEFAULT_MODEL, OllamaProvider.supportedChatModel(null))
    }

    @Test
    fun qwen4BIsAvailableForChat() {
        assertTrue("qwen3:4b" in OllamaProvider.AVAILABLE_MODELS)
        assertEquals("qwen3:4b", OllamaProvider.supportedChatModel("qwen3:4b"))
    }

    @Test
    fun unsupportedModelFallsBackToDefault() {
        assertEquals(OllamaProvider.DEFAULT_MODEL, OllamaProvider.supportedChatModel("qwen3:8b"))
    }
}
