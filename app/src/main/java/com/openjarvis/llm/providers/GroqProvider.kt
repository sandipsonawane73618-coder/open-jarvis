package com.openjarvis.llm.providers

import com.openjarvis.llm.ConnectionResult
import com.openjarvis.llm.HybridChatHttpClient
import com.openjarvis.llm.LLMProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject

class GroqProvider(
    private val apiKey: String,
    private val model: String = "openai/gpt-oss-120b",
    private val httpClient: OkHttpClient = HybridChatHttpClient.client
) : LLMProvider {

    override val name: String = "Groq"
    override val baseUrl: String = "https://api.groq.com/openai/v1"

    override suspend fun complete(systemPrompt: String, userMessage: String): Result<String> =
        request(systemPrompt, userMessage, jsonMode = false)

    override suspend fun completeJson(systemPrompt: String, userMessage: String): Result<String> =
        request(systemPrompt, userMessage, jsonMode = true)

    private suspend fun request(
        systemPrompt: String,
        userMessage: String,
        jsonMode: Boolean
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val requestBody = JSONObject().apply {
                put("model", model)
                put("messages", JSONArray().apply {
                    put(JSONObject().apply { put("role", "system"); put("content", systemPrompt) })
                    put(JSONObject().apply { put("role", "user"); put("content", userMessage) })
                })
                put("max_completion_tokens", 1024)
                put("temperature", 0.1)
                put("reasoning_effort", "low")
                put("reasoning_format", "hidden")
                put("stream", false)
                if (jsonMode) put("response_format", JSONObject().put("type", "json_object"))
            }.toString()
            val request = Request.Builder()
                .url("$baseUrl/chat/completions")
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(requestBody.toRequestBody("application/json".toMediaType()))
                .build()

            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: throw IllegalStateException("Groq returned an empty response")
                val json = runCatching { JSONObject(body) }.getOrNull()
                val errorText = json?.optJSONObject("error")?.optString("message").orEmpty()
                if (!response.isSuccessful) {
                    throw IllegalStateException(errorText.ifBlank { "Groq request failed (HTTP ${response.code})" })
                }
                json?.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")
                    ?.optString("content")?.takeIf { it.isNotBlank() }
                    ?: throw IllegalStateException(errorText.ifBlank { "Groq returned an empty response" })
            }
        }
    }

    override suspend fun testConnection(): Result<Long> = withContext(Dispatchers.IO) {
        runCatching {
            val startTime = System.currentTimeMillis()
            val request = Request.Builder()
                .url("$baseUrl/models")
                .addHeader("Authorization", "Bearer $apiKey")
                .get()
                .build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code}")
            }
            System.currentTimeMillis() - startTime
        }
    }
}
