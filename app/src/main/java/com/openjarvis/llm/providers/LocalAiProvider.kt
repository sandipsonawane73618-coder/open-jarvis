package com.openjarvis.llm.providers

import com.openjarvis.llm.ConnectionResult
import com.openjarvis.llm.LLMProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Optional LocalAI connector. LocalAI exposes an OpenAI-compatible API; this
 * provider is deliberately separate from the default Ollama provider.
 */
class LocalAiProvider(
    override val baseUrl: String,
    private val model: String,
    private val apiKey: String = ""
) : LLMProvider {
    override val name: String = "LocalAI"

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    private fun endpoint(path: String): String {
        val root = baseUrl.trim().trimEnd('/')
        require(root.startsWith("http://") || root.startsWith("https://")) {
            "LocalAI URL must start with http:// or https://"
        }
        return if (root.endsWith("/v1")) "$root/$path" else "$root/v1/$path"
    }

    override suspend fun complete(systemPrompt: String, userMessage: String): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                require(model.isNotBlank()) { "LocalAI model name is required" }
                val payload = JSONObject().apply {
                    put("model", model)
                    put("stream", false)
                    put("temperature", 0.1)
                    put("messages", JSONArray()
                        .put(JSONObject().put("role", "system").put("content", systemPrompt))
                        .put(JSONObject().put("role", "user").put("content", userMessage)))
                }.toString()
                val request = request(endpoint("chat/completions"))
                    .post(payload.toRequestBody("application/json".toMediaType()))
                    .build()
                client.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (!response.isSuccessful) throw IllegalStateException("LocalAI HTTP ${response.code}: ${body.take(400)}")
                    val choices = JSONObject(body).optJSONArray("choices")
                        ?: throw IllegalStateException("LocalAI response has no choices")
                    val content = choices.optJSONObject(0)?.optJSONObject("message")?.optString("content").orEmpty()
                    content.takeIf { it.isNotBlank() } ?: throw IllegalStateException("LocalAI returned empty content")
                }
            }
        }

    override suspend fun testConnection(): Result<Long> = withContext(Dispatchers.IO) {
        runCatching {
            val started = System.currentTimeMillis()
            val request = request(endpoint("models")).get().build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw IllegalStateException("LocalAI HTTP ${response.code}: ${body.take(300)}")
                val models = JSONObject(body).optJSONArray("data")
                    ?: throw IllegalStateException("LocalAI /models response is invalid")
                if (models.length() == 0) throw IllegalStateException("LocalAI has no models loaded")
            }
            System.currentTimeMillis() - started
        }
    }

    private fun request(url: String): Request.Builder =
        Request.Builder().url(url).header("Content-Type", "application/json").apply {
            if (apiKey.isNotBlank()) header("Authorization", "Bearer $apiKey")
        }

    companion object {
        fun normalizeBaseUrl(value: String): String {
            val trimmed = value.trim().trimEnd('/')
            require(trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                "LocalAI URL must start with http:// or https://"
            }
            return trimmed
        }
    }
}
