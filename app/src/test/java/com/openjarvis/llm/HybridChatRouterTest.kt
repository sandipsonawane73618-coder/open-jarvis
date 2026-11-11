package com.openjarvis.llm

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HybridChatRouterTest {
    private class FakeProvider(
        override val name: String,
        private val response: Result<String>,
        private val seen: ((String, String) -> Unit)? = null
    ) : LLMProvider {
        override val baseUrl = "https://test.invalid"
        override suspend fun complete(systemPrompt: String, userMessage: String): Result<String> {
            seen?.invoke(systemPrompt, userMessage)
            return response
        }
        override suspend fun testConnection(): Result<Long> = Result.success(1L)
    }

    @Test fun cloudDisabledUsesLocalProvider() = runBlocking {
        var localCalled = false
        var cloudCalled = false
        val router = HybridChatRouter(
            settings = { HybridChatSettings(onlineChatEnabled = false) },
            apiKey = { "key" },
            cloudProvider = { _, _, _ -> cloudCalled = true; FakeProvider("cloud", Result.success("cloud")) },
            localProvider = { localCalled = true; FakeProvider("ollama", Result.success("local")) }
        )
        assertEquals("local", router.complete("cloud-safe", "local-private", "hello", 5_000).getOrThrow())
        assertTrue(localCalled)
        assertFalse(cloudCalled)
    }

    @Test fun cloudReceivesOnlyCloudSafePromptAndCurrentUserTurn() = runBlocking {
        var observedSystem = ""
        var observedUser = ""
        val router = HybridChatRouter(
            settings = { HybridChatSettings(onlineChatEnabled = true, provider = HybridChatProvider.GEMINI) },
            apiKey = { if (it == HybridChatProvider.GEMINI) "gem-key" else "" },
            cloudProvider = { provider, _, _ ->
                assertEquals(HybridChatProvider.GEMINI, provider)
                FakeProvider("gemini", Result.success("cloud answer")) { system, user ->
                    observedSystem = system; observedUser = user
                }
            },
            localProvider = { error("Local fallback must not run") }
        )
        assertEquals("cloud answer", router.complete("cloud-safe-only", "contains-private-memory", "my question", 5_000).getOrThrow())
        assertEquals("cloud-safe-only", observedSystem)
        assertEquals("my question", observedUser)
        assertFalse(observedSystem.contains("private-memory"))
    }

    @Test fun failedPrimaryTriesSecondaryBeforeLocal() = runBlocking {
        val attempts = mutableListOf<HybridChatProvider>()
        var localCalled = false
        val router = HybridChatRouter(
            settings = { HybridChatSettings(onlineChatEnabled = true, provider = HybridChatProvider.GEMINI) },
            apiKey = { "key-" + it.storageKey },
            cloudProvider = { provider, _, _ ->
                attempts += provider
                if (provider == HybridChatProvider.GEMINI) FakeProvider("gemini", Result.failure(Exception("offline")))
                else FakeProvider("groq", Result.success("groq answer"))
            },
            localProvider = { localCalled = true; FakeProvider("ollama", Result.success("local")) }
        )
        assertEquals("groq answer", router.complete("cloud", "local", "hello", 5_000).getOrThrow())
        assertEquals(listOf(HybridChatProvider.GEMINI, HybridChatProvider.GROQ), attempts)
        assertFalse(localCalled)
    }

    @Test fun cloudFailureFallsBackWithFullLocalPrompt() = runBlocking {
        var localSystem = ""
        val router = HybridChatRouter(
            settings = { HybridChatSettings(onlineChatEnabled = true, localFallbackEnabled = true) },
            apiKey = { "key" },
            cloudProvider = { provider, _, _ -> FakeProvider(provider.name, Result.failure(Exception("timeout"))) },
            localProvider = { FakeProvider("ollama", Result.success("local answer")) { system, _ -> localSystem = system } }
        )
        assertEquals("local answer", router.complete("cloud-safe", "local-full-context", "hello", 5_000).getOrThrow())
        assertEquals("local-full-context", localSystem)
    }

    @Test fun missingKeysUsesLocalAndDisabledFallbackFails() = runBlocking {
        var localCalls = 0
        val noKeys = HybridChatRouter(
            settings = { HybridChatSettings(onlineChatEnabled = true, localFallbackEnabled = true) },
            apiKey = { "" },
            localProvider = { localCalls++; FakeProvider("ollama", Result.success("local")) }
        )
        assertEquals("local", noKeys.complete("cloud", "local", "hello", 5_000).getOrThrow())
        val noFallback = HybridChatRouter(
            settings = { HybridChatSettings(onlineChatEnabled = true, localFallbackEnabled = false) },
            apiKey = { "" },
            localProvider = { error("Fallback disabled") }
        )
        assertTrue(noFallback.complete("cloud", "local", "hello", 5_000).isFailure)
        assertEquals(1, localCalls)
    }
}
