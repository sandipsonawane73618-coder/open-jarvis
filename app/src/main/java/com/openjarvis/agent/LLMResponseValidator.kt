package com.openjarvis.agent

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

object LLMResponseValidator {
    private val knownActions = setOf(
        Action.OPEN_APP, Action.CLICK, Action.TAP, Action.TYPE_TEXT, Action.TYPE,
        Action.SCROLL, Action.BACK, Action.PRESS_BACK, Action.HOME, Action.PRESS_HOME,
        Action.PRESS_RECENTS, Action.WAIT, Action.WAIT_FOR, Action.READ_SCREEN,
        Action.AI_PROMPT, Action.EXTRACT_TEXT, Action.ERROR, Action.COMPLETE
    )

    fun validate(rawResponse: String): ValidationResult {
        val withoutThink = Regex("(?is)<think>.*?</think>").replace(rawResponse, "").trim()
        val normalized = normalizeSingleQuotedJson(withoutThink)
        var clean = extractJson(normalized)
        var wasRepaired = false
        if (clean == null) {
            clean = repairCertainMixedArray(normalized)
            wasRepaired = clean != null
        }
        clean = clean ?: return invalid("Response is not JSON: no action array/object found")

        val parsed = try {
            when {
                clean.startsWith("[") -> JSONArray(clean)
                clean.startsWith("{") -> {
                    val root = JSONObject(clean)
                    when {
                        root.optJSONArray("actions") != null -> root.getJSONArray("actions")
                        root.optJSONArray("steps") != null -> root.getJSONArray("steps")
                        root.has("action") || root.has("type") -> JSONArray().put(root)
                        else -> return invalid("JSON object must contain an actions array")
                    }
                }
                else -> return invalid("Response is not JSON")
            }
        } catch (error: JSONException) {
            return invalid("Invalid JSON: ${error.message ?: "malformed response"}")
        }

        if (parsed.length() == 0) return invalid("Empty action plan")
        if (parsed.length() > 50) return invalid("Action plan too long (${parsed.length()} steps); maximum is 50")

        val actions = mutableListOf<Action>()
        for (index in 0 until parsed.length()) {
            val item = parsed.opt(index)
            if (item !is JSONObject) return invalid("Item $index is not an object")
            val rawType = firstString(item, "action", "type", "command")
            if (rawType.isBlank()) return invalid("Item $index missing 'action' field")
            val type = normalizeAction(rawType)
            if (type !in knownActions) return invalid("Unsupported action type: $rawType")
            val action = parseAction(item, type)
            val missingField = when (action.action) {
                Action.OPEN_APP -> if (action.label.isNullOrBlank() && action.packageName.isNullOrBlank()) "label or package" else null
                Action.CLICK, Action.TAP -> if (action.text.isNullOrBlank()) "target" else null
                Action.TYPE_TEXT, Action.TYPE -> if (action.value.isNullOrBlank()) "value" else null
                Action.SCROLL -> if (action.direction.isNullOrBlank()) "direction" else null
                Action.WAIT, Action.WAIT_FOR -> if (action.text.isNullOrBlank()) "visible text" else null
                Action.AI_PROMPT -> if (action.packageName.isNullOrBlank()) "package" else if (action.prompt.isNullOrBlank()) "prompt" else null
                else -> null
            }
            if (missingField != null) {
                return invalid("Action $index ${action.action} requires non-empty $missingField")
            }
            actions.add(action)
        }
        return ValidationResult(true, actions, emptyList(), wasRepaired)
    }

    /**
     * Convert single-quoted JSON strings to JSON double quotes. This is only a syntax
     * normalization; action names and values are not changed.
     */
    private fun normalizeSingleQuotedJson(source: String): String {
        val output = StringBuilder(source.length)
        var inSingle = false
        var inDouble = false
        var escaped = false
        for (ch in source) {
            if (escaped) {
                output.append(ch)
                escaped = false
                continue
            }
            if ((inSingle || inDouble) && ch == '\\') {
                output.append(ch)
                escaped = true
                continue
            }
            when (ch) {
                '\'' -> {
                    if (!inDouble) {
                        inSingle = !inSingle
                        output.append('"')
                    } else output.append(ch)
                }
                '"' -> {
                    if (!inSingle) inDouble = !inDouble
                    output.append(ch)
                }
                else -> output.append(ch)
            }
        }
        return output.toString()
    }

    /**
     * Repair only the known small-model shape ["action_name","field":"value"].
     * The first item must be a supported action and the second key must be a known
     * action field; otherwise the validator rejects the response unchanged.
     */
    private fun repairCertainMixedArray(source: String): String? {
        val match = Regex(
            """^\s*\[\s*"([a-zA-Z_ -]+)"\s*,\s*"([a-zA-Z_]+)"\s*:\s*"((?:\\.|[^"\\])*)"\s*\]\s*$""",
            RegexOption.DOT_MATCHES_ALL
        ).matchEntire(source) ?: return null
        val action = match.groupValues[1].trim()
        val key = match.groupValues[2].trim()
        val value = match.groupValues[3]
        if (normalizeAction(action) !in knownActions) return null
        if (key !in setOf("value", "target", "label", "direction", "message", "hint", "package")) return null
        return JSONArray().put(JSONObject().put("action", action).put(key, value)).toString()
    }

    private fun extractJson(raw: String): String? {
        val source = raw.trim()
            .replace(Regex("""^\s*```(?:json)?\s*""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s*```\s*$"""), "")
            .trim()
        if (source.isEmpty()) return null
        val firstContent = source.indexOfFirst { !it.isWhitespace() }

        // First prefer a complete action array, even if stray prose brackets occur before it.
        for (start in source.indices) {
            if (source[start] != '[') continue
            val candidate = extractBalancedJson(source, start) ?: continue
            val parsed = runCatching { JSONArray(candidate) }.getOrNull() ?: continue
            val isPrimary = start == firstContent
            if (isPrimary || parsed.length() == 0 || parsed.opt(0) is JSONObject) return candidate
        }

        // Then accept a wrapped actions/steps object.
        for (start in source.indices) {
            if (source[start] != '{') continue
            val candidate = extractBalancedJson(source, start) ?: continue
            val parsed = runCatching { JSONObject(candidate) }.getOrNull() ?: continue
            if (parsed.optJSONArray("actions") != null || parsed.optJSONArray("steps") != null) return candidate
        }

        // Some small models emit one JSON action object per line instead of an array.
        // Collect only complete objects that actually contain an action discriminator.
        val actionObjects = JSONArray()
        var index = 0
        while (index < source.length) {
            if (source[index] != '{') {
                index++
                continue
            }
            val candidate = extractBalancedJson(source, index)
            if (candidate == null) {
                index++
                continue
            }
            val parsed = runCatching { JSONObject(candidate) }.getOrNull()
            val actionType = parsed?.let {
                listOf("action", "type", "command").firstNotNullOfOrNull { key ->
                    it.optString(key).takeIf(String::isNotBlank)
                }
            }
            if (parsed != null && actionType != null) {
                actionObjects.put(parsed)
                index += candidate.length
            } else {
                index++
            }
        }
        return when (actionObjects.length()) {
            0 -> null
            1 -> actionObjects.getJSONObject(0).toString()
            else -> actionObjects.toString()
        }
    }

    /** Returns the first balanced JSON object/array beginning at [start], respecting quoted strings. */
    private fun extractBalancedJson(source: String, start: Int): String? {
        if (start !in source.indices || source[start] !in charArrayOf('{', '[')) return null
        val expectedClosers = mutableListOf<Char>()
        var inString = false
        var escaped = false
        for (index in start until source.length) {
            val char = source[index]
            if (inString) {
                if (escaped) escaped = false
                else when {
                    char == 92.toChar() -> escaped = true
                    char == '"' -> inString = false
                }
                continue
            }
            when (char) {
                '"' -> inString = true
                '{' -> expectedClosers.add('}')
                '[' -> expectedClosers.add(']')
                '}', ']' -> {
                    if (expectedClosers.isEmpty() || expectedClosers.removeAt(expectedClosers.lastIndex) != char) return null
                    if (expectedClosers.isEmpty()) return source.substring(start, index + 1)
                }
            }
        }
        return null
    }
    private fun normalizeAction(raw: String): String {
        val key = raw.trim().lowercase().replace('-', '_').replace(' ', '_')
        return when (key) {
            "tap", "click", "click_text", "tap_text", "tap_on", "click_element", "press" -> Action.CLICK
            "type", "type_text", "enter_text", "input_text", "input", "enter" -> Action.TYPE_TEXT
            "press_back", "go_back", "back", "navigate_back" -> Action.BACK
            "press_home", "go_home", "home", "go_to_home" -> Action.HOME
            "wait_for", "wait", "pause" -> Action.WAIT
            "read_screen", "readscreen", "screen_read", "get_screen_text" -> Action.READ_SCREEN
            "launch_app", "open", "open_application", "start_app" -> Action.OPEN_APP
            "swipe", "scroll_screen", "scroll_page" -> Action.SCROLL
            else -> key
        }
    }

    private fun parseAction(obj: JSONObject, type: String): Action {
        val target = firstString(obj, "text", "target", "element", "label").takeIf { it.isNotBlank() }
        val value = firstString(obj, "value", "text", "input").takeIf { it.isNotBlank() }
        // The legacy phase-4 {steps:[{action,target,value}]} schema stores scroll direction
        // in target and an error's user-facing explanation in value. Keep the current
        // canonical direction/message fields authoritative whenever supplied.
        val direction = firstString(obj, "direction", "scroll_direction").ifBlank {
            if (type == Action.SCROLL) target.orEmpty() else ""
        }.takeIf { it.isNotBlank() }
        val message = firstString(obj, "message", "reason").ifBlank {
            if (type == Action.ERROR) value.orEmpty() else ""
        }.takeIf { it.isNotBlank() }
        return Action(
            action = type,
            packageName = firstString(obj, "package", "packageName", "package_name").takeIf { it.isNotBlank() },
            label = firstString(obj, "label", "app", "app_name", "target").takeIf { it.isNotBlank() },
            text = target,
            value = value,
            hint = firstString(obj, "hint").takeIf { it.isNotBlank() },
            direction = direction,
            distance = firstString(obj, "distance").takeIf { it.isNotBlank() },
            timeoutMs = firstLong(obj, 3000L, "timeout_ms", "timeoutMs", "duration_ms"),
            message = message,
            prompt = firstString(obj, "prompt").takeIf { it.isNotBlank() },
            outputKey = firstString(obj, "outputKey", "output_key").takeIf { it.isNotBlank() }
        )
    }

    private fun firstString(obj: JSONObject, vararg keys: String): String {
        for (key in keys) {
            val value = obj.opt(key)
            if (value is String && value.isNotBlank()) return value.trim()
        }
        return ""
    }

    private fun firstLong(obj: JSONObject, fallback: Long, vararg keys: String): Long {
        for (key in keys) {
            if (obj.has(key)) {
                val value = obj.optLong(key, fallback)
                if (value > 0L) return value
            }
        }
        return fallback
    }

    private fun invalid(message: String) =
        ValidationResult(false, emptyList(), listOf(message), false)

    data class ValidationResult(
        val isValid: Boolean,
        val actions: List<Action>,
        val errors: List<String>,
        val wasRepaired: Boolean
    )
}
