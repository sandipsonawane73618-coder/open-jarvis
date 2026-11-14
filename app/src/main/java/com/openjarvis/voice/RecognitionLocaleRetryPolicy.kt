package com.openjarvis.voice

import java.util.Locale

/** Bounded, deterministic on-device recognition fallback policy. */
internal object RecognitionLocaleRetryPolicy {
    const val MAX_RETRIES = 3

    fun localeAttempts(deviceLocale: String): List<String> {
        val normalizedDevice = normalizeLocale(deviceLocale)
        return (listOf("hi-IN", "en-IN", "mr-IN") + normalizedDevice)
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
        return locale.toLanguageTag().takeIf { it.contains('-') }
    }
}
