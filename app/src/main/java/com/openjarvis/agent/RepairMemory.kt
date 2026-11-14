package com.openjarvis.agent

import android.content.Context
import org.json.JSONObject
import java.security.MessageDigest
import java.util.Locale

/** Bounded local store for explicitly verified, non-sensitive command corrections. */
class RepairMemory(context: Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun normalize(command: String): String {
        if (!ExperimentalSelfHealingSettings.isEnabled(app)) return command
        val key = RepairMemoryPolicy.commandKey(command)
        return runCatching { JSONObject(prefs.getString(KEY_ALIASES, "{}") ?: "{}").optString(key, "") }
            .getOrDefault("").ifBlank { command }
    }

    @Synchronized
    fun rememberVerifiedCorrection(original: String, corrected: String, verified: Boolean): Boolean {
        if (!ExperimentalSelfHealingSettings.isEnabled(app) ||
            !RepairMemoryPolicy.canRemember(original, corrected, verified)) return false
        return runCatching {
            val aliases = JSONObject(prefs.getString(KEY_ALIASES, "{}") ?: "{}")
            aliases.put(RepairMemoryPolicy.commandKey(original), corrected.trim())
            val bounded = JSONObject()
            aliases.keys().asSequence().toList().takeLast(MAX_ALIASES)
                .forEach { key -> bounded.put(key, aliases.optString(key)) }
            prefs.edit().putString(KEY_ALIASES, bounded.toString()).apply()
            true
        }.getOrDefault(false)
    }

    @Synchronized
    fun recordFailure(code: String, stage: String) {
        if (!ExperimentalSelfHealingSettings.isEnabled(app)) return
        val key = code.trim().uppercase(Locale.ROOT).take(64).ifBlank { "UNKNOWN" }
        val counts = runCatching { JSONObject(prefs.getString(KEY_FAILURES, "{}") ?: "{}") }
            .getOrDefault(JSONObject())
        counts.put(key, (counts.optInt(key, 0) + 1).coerceAtMost(999))
        prefs.edit().putString(KEY_FAILURES, counts.toString())
            .putString(KEY_LAST_FAILURE, "$key@${stage.trim().take(48)}").apply()
    }

    @Synchronized
    fun recordRecovery(code: String) {
        if (!ExperimentalSelfHealingSettings.isEnabled(app)) return
        val key = code.trim().uppercase(Locale.ROOT).take(64).ifBlank { "UNKNOWN" }
        val counts = runCatching { JSONObject(prefs.getString(KEY_RECOVERIES, "{}") ?: "{}") }
            .getOrDefault(JSONObject())
        counts.put(key, (counts.optInt(key, 0) + 1).coerceAtMost(999))
        prefs.edit().putString(KEY_RECOVERIES, counts.toString())
            .putString(KEY_LAST_RECOVERY, key).apply()
    }

    fun summary(): String {
        if (!ExperimentalSelfHealingSettings.isEnabled(app)) return "Experimental repair memory is disabled."
        val failure = prefs.getString(KEY_LAST_FAILURE, null)
        val recovery = prefs.getString(KEY_LAST_RECOVERY, null)
        return buildString {
            append("Experimental repair memory enabled.")
            if (!failure.isNullOrBlank()) append(" Last failure: ").append(failure).append(".")
            if (!recovery.isNullOrBlank()) append(" Last recovered: ").append(recovery).append(".")
        }
    }

    companion object {
        private const val PREFS_NAME = "jarvis_repair_memory_v1"
        private const val KEY_ALIASES = "verified_aliases"
        private const val KEY_FAILURES = "failure_counts"
        private const val KEY_RECOVERIES = "recovery_counts"
        private const val KEY_LAST_FAILURE = "last_failure"
        private const val KEY_LAST_RECOVERY = "last_recovery"
        private const val MAX_ALIASES = 100
    }
}

object RepairMemoryPolicy {
    private val sensitive = Regex(
        """(?i)(?:https?://|www\.|[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}|\b(?:\+?\d[\d -]{7,}\d)\b|\b(?:otp|password|passcode|upi|bank|account|card|message|send|call|contact)\b)"""
    )

    fun commandKey(command: String): String {
        val normalized = command.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(normalized.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun canRemember(original: String, corrected: String, verified: Boolean): Boolean {
        val key = original.trim()
        val value = corrected.trim()
        return verified && key.isNotBlank() && value.isNotBlank() &&
            key.length <= 200 && value.length <= 120 &&
            key.lowercase(Locale.ROOT) != value.lowercase(Locale.ROOT) &&
            !sensitive.containsMatchIn(key) && !sensitive.containsMatchIn(value) &&
            value.matches(Regex("(?i)^(press (back|home|recents)|open [a-z0-9 ._-]{1,40})$"))
    }
}
