package com.openjarvis.llm

import com.openjarvis.llm.providers.GeminiProvider
import com.openjarvis.llm.providers.GroqProvider
import kotlinx.coroutines.CancellationException

/** Routes ordinary chat and strict JSON command plans through the same configured provider chain. */
internal class HybridChatRouter(
    private val settings: () -> HybridChatSettings,
    private val apiKey: (HybridChatProvider) -> String,
    private val cloudProvider: (HybridChatProvider, String, String) -> LLMProvider = { provider, key, model ->
        when (provider) {
            HybridChatProvider.GEMINI -> GeminiProvider(key, model)
            HybridChatProvider.GROQ -> GroqProvider(key, model)
        }
    },
    private val localProvider: () -> LLMProvider,
    private val cloudTimeoutMs: Long = DEFAULT_CLOUD_TIMEOUT_MS
) {
    @Volatile private var providerNotice: String? = null

    fun consumeProviderNotice(): String? = providerNotice.also { providerNotice = null }

    suspend fun complete(
        cloudSystemPrompt: String,
        localSystemPrompt: String,
        userMessage: String,
        timeoutMs: Long
    ): Result<String> = completeInternal(cloudSystemPrompt, localSystemPrompt, userMessage, timeoutMs, jsonMode = false)

    suspend fun completeJson(
        cloudSystemPrompt: String,
        localSystemPrompt: String,
        userMessage: String,
        timeoutMs: Long
    ): Result<String> = completeInternal(cloudSystemPrompt, localSystemPrompt, userMessage, timeoutMs, jsonMode = true)

    private suspend fun completeInternal(
        cloudSystemPrompt: String,
        localSystemPrompt: String,
        userMessage: String,
        timeoutMs: Long,
        jsonMode: Boolean
    ): Result<String> {
        providerNotice = null
        if (timeoutMs <= 0L) return timeoutFailure()
        val config = settings()
        if (!config.onlineChatEnabled) return localComplete(localSystemPrompt, userMessage, timeoutMs, jsonMode)

        val startedAt = System.nanoTime()
        for (providerType in listOf(config.provider, config.provider.other())) {
            val key = apiKey(providerType).trim()
            if (key.isBlank()) continue
            val remaining = remainingMs(startedAt, timeoutMs)
            if (remaining <= 0L) break
            val model = if (providerType == config.provider) {
                HybridChatSettingsPolicy.normalizeModel(providerType, config.model)
            } else providerType.defaultModel
            val attempt = try {
                val safeCloudSystem = com.openjarvis.agent.CloudPromptRedactionPolicy.redact(cloudSystemPrompt)
                val safeUserMessage = com.openjarvis.agent.CloudPromptRedactionPolicy.redact(userMessage)
                val provider = cloudProvider(providerType, key, model)
                if (jsonMode) provider.completeJsonWithDeadline(
                    safeCloudSystem, safeUserMessage, minOf(remaining, cloudTimeoutMs)
                ) else provider.completeWithDeadline(
                    safeCloudSystem, safeUserMessage, minOf(remaining, cloudTimeoutMs), conversation = true
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Result.failure(error)
            }
            val answer = attempt.getOrNull()
            if (!answer.isNullOrBlank()) return Result.success(answer)
            val message = attempt.exceptionOrNull()?.message.orEmpty()
            if (providerType == HybridChatProvider.GEMINI &&
                (message.contains("Gemini model band ho gaya", true) ||
                    message.contains("model not found", true) ||
                    message.contains("HTTP 404", true))
            ) {
                providerNotice = "Gemini model band ho gaya, Settings me model badlo"
            }
        }

        val remaining = remainingMs(startedAt, timeoutMs)
        if (config.localFallbackEnabled && remaining > 0L) {
            val local = localComplete(localSystemPrompt, userMessage, remaining, jsonMode)
            if (local.isSuccess) return local
            val notice = providerNotice
            if (notice != null) {
                return Result.failure(IllegalStateException("$notice. Groq aur local Ollama bhi unavailable hain."))
            }
            return local
        }
        val notice = providerNotice
        return Result.failure(IllegalStateException(
            notice ?: "Online chat is unavailable. Check provider settings or enable local fallback."
        ))
    }

    private suspend fun localComplete(
        system: String,
        user: String,
        timeoutMs: Long,
        jsonMode: Boolean
    ): Result<String> {
        if (timeoutMs <= 0L) return timeoutFailure()
        return try {
            val provider = localProvider()
            if (jsonMode) provider.completeJsonWithDeadline(system, user, timeoutMs)
            else provider.completeWithDeadline(system, user, timeoutMs, conversation = true)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            Result.failure(IllegalStateException("Local chat provider failed"))
        }
    }

    private fun remainingMs(startedAtNanos: Long, budgetMs: Long): Long =
        (budgetMs - (System.nanoTime() - startedAtNanos) / 1_000_000L).coerceAtLeast(0L)

    private fun timeoutFailure(): Result<String> =
        Result.failure(IllegalStateException("LLM time budget exceeded."))

    companion object { const val DEFAULT_CLOUD_TIMEOUT_MS = 8_000L }
}
