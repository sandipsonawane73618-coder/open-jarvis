package com.openjarvis.agent

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray

internal object PersonalMemoryCodec {
    const val MAX_ITEMS = 50
    const val MAX_ITEM_LENGTH = 180

    fun normalize(value: String): String? {
        val clean = value
            .replace(Regex("[\\x00-\\x1F]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        return clean.takeIf { it.length in 3..MAX_ITEM_LENGTH }
    }

    private fun factKey(value: String): String? {
        val text = value.lowercase()
        return when {
            text.startsWith("my name is ") || text.startsWith("name is ") || text.startsWith("call me ") || text.startsWith("mera naam ") || text.startsWith("मेरा नाम ") || text.startsWith("माझे नाव ") -> "name"
            text.startsWith("my preferred language ") || text.startsWith("reply in ") || text.startsWith("मेरी भाषा ") || text.startsWith("माझी भाषा ") || (text.startsWith("i prefer ") && Regex("\\b(hindi|marathi|english|gujarati|tamil|telugu|kannada|bengali|punjabi)\\b").containsMatchIn(text)) -> "language"
            text.startsWith("i like ") || text.startsWith("i love ") || text.startsWith("i prefer ") || text.startsWith("my preference ") || text.contains(" pasand ") || text.contains(" पसंद ") || text.contains(" आवडते") -> "preference"
            text.startsWith("i work ") || text.startsWith("my work ") || text.startsWith("my job ") || text.startsWith("i am an ") || text.startsWith("i am a ") || text.startsWith("मेरा काम ") || text.startsWith("माझे काम ") -> "work"
            else -> null
        }
    }

    fun remember(existing: List<String>, value: String): List<String> {
        val fact = normalize(value) ?: return existing
        val key = factKey(fact)
        val withoutDuplicate = existing.filterNot {
            it.equals(fact, ignoreCase = true) || (key != null && factKey(it) == key)
        }
        return (listOf(fact) + withoutDuplicate).take(MAX_ITEMS)
    }

    fun update(existing: List<String>, oldFact: String, newFact: String): List<String> {
        val old = normalize(oldFact) ?: return existing
        val replacement = normalize(newFact) ?: return existing
        return remember(existing.filterNot { it.equals(old, ignoreCase = true) }, replacement)
    }

    fun delete(existing: List<String>, fact: String): List<String> {
        val normalized = normalize(fact) ?: return existing
        return existing.filterNot { it.equals(normalized, ignoreCase = true) || it.contains(normalized, ignoreCase = true) }
    }

    fun forget(existing: List<String>, query: String): List<String> {
        val all = query.trim().lowercase()
        if (all in setOf("all", "everything", "sab", "sab kuch", "सभी", "सब", "सब कुछ", "सगळं", "सर्व")) {
            return emptyList()
        }
        val normalized = normalize(query) ?: return existing
        return delete(existing, normalized)
    }

    private val stopWords = setOf(
        "the", "and", "for", "with", "that", "this", "you", "your", "please", "jarvis",
        "hai", "ho", "ka", "ki", "ke", "ko", "me", "mein", "mujhe", "mera", "meri",
        "aur", "karo", "kar", "batao", "bolo", "open", "please", "remember",
        "याद", "मुझे", "मेरा", "मेरी", "और", "करो", "है", "को", "में",
        "लक्षात", "ठेव", "मला", "आणि", "कर", "आहे"
    )

    private fun tokens(value: String): Set<String> =
        Regex("[\\p{L}\\p{M}\\p{N}]{2,}")
            .findAll(value.lowercase())
            .map { it.value }
            .filterNot { it in stopWords }
            .toSet()

    /**
     * Bounded local retrieval using inverse-document-frequency weighting.
     * Rare query terms contribute more than generic terms shared by many notes.
     * This is lexical retrieval only: no embeddings, network, model, or raw-query persistence.
     */
    fun retrieve(items: List<String>, query: String, limit: Int = 3): List<String> {
        val queryTokens = tokens(query)
        if (queryTokens.isEmpty() || items.isEmpty() || limit <= 0) return emptyList()

        val documents = items.map(::tokens)
        val documentCount = documents.size.toFloat()
        val weights = queryTokens.associateWith { token ->
            val frequency = documents.count { token in it }.toFloat()
            kotlin.math.ln((documentCount + 1f) / (frequency + 1f)) + 1f
        }
        val totalWeight = weights.values.sum().coerceAtLeast(1f)

        return items.withIndex()
            .mapNotNull { (index, item) ->
                val itemTokens = documents[index]
                val matchedWeight = weights.filterKeys { it in itemTokens }.values.sum()
                if (matchedWeight <= 0f) null
                else {
                    val coverage = matchedWeight / totalWeight
                    val phraseBonus = if (query.trim().length >= 4 &&
                        item.contains(query.trim(), ignoreCase = true)) 0.2f else 0f
                    Triple(item, coverage + phraseBonus, index)
                }
            }
            .sortedWith(compareByDescending<Triple<String, Float, Int>> { it.second }.thenBy { it.third })
            .take(limit)
            .map { it.first }
    }

    private fun neutralizePromptPayload(value: String): String =
        value
            .replace(Regex("(?i)system\\s*:"), "system∶")
            .replace(Regex("(?i)ignore"), "i\u200Bgnore")
            .replace(Regex("(?i)instruction"), "instr\u200Buction")
            .replace(Regex("(?is)<\\s*/?\\s*think\\s*>"), "‹think›")
            .replace("<", "‹")
            .replace(">", "›")

    fun promptContext(items: List<String>, query: String): String {
        val matches = retrieve(items, query)
        if (matches.isEmpty()) return ""
        val facts = JSONArray()
        matches.forEach { facts.put(neutralizePromptPayload(it)) }
        return facts.toString()
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

    fun update(oldFact: String, newFact: String): Boolean {
        val before = read()
        val after = PersonalMemoryCodec.update(before, oldFact, newFact)
        if (after == before) return false
        save(after)
        return true
    }

    fun delete(fact: String): Boolean = forget(fact)

    fun clearAll(): Boolean {
        val hadItems = read().isNotEmpty()
        save(emptyList())
        return hadItems
    }

    fun getAll(): List<String> = read()

    fun promptContext(query: String): String = PersonalMemoryCodec.promptContext(read(), query)

    companion object {
        private const val KEY_ITEMS = "items_json"
    }
}
