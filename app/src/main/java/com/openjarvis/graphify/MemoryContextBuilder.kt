package com.openjarvis.graphify

import com.openjarvis.graphify.nodes.PatternNode
import com.openjarvis.graphify.nodes.TaskNode

/**
 * Privacy-preserving episodic recall.
 *
 * It emits only allow-listed task categories, never raw commands, results,
 * contact names, message text, screen content, or provider details.
 */
internal object MemoryContextBuilder {
    const val MAX_CONTEXT_CHARS = 480
    private const val MAX_TASK_CATEGORIES = 3
    private const val MAX_PATTERN_CATEGORIES = 2
    private const val MEMORY_WINDOW_MS = 30L * 24L * 60L * 60L * 1000L

    private val sensitiveIntent = Regex(
        "(?i)\\b(call|dial|message|sms|whatsapp|contact|phone|number|bhej|bhejo|send|text|otp|password|passcode|email|bank|payment)\\b"
    )
    private val phoneLike = Regex("(?<!\\d)\\+?\\d[\\d .()\\-]{6,}\\d(?!\\d)")
    private val emailLike = Regex("(?i)\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\b")
    private val urlLike = Regex("(?i)https?://|www\\.")

    private val categoryByToken = mapOf(
        "youtube" to "video search",
        "video" to "video search",
        "search" to "web search",
        "google" to "web search",
        "browser" to "web search",
        "web" to "web search",
        "settings" to "device settings",
        "setting" to "device settings",
        "timer" to "timer",
        "alarm" to "alarm",
        "torch" to "torch",
        "flashlight" to "torch",
        "volume" to "volume",
        "wifi" to "wifi",
        "bluetooth" to "bluetooth",
        "brightness" to "brightness",
        "maps" to "navigation",
        "map" to "navigation",
        "navigation" to "navigation",
        "navigate" to "navigation",
        "battery" to "battery",
        "screen" to "screen reading",
        "screenshot" to "screen reading",
        "read" to "screen reading",
        "scroll" to "scroll",
        "translate" to "translation",
        "weather" to "weather",
        "camera" to "camera",
        "home" to "navigation controls",
        "back" to "navigation controls"
    )

    private val stopWords = setOf(
        "the", "and", "for", "with", "that", "this", "please", "jarvis",
        "hai", "ho", "ka", "ki", "ke", "ko", "me", "mein", "mujhe", "mera",
        "meri", "aur", "karo", "kar", "batao", "bolo", "करो", "है", "को",
        "में", "और", "मुझे", "मेरा", "माझा", "मला", "आहे", "कर"
    )

    private fun tokens(value: String): Set<String> =
        Regex("[\\p{L}\\p{M}]{2,}")
            .findAll(value.lowercase())
            .map { it.value }
            .filterNot { it in stopWords }
            .toSet()

    private fun isSensitive(value: String): Boolean =
        sensitiveIntent.containsMatchIn(value) ||
            phoneLike.containsMatchIn(value) ||
            emailLike.containsMatchIn(value) ||
            urlLike.containsMatchIn(value)

    private fun categories(value: String): Set<String> =
        tokens(value).mapNotNull { categoryByToken[it] }.toSet()

    fun build(
        command: String,
        tasks: List<TaskNode>,
        patterns: List<PatternNode>,
        nowMs: Long
    ): String {
        if (command.isBlank() || isSensitive(command)) return ""

        val queryCategories = categories(command)
        if (queryCategories.isEmpty()) return ""

        val cutoff = nowMs - MEMORY_WINDOW_MS
        val taskCategories = tasks.asSequence()
            .filter { it.timestamp in cutoff..nowMs }
            .filter { it.result.startsWith("success", ignoreCase = true) }
            .filterNot { isSensitive(it.command) }
            .map { categories(it.command).intersect(queryCategories) }
            .filter { it.isNotEmpty() }
            .flatten()
            .distinct()
            .take(MAX_TASK_CATEGORIES)
            .toList()

        val patternCategories = patterns.asSequence()
            .filter { it.lastSeen in cutoff..nowMs && it.confidence >= 0.60f }
            .filterNot { isSensitive(it.sequenceString) }
            .map { categories(it.sequenceString).intersect(queryCategories) }
            .filter { it.isNotEmpty() }
            .flatten()
            .distinct()
            .take(MAX_PATTERN_CATEGORIES)
            .toList()

        if (taskCategories.isEmpty() && patternCategories.isEmpty()) return ""

        return buildString {
            if (taskCategories.isNotEmpty()) {
                appendLine("Previously successful task categories:")
                taskCategories.forEach { appendLine("- $it") }
            }
            if (patternCategories.isNotEmpty()) {
                appendLine("Repeated workflow categories:")
                patternCategories.forEach { appendLine("- $it") }
            }
        }.trim().take(MAX_CONTEXT_CHARS)
    }
}
