package com.openjarvis.agent

import android.content.Context
import org.json.JSONObject
import java.text.Normalizer
import java.util.Locale

/**
 * Persists only deterministic native intents that completed successfully.
 * Keys are exact normalized utterances; no fuzzy matching or UI-plan replay.
 */
internal class NativeLearnedStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("native_learned_intents", Context.MODE_PRIVATE)

    fun find(command: String): StepIntent? {
        val key = normalize(command)
        if (key.isBlank()) return null
        val raw = prefs.getString(key, null) ?: return null
        return runCatching { decode(JSONObject(raw)) }.getOrNull()
    }

    fun save(command: String, intent: StepIntent) {
        val key = normalize(command)
        if (key.isBlank()) return
        prefs.edit().putString(key, encode(intent).toString()).apply()
    }

    private fun encode(intent: StepIntent) = JSONObject().apply {
        when (intent) {
            is StepIntent.Alarm -> put("type", "alarm").put("hour", intent.hour).put("minute", intent.minute)
            is StepIntent.Timer -> put("type", "timer").put("seconds", intent.seconds)
            is StepIntent.Torch -> put("type", "torch").put("on", intent.on)
            is StepIntent.Volume -> put("type", "volume").put("up", intent.up)
            is StepIntent.Navigate -> put("type", "navigate").put("place", intent.place)
            is StepIntent.Message -> put("type", "message").put("app", intent.app).put("contact", intent.contact).put("text", intent.text)
            is StepIntent.AppLaunch -> put("type", "app_launch").put("label", intent.label)
            is StepIntent.ScreenRead -> put("type", "screen_read").put("remember", intent.remember)
            is StepIntent.Screenshot -> put("type", "screenshot").put("saveToGallery", intent.saveToGallery)
        }
    }

    private fun decode(json: JSONObject): StepIntent = when (json.getString("type")) {
        "alarm" -> StepIntent.Alarm(json.getInt("hour"), json.getInt("minute")).also {
            require(it.hour in 0..23 && it.minute in 0..59)
        }
        "timer" -> StepIntent.Timer(json.getInt("seconds")).also { require(it.seconds in 1..86400) }
        "torch" -> StepIntent.Torch(json.getBoolean("on"))
        "volume" -> StepIntent.Volume(json.getBoolean("up"))
        "navigate" -> StepIntent.Navigate(json.getString("place").take(120)).also { require(it.place.isNotBlank()) }
        "message" -> StepIntent.Message(json.optString("app").takeIf { it.isNotBlank() }, json.optString("contact").takeIf { it.isNotBlank() }, json.optString("text").takeIf { it.isNotBlank() })
        "app_launch" -> StepIntent.AppLaunch(json.getString("label").take(60)).also { require(it.label.isNotBlank()) }
        "screen_read" -> StepIntent.ScreenRead(json.optBoolean("remember", false))
        "screenshot" -> StepIntent.Screenshot(json.optBoolean("saveToGallery", true))
        else -> throw IllegalArgumentException("Unknown learned native intent")
    }

    private fun normalize(value: String): String =
        Normalizer.normalize(value.trim(), Normalizer.Form.NFC)
            .lowercase(Locale.ROOT)
            .replace(Regex("[\\p{Punct}।॥“”‘’]"), " ")
            .replace(Regex("\\s+"), " ")
            .take(240)
}
