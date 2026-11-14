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

class GeminiProvider(
    private val apiKey: String,
    private val model: String = "gemini-2.5-flash",
    private val httpClient: OkHttpClient = HybridChatHttpClient.client
) : LLMProvider {

    override val name: String = "Google Gemini"
    override val baseUrl: String = "https://generativelanguage.googleapis.com/v1beta"

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
            val fullPrompt = "$systemPrompt\n\nUser: $userMessage"
            val generationConfig = JSONObject().apply {
                put("temperature", 0.1)
                put("maxOutputTokens", 1024)
                if (jsonMode) put("responseMimeType", "application/json")
            }
            val requestBody = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply { put("text", fullPrompt) })
                        })
                    })
                })
                put("generationConfig", generationConfig)
            }.toString()
            val request = Request.Builder()
                .url("$baseUrl/models/$model:generateContent?key=$apiKey")
                .addHeader("Content-Type", "application/json")
                .post(requestBody.toRequestBody("application/json".toMediaType()))
                .build()

            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: throw IllegalStateException("Gemini returned an empty response")
                val json = runCatching { JSONObject(body) }.getOrNull()
                val errorText = json?.optJSONObject("error")?.optString("message").orEmpty()
                if (response.code == 404 || errorText.contains("model", true) && errorText.contains("not found", true)) {
                    throw IllegalStateException("Gemini model band ho gaya, Settings me model badlo")
                }
                if (!response.isSuccessful) {
                    throw IllegalStateException(errorText.ifBlank { "Gemini request failed (HTTP ${response.code})" })
                }
                val candidates = json?.optJSONArray("candidates")
                    ?: throw IllegalStateException(errorText.ifBlank { "Gemini returned no candidate" })
                candidates.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")
                    ?.optJSONObject(0)?.optString("text")
                    ?.takeIf { it.isNotBlank() }
                    ?: throw IllegalStateException("Gemini returned an empty candidate")
            }
        }
    }

    override suspend fun testConnection(): Result<Long> = withContext(Dispatchers.IO) {
        runCatching {
            val startTime = System.currentTimeMillis()
            val request = Request.Builder()
                .url("$baseUrl/models?key=$apiKey")
                .get()
                .build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code}")
            }
            System.currentTimeMillis() - startTime
        }
    }
}
