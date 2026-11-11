package com.openjarvis.llm

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

enum class HybridChatProvider(val storageKey: String, val defaultModel: String) {
    GEMINI("gemini", "gemini-2.5-flash"),
    GROQ("groq", "openai/gpt-oss-120b");

    fun other(): HybridChatProvider = if (this == GEMINI) GROQ else GEMINI
}

data class HybridChatSettings(
    val onlineChatEnabled: Boolean = false,
    val provider: HybridChatProvider = HybridChatProvider.GEMINI,
    val model: String = HybridChatProvider.GEMINI.defaultModel,
    val localFallbackEnabled: Boolean = true
)

internal object HybridChatSettingsPolicy {
    fun normalizeModel(provider: HybridChatProvider, model: String?): String {
        val normalized = model.orEmpty()
            .replace(Regex("[\\x00-\\x1F]"), "")
            .trim()
            .take(100)
        return normalized.ifBlank { provider.defaultModel }
    }

    fun providerFromStorage(value: String?): HybridChatProvider =
        HybridChatProvider.entries.firstOrNull { it.storageKey == value } ?: HybridChatProvider.GEMINI
}

/** Hybrid chat preferences and provider credentials are encrypted at rest. */
class HybridChatSettingsStore(context: Context) {
    private val appContext = context.applicationContext
    private val preferences by lazy {
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            appContext,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun load(): HybridChatSettings {
        val provider = HybridChatSettingsPolicy.providerFromStorage(
            preferences.getString(KEY_PROVIDER, HybridChatProvider.GEMINI.storageKey)
        )
        return HybridChatSettings(
            onlineChatEnabled = preferences.getBoolean(KEY_ONLINE_ENABLED, false),
            provider = provider,
            model = HybridChatSettingsPolicy.normalizeModel(
                provider,
                preferences.getString(KEY_MODEL, provider.defaultModel)
            ),
            localFallbackEnabled = preferences.getBoolean(KEY_LOCAL_FALLBACK, true)
        )
    }

    /** Null credential arguments preserve the existing encrypted key. */
    fun save(
        settings: HybridChatSettings,
        geminiApiKey: String? = null,
        groqApiKey: String? = null
    ) {
        preferences.edit().apply {
            putBoolean(KEY_ONLINE_ENABLED, settings.onlineChatEnabled)
            putString(KEY_PROVIDER, settings.provider.storageKey)
            putString(KEY_MODEL, HybridChatSettingsPolicy.normalizeModel(settings.provider, settings.model))
            putBoolean(KEY_LOCAL_FALLBACK, settings.localFallbackEnabled)
            if (geminiApiKey != null) putString(KEY_GEMINI_KEY, geminiApiKey.trim())
            if (groqApiKey != null) putString(KEY_GROQ_KEY, groqApiKey.trim())
            apply()
        }
    }

    fun apiKey(provider: HybridChatProvider): String = preferences.getString(
        if (provider == HybridChatProvider.GEMINI) KEY_GEMINI_KEY else KEY_GROQ_KEY,
        ""
    ).orEmpty()

    fun saveApiKey(provider: HybridChatProvider, apiKey: String) {
        preferences.edit()
            .putString(if (provider == HybridChatProvider.GEMINI) KEY_GEMINI_KEY else KEY_GROQ_KEY, apiKey.trim())
            .apply()
    }

    companion object {
        internal const val PREFS_NAME = "jarvis_hybrid_chat_encrypted"
        internal const val KEY_GEMINI_KEY = "gemini_api_key"
        internal const val KEY_GROQ_KEY = "groq_api_key"
        private const val KEY_ONLINE_ENABLED = "online_chat_enabled"
        private const val KEY_PROVIDER = "online_chat_provider"
        private const val KEY_MODEL = "online_chat_model"
        private const val KEY_LOCAL_FALLBACK = "local_chat_fallback_enabled"
    }
}
