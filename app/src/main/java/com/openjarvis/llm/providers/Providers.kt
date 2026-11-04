package com.openjarvis.llm.providers

import com.openjarvis.llm.ConnectionResult
import com.openjarvis.llm.HttpClient
import com.openjarvis.llm.LLMProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

class OpenRouterProvider(
    private val apiKey: String,
    private val model: String = "meta-llama/llama-3-8b-instruct:free"
) : LLMProvider {
    
    override val name: String = "OpenRouter"
    override val baseUrl: String = "https://openrouter.ai/api/v1"
    
    override suspend fun complete(
        systemPrompt: String,
        userMessage: String
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val requestBody = JSONObject().apply {
                put("model", model)
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "system")
                        put("content", systemPrompt)
                    })
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", userMessage)
                    })
                })
                put("max_tokens", 1024)
                put("temperature", 0.1)
            }.toString()
            
            val request = Request.Builder()
                .url("$baseUrl/chat/completions")
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .addHeader("HTTP-Referer", "https://github.com/tokenarc/open-jarvis")
                .addHeader("X-Title", "Open Jarvis")
                .post(requestBody.toRequestBody("application/json".toMediaType()))
                .build()
            
            HttpClient.client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: throw Exception("Empty response")
                val json = JSONObject(body)
                if (json.has("choices")) {
                    json.getJSONArray("choices")
                        .getJSONObject(0)
                        .getJSONObject("message")
                        .getString("content")
                } else if (json.has("error")) {
                    throw Exception(json.getJSONObject("error").getString("message"))
                } else {
                    throw Exception("Unknown response: $body")
                }
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
            
            HttpClient.client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw Exception("HTTP ${response.code}")
                }
            }
            System.currentTimeMillis() - startTime
        }
    }
}

class AnthropicProvider(
    private val apiKey: String,
    private val model: String = "claude-haiku-4-20250514"
) : LLMProvider {
    
    override val name: String = "Anthropic Claude"
    override val baseUrl: String = "https://api.anthropic.com/v1"
    
    override suspend fun complete(
        systemPrompt: String,
        userMessage: String
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val requestBody = JSONObject().apply {
                put("model", model)
                put("max_tokens", 1024)
                put("system", systemPrompt)
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", userMessage)
                    })
                })
            }.toString()
            
            val request = Request.Builder()
                .url("$baseUrl/messages")
                .addHeader("x-api-key", apiKey)
                .addHeader("anthropic-version", "2023-06-01")
                .addHeader("Content-Type", "application/json")
                .post(requestBody.toRequestBody("application/json".toMediaType()))
                .build()
            
            HttpClient.client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: throw Exception("Empty response")
                val json = JSONObject(body)
                if (json.has("content")) {
                    json.getJSONArray("content")
                        .getJSONObject(0)
                        .getString("text")
                } else if (json.has("error")) {
                    throw Exception(json.getJSONObject("error").getString("message"))
                } else {
                    throw Exception("Unknown response: $body")
                }
            }
        }
    }
    
    override suspend fun testConnection(): Result<Long> = withContext(Dispatchers.IO) {
        runCatching {
            val startTime = System.currentTimeMillis()
            val requestBody = JSONObject().apply {
                put("model", model)
                put("max_tokens", 1)
                put("messages", JSONArray().put(
                    JSONObject().put("role", "user").put("content", "ping")
                ))
            }.toString()
            
            val request = Request.Builder()
                .url("$baseUrl/messages")
                .addHeader("x-api-key", apiKey)
                .addHeader("anthropic-version", "2023-06-01")
                .addHeader("Content-Type", "application/json")
                .post(requestBody.toRequestBody("application/json".toMediaType()))
                .build()
            
            HttpClient.client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw Exception("HTTP ${response.code}")
                }
            }
            System.currentTimeMillis() - startTime
        }
    }
}

class OpenAIProvider(
    private val apiKey: String,
    private val model: String = "gpt-4o-mini"
) : LLMProvider {
    
    override val name: String = "OpenAI"
    override val baseUrl: String = "https://api.openai.com/v1"
    
    override suspend fun complete(
        systemPrompt: String,
        userMessage: String
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val requestBody = JSONObject().apply {
                put("model", model)
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "system")
                        put("content", systemPrompt)
                    })
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", userMessage)
                    })
                })
                put("max_tokens", 1024)
                put("temperature", 0.1)
            }.toString()
            
            val request = Request.Builder()
                .url("$baseUrl/chat/completions")
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(requestBody.toRequestBody("application/json".toMediaType()))
                .build()
            
            HttpClient.client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: throw Exception("Empty response")
                val json = JSONObject(body)
                if (json.has("choices")) {
                    json.getJSONArray("choices")
                        .getJSONObject(0)
                        .getJSONObject("message")
                        .getString("content")
                } else if (json.has("error")) {
                    throw Exception(json.getJSONObject("error").getString("message"))
                } else {
                    throw Exception("Unknown response: $body")
                }
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
            
            HttpClient.client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw Exception("HTTP ${response.code}")
                }
            }
            System.currentTimeMillis() - startTime
        }
    }
}

class OllamaProvider(
    override val baseUrl: String,
    private val model: String = DEFAULT_MODEL,
    private val appContext: android.content.Context? = null
) : LLMProvider {

    private val client = okhttp3.OkHttpClient.Builder()
        .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(180, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
    private val versionClient = okhttp3.OkHttpClient.Builder()
        .connectTimeout(2, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(2, java.util.concurrent.TimeUnit.SECONDS)
        .callTimeout(3, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    override val name: String = "Ollama"

    override suspend fun complete(systemPrompt: String, userMessage: String): Result<String> =
        completeInternal(systemPrompt, userMessage, actionPlan = true, timeoutMs = 120_000L)

    override suspend fun completeConversation(systemPrompt: String, userMessage: String): Result<String> =
        completeInternal(systemPrompt, userMessage, actionPlan = false, timeoutMs = 120_000L)

    override suspend fun completeWithDeadline(
        systemPrompt: String,
        userMessage: String,
        timeoutMs: Long,
        conversation: Boolean
    ): Result<String> = completeInternal(systemPrompt, userMessage, actionPlan = !conversation, timeoutMs = timeoutMs)

    private suspend fun completeInternal(
        systemPrompt: String,
        userMessage: String,
        actionPlan: Boolean,
        timeoutMs: Long
    ): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val deadlineNanos = System.nanoTime() + java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(timeoutMs.coerceAtLeast(1L))
                val schemaAvailable = actionPlan && supportsJsonSchema(deadlineNanos)
                if (schemaAvailable) {
                    runCatching {
                        executeChat(systemPrompt, userMessage, useSchema = true, numPredict = 128, timeoutMs = remainingMs(deadlineNanos))
                    }.getOrElse { schemaError ->
                        android.util.Log.w("OllamaFormat", "JSON schema request failed; retrying without schema: ${schemaError.message}")
                        executeChat(systemPrompt, userMessage, useSchema = false, numPredict = 128, timeoutMs = remainingMs(deadlineNanos))
                    }
                } else {
                    executeChat(systemPrompt, userMessage, useSchema = false, numPredict = if (actionPlan) 128 else 256, timeoutMs = remainingMs(deadlineNanos))
                }
            }
        }

    private fun remainingMs(deadlineNanos: Long): Long {
        val remaining = java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(deadlineNanos - System.nanoTime())
        if (remaining <= 0L) throw IllegalStateException("LLM time budget exceeded (45 seconds). Try a shorter command.")
        return remaining
    }

    private fun supportsJsonSchema(deadlineNanos: Long): Boolean {
        return try {
            val request = Request.Builder()
                .url(OllamaResponseParser.buildUrl(baseUrl, "/api/version"))
                .get().build()
            val versionCall = versionClient.newCall(request)
            versionCall.timeout().timeout(minOf(3_000L, remainingMs(deadlineNanos)), java.util.concurrent.TimeUnit.MILLISECONDS)
            versionCall.execute().use { response ->
                if (!response.isSuccessful) {
                    android.util.Log.w("OllamaFormat", "/api/version unavailable (HTTP ${response.code}); using plain JSON prompt")
                    return false
                }
                val version = JSONObject(response.body?.string().orEmpty()).optString("version")
                val parts = version.split(".").map { it.toIntOrNull() ?: 0 }
                val supported = parts.getOrElse(0) { 0 } > 0 ||
                    (parts.getOrElse(0) { 0 } == 0 && parts.getOrElse(1) { 0 } >= 5)
                if (!supported) android.util.Log.w("OllamaFormat", "Ollama $version is older than 0.5.0; using plain JSON prompt")
                supported
            }
        } catch (error: Exception) {
            android.util.Log.w("OllamaFormat", "/api/version check failed; continuing without schema: ${error.message}")
            false
        }
    }

    private fun executeChat(
        systemPrompt: String,
        userMessage: String,
        useSchema: Boolean,
        numPredict: Int,
        timeoutMs: Long
    ): String {
        val request = Request.Builder()
            .url(OllamaResponseParser.buildUrl(baseUrl, "/api/chat"))
            .addHeader("Content-Type", "application/json")
            .post(
                OllamaResponseParser.buildChatRequestBody(model, systemPrompt, userMessage, useSchema, numPredict)
                    .toRequestBody("application/json".toMediaType())
            ).build()
        val started = System.nanoTime()
        try {
            val chatCall = client.newCall(request)
            chatCall.timeout().timeout(timeoutMs.coerceAtLeast(1L), java.util.concurrent.TimeUnit.MILLISECONDS)
            chatCall.execute().use { response ->
                val body = response.body ?: throw java.io.IOException("Empty Ollama response")
                if (!response.isSuccessful) {
                    throw IllegalStateException(OllamaResponseParser.httpError(response.code, body.string()))
                }
                val source = body.source()
                val answer = StringBuilder()
                var firstTokenMs = -1L
                var promptTokens = -1
                var promptEvalMs = -1L
                var generationMs = -1L
                var outputTokens = 0
                while (!source.exhausted()) {
                    val line = source.readUtf8Line() ?: break
                    if (line.isBlank()) continue
                    val chunk = JSONObject(line)
                    val piece = chunk.optJSONObject("message")?.optString("content").orEmpty()
                    if (piece.isNotEmpty()) {
                        if (firstTokenMs < 0) firstTokenMs = (System.nanoTime() - started) / 1_000_000
                        answer.append(piece)
                    }
                    if (chunk.optBoolean("done", false)) {
                        promptTokens = chunk.optInt("prompt_eval_count", -1)
                        promptEvalMs = chunk.optLong("prompt_eval_duration", 0L) / 1_000_000
                        generationMs = chunk.optLong("eval_duration", 0L) / 1_000_000
                        outputTokens = chunk.optInt("eval_count", 0)
                    }
                }
                val totalMs = (System.nanoTime() - started) / 1_000_000
                android.util.Log.i("OllamaTiming", "time_to_first_token_ms=${firstTokenMs.coerceAtLeast(0)} total_ms=${totalMs} prompt_tokens=${promptTokens} cached_prompt_tokens=not-reported-by-ollama output_tokens=${outputTokens} prompt_eval_ms=${promptEvalMs} generation_ms=${generationMs}")
                val cleaned = OllamaResponseParser.stripThinkBlocks(answer.toString())
                if (cleaned.isBlank()) throw IllegalStateException("Ollama returned no answer.")
                appContext?.let { ctx ->
                    val prefs = ctx.getSharedPreferences("ollama_diagnostics", android.content.Context.MODE_PRIVATE)
                    val old = prefs.getString("recent_requests", "").orEmpty()
                    val log = "Prompt: $promptTokens tokens | first token: $firstTokenMs ms | total: $totalMs ms | prompt eval: $promptEvalMs ms | generation: $generationMs ms | output: $outputTokens tokens | schema: $useSchema"
                    prefs.edit().putString("recent_requests", (listOf(log) + old.lineSequence().filter { it.isNotBlank() }.take(11)).joinToString("\n")).apply()
                    android.util.Log.i("OllamaPerf", log)
                }
                return cleaned
            }
        } catch (error: java.net.SocketTimeoutException) {
            throw IllegalStateException("Ollama request timed out within the remaining task budget.", error)
        } catch (error: java.io.IOException) {
            throw IllegalStateException("Ollama not reachable - Termux mein ollama serve chalao. ${error.message.orEmpty()}", error)
        }
    }

    override suspend fun testConnection(): Result<Long> = withContext(Dispatchers.IO) {
        runCatching {
            val startTime = System.currentTimeMillis()
            val request = Request.Builder().url(OllamaResponseParser.buildUrl(baseUrl, "/api/tags")).get().build()
            val body = executeWithRetry(request)
            if (!OllamaResponseParser.hasModel(body, model)) {
                throw IllegalStateException("Model $model not found. Termux mein 'ollama pull $model' chalao.")
            }
            System.currentTimeMillis() - startTime
        }
    }

    private suspend fun executeWithRetry(request: Request): String {
        var lastError: java.io.IOException? = null
        for (attempt in 0..2) {
            try {
                client.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        val message = OllamaResponseParser.httpError(response.code, body)
                        if (response.code == 429 || response.code >= 500) throw java.io.IOException(message)
                        throw IllegalStateException(message)
                    }
                    if (body.isBlank()) throw java.io.IOException("Ollama returned an empty HTTP response.")
                    return body
                }
            } catch (error: java.io.IOException) {
                lastError = error
                if (attempt == 2) break
                kotlinx.coroutines.delay(500L * (attempt + 1))
            }
        }
        throw IllegalStateException("Ollama not reachable - Termux mein ollama serve chalao. ${lastError?.message.orEmpty()}", lastError)
    }

    companion object {
        const val DEFAULT_MODEL = "qwen3:1.7b"
        const val DEFAULT_BASE_URL = "http://127.0.0.1:11434"
        val AVAILABLE_MODELS = listOf(DEFAULT_MODEL)
    }
}

class CustomProvider(
    override val baseUrl: String,
    private val apiKey: String = "",
    private val model: String = "gpt-4o-mini"
) : LLMProvider {
    
    override val name: String = "Custom"
    
    override suspend fun complete(
        systemPrompt: String,
        userMessage: String
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val requestBody = JSONObject().apply {
                put("model", model)
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "system")
                        put("content", systemPrompt)
                    })
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", userMessage)
                    })
                })
                put("max_tokens", 1024)
                put("temperature", 0.1)
            }.toString()
            
            val builder = Request.Builder()
                .url("$baseUrl/chat/completions")
                .addHeader("Content-Type", "application/json")
                .post(requestBody.toRequestBody("application/json".toMediaType()))
            
            if (apiKey.isNotBlank()) {
                builder.addHeader("Authorization", "Bearer $apiKey")
            }
            
            HttpClient.client.newCall(builder.build()).execute().use { response ->
                val body = response.body?.string() ?: throw Exception("Empty response")
                val json = JSONObject(body)
                if (json.has("choices")) {
                    json.getJSONArray("choices")
                        .getJSONObject(0)
                        .getJSONObject("message")
                        .getString("content")
                } else if (json.has("error")) {
                    throw Exception(json.getJSONObject("error").getString("message"))
                } else {
                    throw Exception("Unknown response: $body")
                }
            }
        }
    }
    
    override suspend fun testConnection(): Result<Long> = withContext(Dispatchers.IO) {
        runCatching {
            val startTime = System.currentTimeMillis()
            
            val builder = Request.Builder()
                .url("$baseUrl/models")
                .get()
            
            if (apiKey.isNotBlank()) {
                builder.addHeader("Authorization", "Bearer $apiKey")
            }
            
            HttpClient.client.newCall(builder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    throw Exception("HTTP ${response.code}")
                }
            }
            System.currentTimeMillis() - startTime
        }
    }
}