package com.openjarvis.agent

import android.content.Context
import java.security.MessageDigest
import java.util.Locale

/** Persistent retry-claim gate. It never executes or replays device actions. */
class SelfHealingEngine(context: Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun claimRetry(command: String, errorCode: String, retryable: Boolean, actionsStarted: Boolean): Boolean {
        if (!ExperimentalSelfHealingSettings.isEnabled(app) || command.isBlank() || errorCode.isBlank()) return false
        val commandKey = digest(command.trim().lowercase(Locale.ROOT))
        val codeKey = digest(errorCode.trim().uppercase(Locale.ROOT))
        val key = "retry_${commandKey}_${codeKey}"
        val now = System.currentTimeMillis()
        val lastAttempt = prefs.getLong(key, 0L)
        val elapsed = if (lastAttempt <= 0L) Long.MAX_VALUE else (now - lastAttempt).coerceAtLeast(0L)
        if (!SelfHealingRetryPolicy.canClaim(retryable, actionsStarted, lastAttempt > 0L, elapsed)) return false
        prefs.edit().putLong(key, now)
            .putString(KEY_LAST_RETRY, "${errorCode.uppercase(Locale.ROOT).take(64)}#${commandKey.take(8)}")
            .apply()
        return true
    }

    @Synchronized
    fun recordSuccess(command: String) {
        if (!ExperimentalSelfHealingSettings.isEnabled(app)) return
        val prefix = "retry_${digest(command.trim().lowercase(Locale.ROOT))}_"
        val editor = prefs.edit()
        prefs.all.keys.filter { it.startsWith(prefix) }.forEach(editor::remove)
        editor.putString(KEY_LAST_SUCCESS, System.currentTimeMillis().toString()).apply()
    }

    fun summary(): String {
        if (!ExperimentalSelfHealingSettings.isEnabled(app)) return "Experimental self-healing is disabled."
        val retry = prefs.getString(KEY_LAST_RETRY, null)
        val success = prefs.getString(KEY_LAST_SUCCESS, null)
        return buildString {
            append("Experimental retry guard enabled; device actions are never replayed.")
            if (!retry.isNullOrBlank()) append(" Last retry claim: ").append(retry).append(".")
            if (!success.isNullOrBlank()) append(" Last success timestamp: ").append(success).append(".")
        }
    }

    private fun digest(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    companion object {
        private const val PREFS_NAME = "jarvis_self_healing_v1"
        private const val KEY_LAST_RETRY = "last_retry"
        private const val KEY_LAST_SUCCESS = "last_success"
    }
}

object SelfHealingRetryPolicy {
    const val COOLDOWN_MS = 10 * 60 * 1000L

    fun canClaim(
        retryable: Boolean,
        actionsStarted: Boolean,
        alreadyClaimed: Boolean,
        elapsedSinceLastMs: Long,
        cooldownMs: Long = COOLDOWN_MS
    ): Boolean = retryable && !actionsStarted && !alreadyClaimed &&
        elapsedSinceLastMs >= cooldownMs.coerceAtLeast(0L)
}
