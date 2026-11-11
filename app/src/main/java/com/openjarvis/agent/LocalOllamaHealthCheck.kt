package com.openjarvis.agent

import com.openjarvis.llm.providers.OllamaProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Short, read-only health probe for the configured local Ollama instance. */
internal class LocalOllamaHealthCheck {
    private val client = OkHttpClient.Builder()
        .connectTimeout(2, TimeUnit.SECONDS)
        .readTimeout(2, TimeUnit.SECONDS)
        .callTimeout(3, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .build()

    suspend fun verify(): LocalOllamaHealthPolicy.Result = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("${OllamaProvider.DEFAULT_BASE_URL}/api/tags")
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext LocalOllamaHealthPolicy.evaluate(response.code, emptyList())
                }
                val models = JSONObject(response.body?.string().orEmpty()).optJSONArray("models")
                val names = (0 until (models?.length() ?: 0)).mapNotNull { index ->
                    models?.optJSONObject(index)?.optString("name")?.takeIf { it.isNotBlank() }
                }
                LocalOllamaHealthPolicy.evaluate(response.code, names)
            }
        } catch (error: Exception) {
            LocalOllamaHealthPolicy.unavailable(error.javaClass.simpleName)
        }
    }
}

internal object LocalOllamaHealthPolicy {
    enum class State { READY, SERVER_UNAVAILABLE, MODEL_MISSING, HTTP_ERROR }
    data class Result(val state: State, val detail: String)

    fun evaluate(httpCode: Int, modelNames: List<String>): Result {
        if (httpCode !in 200..299) return Result(State.HTTP_ERROR, "Local Ollama returned HTTP $httpCode.")
        val expected = OllamaProvider.DEFAULT_MODEL
        return if (modelNames.any { it == expected }) {
            Result(State.READY, "Local Ollama is reachable and $expected is installed.")
        } else {
            Result(State.MODEL_MISSING, "$expected is not listed by the local Ollama server. Keep the configured default unchanged.")
        }
    }

    fun unavailable(reasonType: String): Result =
        Result(State.SERVER_UNAVAILABLE, "Local Ollama health check could not connect ($reasonType). Start/check ollama serve in Termux.")
}
