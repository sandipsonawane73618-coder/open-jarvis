package com.openjarvis.llm

import com.openjarvis.llm.providers.GeminiProvider
import com.openjarvis.llm.providers.GroqProvider
import kotlinx.coroutines.CancellationException

/** Routes ordinary conversation only; action planning and skills remain on local paths. */
internal class HybridChatRouter(
    private val settings: () -> HybridChatSettings,
    private val apiKey: (HybridChatProvider) -> String,
    private val cloudProvider: (HybridChatProvider, String, String) -> LLMProvider = { provider, key, model ->
        when (provider) {
            HybridChatProvider.GEMINI -> GeminiProvider(key, model, HybridChatHttpClient.client)
            HybridChatProvider.GROQ -> GroqProvider(key, model, HybridChatHttpClient.client)
        }
    },
    private val localProvider: () -> LLMProvider,
    private val cloudTimeoutMs: Long = DEFAULT_CLOUD_TIMEOUT_MS
) {
    suspend fun complete(cloudSystemPrompt: String, localSystemPrompt: String, userMessage: String, timeoutMs: Long): Result<String> {
        if (timeoutMs <= 0L) return timeoutFailure()
        val config = settings()
        if (!config.onlineChatEnabled) return localComplete(localSystemPrompt, userMessage, timeoutMs)

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
                cloudProvider(providerType, key, model).completeWithDeadline(
                    cloudSystemPrompt, userMessage, minOf(remaining, cloudTimeoutMs), conversation = true
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                Result.failure(IllegalStateException("Cloud provider request failed"))
            }
            val answer = attempt.getOrNull()
            if (!answer.isNullOrBlank()) return Result.success(answer)
        }

        val remaining = remainingMs(startedAt, timeoutMs)
        if (config.localFallbackEnabled && remaining > 0L) {
            return localComplete(localSystemPrompt, userMessage, remaining)
        }
        return Result.failure(IllegalStateException("Online chat is unavailable. Check provider settings or enable local fallback."))
    }

    private suspend fun localComplete(system: String, user: String, timeoutMs: Long): Result<String> {
        if (timeoutMs <= 0L) return timeoutFailure()
        return try {
            localProvider().completeWithDeadline(system, user, timeoutMs, conversation = true)
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

/** No body/header logging and no automatic retry for requests containing user chat text. */
internal object HybridChatHttpClient {
    val client: okhttp3.OkHttpClient by lazy {
        okhttp3.OkHttpClient.Builder()
            .connectTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
            .callTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .build()
    }
}
