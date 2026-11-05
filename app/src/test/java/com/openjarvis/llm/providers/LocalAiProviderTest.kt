package com.openjarvis.llm.providers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalAiProviderTest {
    @Test fun normalizesVersionedBaseUrlWithoutDuplicatingV1() {
        assertEquals("http://127.0.0.1:8080/v1", LocalAiProvider.normalizeBaseUrl(" http://127.0.0.1:8080/v1/ "))
    }

    @Test fun acceptsServerRoot() {
        assertEquals("http://192.168.1.5:8080", LocalAiProvider.normalizeBaseUrl("http://192.168.1.5:8080/"))
    }

    @Test fun rejectsMissingScheme() {
        val result = runCatching { LocalAiProvider.normalizeBaseUrl("127.0.0.1:8080") }
        assertTrue(result.isFailure)
    }
}
