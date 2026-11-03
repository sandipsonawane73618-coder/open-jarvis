package com.openjarvis.llm.providers

import org.json.JSONArray
import org.json.JSONObject

internal object OllamaResponseParser {
    const val DEFAULT_BASE_URL = "http://127.0.0.1:11434"
    const val DEFAULT_MODEL = "qwen3:1.7b"

    private val thinkBlock = Regex("(?is)<think>.*?</think>")

    fun normalizeBaseUrl(raw: String): String {
        val trimmed = raw.trim()
        require(trimmed.isNotEmpty()) { "URL missing" }
        val withScheme = if (
            trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true)
        ) trimmed else "http://$trimmed"
        return withScheme.trimEnd('/')
    }

    /** The only URL joiner used by Ollama requests. */
    fun buildUrl(baseUrl: String, path: String): String {
        val base = normalizeBaseUrl(baseUrl)
        val cleanPath = path.trim().trimStart('/')
        require(cleanPath.isNotEmpty()) { "Ollama endpoint path missing" }
        return "$base/$cleanPath"
    }

    fun buildChatRequestBody(model: String, systemPrompt: String, userMessage: String): String =
        JSONObject().apply {
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
            put("stream", true)
            put("think", false)
            put("keep_alive", "30m")
            put("options", JSONObject().put("num_ctx", 2048))
        }.toString()

    fun stripThinkBlocks(content: String): String = thinkBlock.replace(content, "").trim()

    fun parseChatContent(body: String): String {
        val json = JSONObject(body)
        if (json.has("error")) {
            throw IllegalStateException(
                json.optString("error").ifBlank { "Ollama returned an unspecified error." }
            )
        }
        val message = json.optJSONObject("message")
            ?: throw IllegalStateException("Ollama response is missing the message object.")
        val cleaned = stripThinkBlocks(message.optString("content", ""))
        if (cleaned.isBlank()) {
            throw IllegalStateException("Ollama returned no answer after removing its thinking block.")
        }
        return cleaned
    }

    fun hasModel(tagsBody: String, expectedModel: String): Boolean {
        val models = JSONObject(tagsBody).optJSONArray("models") ?: return false
        for (index in 0 until models.length()) {
            val name = models.optJSONObject(index)?.optString("name").orEmpty()
            if (name == expectedModel || name.startsWith("$expectedModel:")) return true
        }
        return false
    }

    fun httpError(code: Int, body: String): String {
        val detail = runCatching { JSONObject(body).optString("error") }.getOrNull().orEmpty()
        return if (detail.isNotBlank()) "Ollama HTTP $code: $detail" else "Ollama HTTP $code"
    }
}
