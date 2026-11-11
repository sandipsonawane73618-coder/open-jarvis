package com.openjarvis.agent

import org.junit.Assert.*
import org.junit.Test

class LocalOllamaHealthPolicyTest {
    @Test fun exactDefaultModelIsReady() {
        val result = LocalOllamaHealthPolicy.evaluate(200, listOf("qwen3:1.7b"))
        assertEquals(LocalOllamaHealthPolicy.State.READY, result.state)
    }

    @Test fun missingDefaultModelDoesNotSuggestChangingDefault() {
        val result = LocalOllamaHealthPolicy.evaluate(200, listOf("qwen2.5:1.5b"))
        assertEquals(LocalOllamaHealthPolicy.State.MODEL_MISSING, result.state)
        assertTrue(result.detail.contains("qwen3:1.7b"))
        assertTrue(result.detail.contains("unchanged"))
    }

    @Test fun httpFailureIsReportedWithoutFallback() {
        val result = LocalOllamaHealthPolicy.evaluate(503, emptyList())
        assertEquals(LocalOllamaHealthPolicy.State.HTTP_ERROR, result.state)
    }

    @Test fun connectionFailureHasTermuxGuidance() {
        val result = LocalOllamaHealthPolicy.unavailable("ConnectException")
        assertEquals(LocalOllamaHealthPolicy.State.SERVER_UNAVAILABLE, result.state)
        assertTrue(result.detail.contains("Termux"))
    }
}
