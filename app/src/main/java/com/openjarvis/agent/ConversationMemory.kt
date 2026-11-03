package com.openjarvis.agent

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Small, private on-device conversation window for continuity between Jarvis turns. */
internal class ConversationMemory(context: Context) {
    private val prefs = context.getSharedPreferences("jarvis_conversation_memory", Context.MODE_PRIVATE)

    @Synchronized
    fun recentContext(): String = prefs.getString("turns", "[]").orEmpty().let { raw ->
        runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).map { index ->
                val item = array.getJSONObject(index)
                "User: ${item.optString("user")}\nJarvis: ${item.optString("assistant")}"
            }.joinToString("\n")
        }.getOrDefault("")
    }

    @Synchronized
    fun remember(user: String, assistant: String) {
        val array = runCatching { JSONArray(prefs.getString("turns", "[]")) }.getOrElse { JSONArray() }
        array.put(JSONObject().put("user", user.take(500)).put("assistant", assistant.take(1200)))
        val trimmed = JSONArray()
        val start = (array.length() - 8).coerceAtLeast(0)
        for (index in start until array.length()) trimmed.put(array.getJSONObject(index))
        prefs.edit().putString("turns", trimmed.toString()).apply()
    }
}
