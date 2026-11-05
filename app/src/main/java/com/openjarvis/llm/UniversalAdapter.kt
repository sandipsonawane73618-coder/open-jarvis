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
        // Migrate legacy provider preferences to the required local-only setup.
        prefs.edit().apply {
            putString("provider_name", "Ollama (Local)")
            putString("provider_base_url", baseUrl)
            putString("provider_api_key", "")
            putString("provider_model", model)
            apply()
        }
        return OllamaProvider(baseUrl, model, context.applicationContext)
    }
    
    suspend fun complete(
        systemPrompt: String,
        userMessage: String
    ): Result<String> = getActiveProvider().complete(systemPrompt, userMessage)
    
    suspend fun completeConversation(systemPrompt: String, userMessage: String): Result<String> =
        getActiveProvider().completeConversation(systemPrompt, userMessage)

    suspend fun completeWithDeadline(
        systemPrompt: String,
        userMessage: String,
        timeoutMs: Long,
        conversation: Boolean = false
    ): Result<String> = getActiveProvider().completeWithDeadline(systemPrompt, userMessage, timeoutMs, conversation)

    suspend fun testConnection(): Result<Long> = getActiveProvider().testConnection()
    
    fun saveSettings(name: String, baseUrl: String, apiKey: String, model: String) {
        prefs.edit().apply {
            putString("provider_name", "Ollama (Local)")
            putString("provider_base_url", OllamaProvider.DEFAULT_BASE_URL)
            putString("provider_api_key", "")
            putString("provider_model", OllamaProvider.DEFAULT_MODEL)
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