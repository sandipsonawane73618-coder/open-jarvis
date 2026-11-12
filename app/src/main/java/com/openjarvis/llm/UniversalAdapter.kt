package com.openjarvis.llm

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.openjarvis.llm.providers.*
import com.openjarvis.local.ModelManager

class UniversalAdapter(private val context: Context) {
    
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()
    
    private val prefs = EncryptedSharedPreferences.create(
        context,
        "jarvis_encrypted_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
    
    fun getActiveProvider(): LLMProvider {
        val baseUrl = OllamaProvider.DEFAULT_BASE_URL
        val model = OllamaProvider.DEFAULT_MODEL
        return OllamaProvider(baseUrl, model, context.applicationContext)
    }

    private fun getChatProvider(): LLMProvider {
        val selected = prefs.getString("provider_model", OllamaProvider.DEFAULT_MODEL).orEmpty()
        val model = OllamaProvider.supportedChatModel(selected)
        return OllamaProvider(OllamaProvider.DEFAULT_BASE_URL, model, context.applicationContext)
    }

    /** Local-only provider used as Hybrid Chat fallback; keeps the installed Qwen model. */
    internal fun getLocalChatProvider(): LLMProvider = getChatProvider()
    
    suspend fun complete(
        systemPrompt: String,
        userMessage: String
    ): Result<String> = getActiveProvider().complete(systemPrompt, userMessage)
    
    suspend fun completeConversation(systemPrompt: String, userMessage: String): Result<String> =
        getChatProvider().completeConversation(systemPrompt, userMessage)

    suspend fun completeConversationStreaming(
        systemPrompt: String,
        userMessage: String,
        onChunk: (String) -> Unit
    ): Result<String> = getChatProvider().completeConversationStreaming(systemPrompt, userMessage, onChunk)

    suspend fun completeWithDeadline(
        systemPrompt: String,
        userMessage: String,
        timeoutMs: Long,
        conversation: Boolean = false
    ): Result<String> = (if (conversation) getChatProvider() else getActiveProvider())
        .completeWithDeadline(systemPrompt, userMessage, timeoutMs, conversation)

    suspend fun completeSkillWithDeadline(
        systemPrompt: String,
        userMessage: String,
        timeoutMs: Long
    ): Result<String> = getActiveProvider().completeSkillWithDeadline(systemPrompt, userMessage, timeoutMs)

    suspend fun completeNativeIntentWithDeadline(
        systemPrompt: String,
        userMessage: String,
        timeoutMs: Long
    ): Result<String> {
        if (timeoutMs <= 0L) return Result.failure(IllegalStateException("LLM time budget exceeded (45 seconds)."))
        val provider = OllamaProvider(
            OllamaProvider.DEFAULT_BASE_URL,
            "qwen3:1.7b",
            context.applicationContext
        )
        return provider.completeNativeIntentWithDeadline(systemPrompt, userMessage, timeoutMs)
    }

    suspend fun testConnection(): Result<Long> = getActiveProvider().testConnection()
    
    fun saveSettings(name: String, baseUrl: String, apiKey: String, model: String) {
        prefs.edit().apply {
            putString("provider_name", "Ollama (Local)")
            putString("provider_base_url", OllamaProvider.DEFAULT_BASE_URL)
            putString("provider_api_key", "")
            putString("provider_model", model.trim().takeIf { it in OllamaProvider.AVAILABLE_MODELS } ?: OllamaProvider.DEFAULT_MODEL)
            apply()
        }
    }

    /** Create an opt-in LocalAI provider without changing the active Ollama default. */
    fun createLocalAiProvider(baseUrl: String, model: String, apiKey: String = ""): LLMProvider {
        return LocalAiProvider(
            baseUrl = LocalAiProvider.normalizeBaseUrl(baseUrl),
            model = model.trim(),
            apiKey = apiKey
        )
    }

    fun getProviderName(): String = "Ollama (Local)"
    
    companion object {
        private var modelManager: ModelManager? = null
        
        val AVAILABLE_PROVIDERS = listOf("Ollama (Local)")
        
        fun getModelManager(context: Context): ModelManager {
            return modelManager ?: synchronized(this) {
                modelManager ?: ModelManager(context.applicationContext).also { modelManager = it }
            }
        }
        
        fun isLocalModelLoaded(): Boolean {
            return modelManager?.state?.value is ModelManager.ModelState.Loaded
        }
        
        suspend fun loadLocalModel(tier: ModelManager.ModelTier): Result<Unit> {
            return modelManager?.loadModel(tier) ?: Result.failure(Exception("ModelManager not initialized"))
        }
        
        fun getDefaultModel(provider: String): String = when (provider) {
            else -> OllamaProvider.DEFAULT_MODEL
        }
    }
}
