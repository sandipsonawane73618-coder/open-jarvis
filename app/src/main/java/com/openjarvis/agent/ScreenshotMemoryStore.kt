package com.openjarvis.agent

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray

internal object ScreenshotMemoryCodec {
    const val MAX_ITEMS = 8
    const val MAX_TEXT_LENGTH = 2_000

    fun normalize(text: String): String? {
        val clean = text
            .replace(Regex("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]"), " ")
            .lineSequence()
            .map { it.replace(Regex("\\s+"), " ").trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n")
            .trim()
        return clean.takeIf { it.length in 3..MAX_TEXT_LENGTH }
    }

    fun remember(existing: List<String>, text: String): List<String> {
        val note = normalize(text) ?: return existing
        val unique = existing.filterNot { it.equals(note, ignoreCase = true) }
        return (listOf(note) + unique).take(MAX_ITEMS)
    }

    fun retrieve(items: List<String>, query: String, limit: Int = 2): List<String> =
        PersonalMemoryCodec.retrieve(items, query, limit)
            .map { it.take(MAX_TEXT_LENGTH) }

    fun promptContext(items: List<String>, query: String): String {
        val matches = retrieve(items, query)
        if (matches.isEmpty()) return ""
        return matches.mapIndexed { index, text ->
            "- Saved screen ${index + 1}: ${text.take(650)}"
        }.joinToString("\n").take(1_300)
    }

    fun decode(raw: String?): List<String> = runCatching {
        val array = JSONArray(raw ?: "[]")
        (0 until array.length()).mapNotNull { index ->
            normalize(array.optString(index))
        }.take(MAX_ITEMS)
    }.getOrDefault(emptyList())

    fun encode(items: List<String>): String {
        val array = JSONArray()
        items.take(MAX_ITEMS).mapNotNull(::normalize).forEach(array::put)
        return array.toString()
    }
}

/**
 * Explicitly saved screen text only. Images are never retained; OCR text is encrypted
 * locally, bounded, and recalled only when the current request matches its content.
 */
class ScreenshotMemoryStore(context: Context) {
    private val appContext = context.applicationContext
    private val preferences by lazy {
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            appContext,
            "jarvis_screenshot_memory",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private fun read(): List<String> =
        ScreenshotMemoryCodec.decode(preferences.getString(KEY_ITEMS, "[]"))

    fun remember(text: String): Boolean {
        val normalized = ScreenshotMemoryCodec.normalize(text) ?: return false
        val updated = ScreenshotMemoryCodec.remember(read(), normalized)
        preferences.edit().putString(KEY_ITEMS, ScreenshotMemoryCodec.encode(updated)).apply()
        return true
    }

    fun forgetAll() {
        preferences.edit().remove(KEY_ITEMS).apply()
    }

    fun promptContext(query: String): String =
        ScreenshotMemoryCodec.promptContext(read(), query)

    companion object {
        private const val KEY_ITEMS = "saved_screen_text_json"
    }
}
