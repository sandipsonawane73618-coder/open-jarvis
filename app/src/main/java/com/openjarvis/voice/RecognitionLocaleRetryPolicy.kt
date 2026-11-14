package com.openjarvis.voice

import java.util.Locale

/** Bounded, deterministic on-device recognition fallback policy. */
internal object RecognitionLocaleRetryPolicy {
    const val MAX_RETRIES = 3

    fun localeAttempts(deviceLocale: String): List<String> {
        val normalizedDevice = normalizeLocale(deviceLocale)
        val deviceFallback = if (normalizedDevice == null) emptyList() else listOf(normalizedDevice)
        return (listOf("hi-IN", "en-IN", "mr-IN") + deviceFallback)
            .distinct()
            .take(MAX_RETRIES + 1)
    }

    fun nextLocale(attempted: List<String>, deviceLocale: String): String? =
        localeAttempts(deviceLocale).firstOrNull { it !in attempted }

    fun shouldRetry(errorCode: Int): Boolean = errorCode == 12 || errorCode == 13

    fun statusFor(
        locale: String,
        installed: Set<String>,
        pending: Set<String>,
        supported: Set<String>
    ): String = when {
        locale in installed -> "Installed"
        locale in pending -> "Download pending"
        locale in supported -> "Available"
        else -> "Unsupported"
    }

    private fun normalizeLocale(raw: String): String? {
        val locale = Locale.forLanguageTag(raw.trim().replace('_', '-'))
        if (locale.language.isBlank()) return null
        val tag = locale.toLanguageTag()
        if (tag.contains('-')) return tag
        return when (locale.language.lowercase(Locale.ROOT)) {
            "hi" -> "hi-IN"
            "en" -> "en-IN"
            "mr" -> "mr-IN"
            else -> null
        }
    }
}
