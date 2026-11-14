package com.openjarvis.llm

interface LLMProvider {
    val name: String
    val baseUrl: String
    
    suspend fun complete(
        systemPrompt: String,
        userMessage: String
    ): Result<String>
    
    /** Request a provider-native JSON response when supported; local providers may use strict prompt mode. */
    suspend fun completeJson(systemPrompt: String, userMessage: String): Result<String> = complete(systemPrompt, userMessage)

    suspend fun completeJsonWithDeadline(systemPrompt: String, userMessage: String, timeoutMs: Long): Result<String> {
        if (timeoutMs <= 0L) return Result.failure(IllegalStateException("LLM time budget exceeded."))
        return kotlinx.coroutines.withTimeoutOrNull(timeoutMs) { completeJson(systemPrompt, userMessage) }
            ?: Result.failure(IllegalStateException("LLM time budget exceeded."))
    }

    suspend fun completeConversation(systemPrompt: String, userMessage: String): Result<String> = complete(systemPrompt, userMessage)

    /** Emits decoded text deltas as they arrive; implementations may fall back to one final chunk. */
    suspend fun completeConversationStreaming(
        systemPrompt: String,
        userMessage: String,
        onChunk: (String) -> Unit
    ): Result<String> = completeConversation(systemPrompt, userMessage).onSuccess(onChunk)

    suspend fun completeWithDeadline(
        systemPrompt: String,
        userMessage: String,
        timeoutMs: Long,
        conversation: Boolean = false
    ): Result<String> {
        if (timeoutMs <= 0L) return Result.failure(IllegalStateException("LLM time budget exceeded (45 seconds)."))
        return kotlinx.coroutines.withTimeoutOrNull(timeoutMs) {
            if (conversation) completeConversation(systemPrompt, userMessage) else complete(systemPrompt, userMessage)
        } ?: Result.failure(IllegalStateException("LLM time budget exceeded (45 seconds)."))
    }

    suspend fun completeSkillWithDeadline(
        systemPrompt: String,
        userMessage: String,
        timeoutMs: Long
    ): Result<String> = completeWithDeadline(systemPrompt, userMessage, timeoutMs, conversation = false)

    suspend fun testConnection(): Result<Long>
}

sealed class ConnectionResult {
    data class Success(val latencyMs: Long) : ConnectionResult()
    data class Error(val message: String) : ConnectionResult()
}