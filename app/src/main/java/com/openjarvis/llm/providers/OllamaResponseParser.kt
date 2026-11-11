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

    fun buildChatRequestBody(
        model: String,
        systemPrompt: String,
        userMessage: String,
        useJsonSchema: Boolean = false,
        numPredict: Int = 128,
        useSkillSchema: Boolean = false,
        useNativeIntentSchema: Boolean = false
    ): String =
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
            if (useNativeIntentSchema) put("format", nativeIntentSchema()) else if (useSkillSchema) put("format", skillPlanSchema()) else if (useJsonSchema) put("format", actionPlanSchema())
            put("think", false)
            put("keep_alive", "30m")
            put("options", JSONObject().apply {
                put("num_ctx", 2048)
                put("num_predict", numPredict.coerceIn(32, 512))
                put("temperature", 0.1)
            })
        }.toString()


    fun nativeIntentSchema(): JSONObject = JSONObject().apply {
        put("type", "object")
        put("properties", JSONObject().apply {
            put("skill", JSONObject().put("type", "string").put("enum", JSONArray(listOf(
                "none", "alarm", "timer", "torch", "volume", "navigate", "app_launch", "message"
            ))))
            put("args", JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().apply {
                    put("hour", JSONObject().put("type", "integer").put("minimum", 0).put("maximum", 23))
                    put("minute", JSONObject().put("type", "integer").put("minimum", 0).put("maximum", 59))
                    put("seconds", JSONObject().put("type", "integer").put("minimum", 1).put("maximum", 86400))
                    put("on", JSONObject().put("type", "boolean"))
                    put("up", JSONObject().put("type", "boolean"))
                    put("place", JSONObject().put("type", "string").put("maxLength", 120))
                    put("label", JSONObject().put("type", "string").put("maxLength", 60))
                    put("app", JSONObject().put("type", "string").put("enum", JSONArray(listOf("whatsapp", "sms"))))
                    put("contact", JSONObject().put("type", "string").put("maxLength", 100))
                    put("text", JSONObject().put("type", "string").put("maxLength", 1000))
                })
                put("additionalProperties", false)
            })
        })
        put("required", JSONArray(listOf("skill", "args")))
        put("additionalProperties", false)
    }

    fun actionPlanSchema(): JSONObject = JSONObject().apply {
        put("type", "array")
        put("items", JSONObject().apply {
            put("type", "object")
            put("properties", JSONObject().apply {
                put("action", JSONObject().put("type", "string").put("enum", JSONArray(listOf(
                    "open_app", "click", "tap", "type_text", "type", "scroll", "back",
                    "press_back", "home", "press_home", "press_recents", "wait", "wait_for",
                    "read_screen", "ai_prompt", "extract_text", "error"
                ))))
                put("label", JSONObject().put("type", "string"))
                put("target", JSONObject().put("type", "string"))
                put("text", JSONObject().put("type", "string"))
                put("element", JSONObject().put("type", "string"))
                put("value", JSONObject().put("type", "string"))
                put("input", JSONObject().put("type", "string"))
                put("direction", JSONObject().put("type", "string"))
                put("distance", JSONObject().put("type", "string"))
                put("timeout_ms", JSONObject().put("type", "integer"))
                put("message", JSONObject().put("type", "string"))
                put("reason", JSONObject().put("type", "string"))
                put("prompt", JSONObject().put("type", "string"))
                put("outputKey", JSONObject().put("type", "string"))
                put("output_key", JSONObject().put("type", "string"))
                put("package", JSONObject().put("type", "string"))
                put("hint", JSONObject().put("type", "string"))
            })
            put("required", JSONArray(listOf("action")))
            put("additionalProperties", false)
        })
    }


    fun skillPlanSchema(): JSONObject {
        fun actionItemSchema() = JSONObject().apply {
            put("type", "object")
            put("properties", JSONObject().apply {
                put("action", JSONObject().put("type", "string").put("enum", JSONArray(listOf(
                    "open_app", "click", "tap", "type_text", "type", "scroll", "back",
                    "press_back", "home", "press_home", "press_recents", "wait", "wait_for",
                    "read_screen", "ai_prompt", "extract_text", "error"
                ))))
                listOf("label", "target", "text", "element", "value", "input", "direction",
                    "distance", "message", "reason", "prompt", "outputKey", "output_key",
                    "package", "hint").forEach { put(it, JSONObject().put("type", "string")) }
                put("timeout_ms", JSONObject().put("type", "integer"))
            })
            put("required", JSONArray(listOf("action")))
            put("additionalProperties", false)
        }
        return JSONObject().apply {
            put("type", "object")
            put("properties", JSONObject().apply {
                put("skill", JSONObject().put("type", "string").put("enum", JSONArray(listOf(
                    "OPEN_APP", "SETTINGS", "CALL", "SEARCH", "WHATSAPP_MESSAGE", "SMS_MESSAGE",
                    "ALARM", "TIMER", "TORCH", "VOLUME", "MAPS", "ANDROID_UI_ACTIONS", "UNKNOWN"
                ))))
                put("args", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject().apply {
                        listOf("app", "screen", "contact", "text", "hour", "minute", "seconds",
                            "direction", "destination").forEach { put(it, JSONObject().put("type", "string")) }
                        put("enabled", JSONObject().put("type", "boolean"))
                        put("actions", JSONObject().put("type", "array").put("items", actionItemSchema()))
                    })
                    put("additionalProperties", false)
                })
            })
            put("required", JSONArray(listOf("skill", "args")))
            put("additionalProperties", false)
        }
    }

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
