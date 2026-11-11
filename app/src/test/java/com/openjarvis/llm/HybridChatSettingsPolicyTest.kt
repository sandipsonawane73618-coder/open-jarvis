package com.openjarvis.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HybridChatSettingsPolicyTest {
    @Test
    fun defaultsKeepCloudOffAndLocalFallbackOn() {
        val settings = HybridChatSettings()
        assertFalse(settings.onlineChatEnabled)
        assertEquals(HybridChatProvider.GEMINI, settings.provider)
        assertEquals("gemini-2.5-flash", settings.model)
        assertTrue(settings.localFallbackEnabled)
    }

    @Test
    fun providerModelsHaveSafeDefaults() {
        assertEquals(HybridChatProvider.GEMINI.defaultModel, HybridChatSettingsPolicy.normalizeModel(HybridChatProvider.GEMINI, "  "))
        assertEquals(HybridChatProvider.GROQ.defaultModel, HybridChatSettingsPolicy.normalizeModel(HybridChatProvider.GROQ, null))
    }

    @Test
    fun modelInputIsTrimmedBoundedAndControlCharactersAreRemoved() {
        assertEquals("gemini-custom", HybridChatSettingsPolicy.normalizeModel(HybridChatProvider.GEMINI, "\n gemini-custom\u0000 "))
        assertEquals(100, HybridChatSettingsPolicy.normalizeModel(HybridChatProvider.GROQ, "m".repeat(120)).length)
    }

    @Test
    fun invalidStoredProviderFallsBackToGemini() {
        assertEquals(HybridChatProvider.GEMINI, HybridChatSettingsPolicy.providerFromStorage("unknown"))
        assertEquals(HybridChatProvider.GROQ, HybridChatSettingsPolicy.providerFromStorage("groq"))
    }

    @Test
    fun providerCredentialsUseDistinctEncryptedPreferenceKeys() {
        assertEquals("gemini_api_key", HybridChatSettingsStore.KEY_GEMINI_KEY)
        assertEquals("groq_api_key", HybridChatSettingsStore.KEY_GROQ_KEY)
        assertTrue(HybridChatSettingsStore.PREFS_NAME.contains("encrypted"))
        assertFalse(HybridChatSettingsStore.KEY_GEMINI_KEY == HybridChatSettingsStore.KEY_GROQ_KEY)
    }
}
