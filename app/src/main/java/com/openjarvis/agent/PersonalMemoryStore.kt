package com.openjarvis.agent

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray

internal object PersonalMemoryCodec {
    const val MAX_ITEMS = 20
    const val MAX_ITEM_LENGTH = 180

    fun normalize(value: String): String? {
        val clean = value
            .replace(Regex("[\\x00-\\x1F]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        return clean.takeIf { it.length in 3..MAX_ITEM_LENGTH }
    }

    fun remember(existing: List<String>, value: String): List<String> {
        val fact = normalize(value) ?: return existing
        val withoutDuplicate = existing.filterNot { it.equals(fact, ignoreCase = true) }
        return (listOf(fact) + withoutDuplicate).take(MAX_ITEMS)
    }

    fun forget(existing: List<String>, query: String): List<String> {
        val normalized = normalize(query) ?: return existing
        if (normalized.equals("all", true) ||
            normalized.equals("everything", true) ||
            normalized.equals("sab kuch", true) ||
            normalized.equals("सभी", true)
        ) return emptyList()
        return existing.filterNot { it.contains(normalized, ignoreCase = true) }
    }

    fun promptContext(items: List<String>): String {
        if (items.isEmpty()) return "No user-saved preferences."
        return items.take(8).joinToString(separator = "\n") { "- $it" }.take(650)
    }
}

/** User-controlled notes encrypted locally; only explicit remember/forget commands modify them. */
class PersonalMemoryStore(context: Context) {
    private val appContext = context.applicationContext
    private val preferences by lazy {
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            appContext,
            "jarvis_personal_memory",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private fun read(): List<String> = runCatching {
        val raw = preferences.getString(KEY_ITEMS, "[]") ?: "[]"
        val array = JSONArray(raw)
        (0 until array.length()).mapNotNull { index ->
            array.optString(index).takeIf { it.isNotBlank() }
        }.take(PersonalMemoryCodec.MAX_ITEMS)
    }.getOrDefault(emptyList())

    private fun save(items: List<String>) {
        val array = JSONArray()
        items.take(PersonalMemoryCodec.MAX_ITEMS).forEach(array::put)
        preferences.edit().putString(KEY_ITEMS, array.toString()).apply()
    }

    fun remember(value: String): Boolean {
        val normalized = PersonalMemoryCodec.normalize(value) ?: return false
        save(PersonalMemoryCodec.remember(read(), normalized))
        return true
    }

    fun forget(query: String): Boolean {
        val before = read()
        val after = PersonalMemoryCodec.forget(before, query)
        if (after == before) return false
        save(after)
        return true
    }

    fun getAll(): List<String> = read()

    fun promptContext(): String = PersonalMemoryCodec.promptContext(read())

    companion object {
        private const val KEY_ITEMS = "items_json"
    }
}
