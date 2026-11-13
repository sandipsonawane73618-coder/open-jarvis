package com.openjarvis.commandlab

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class CommandLogEntry(
    val id: String,
    val timeMillis: Long,
    val commandText: String,
    val sttConfidence: Float?,
    val language: String,
    val route: String,
    val source: String,
    val result: String? = null,
    val failReason: String? = null
)

object CommandLogPolicy {
    private val sensitiveMarker = Regex(
        "(?i)\\b(password|passcode|otp|one[- ]time(?:\\s+(?:password|code))?|verification code|pin|upi pin|cvv|cvc|security code)\\b"
    )
    private val email = Regex("(?i)\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\b")
    private val phone = Regex("(?<!\\d)(?:\\+?91[-\\s]?)?[6-9]\\d{9}(?!\\d)")

    fun redact(value: String?): String? {
        if (value == null) return null
        if (sensitiveMarker.containsMatchIn(value)) return "[REDACTED: sensitive content]"
        return value
            .replace(email, "[REDACTED: email]")
            .replace(phone, "[REDACTED: phone]")
            .take(500)
    }

    fun keepLast100(entries: List<CommandLogEntry>): List<CommandLogEntry> =
        entries.takeLast(100)
}

class CommandLogStore(context: Context) {
    companion object {
        private const val PREFS = "command_lab_encrypted"
        private const val KEY_ENTRIES = "entries_json"
        private const val MAX_ENTRIES = 100
    }

    private val prefs = EncryptedSharedPreferences.create(
        context,
        PREFS,
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    @Synchronized
    fun record(
        command: String,
        confidence: Float?,
        language: String,
        route: String,
        source: String
    ): String {
        val id = UUID.randomUUID().toString()
        val entry = CommandLogEntry(
            id = id,
            timeMillis = System.currentTimeMillis(),
            commandText = CommandLogPolicy.redact(command).orEmpty(),
            sttConfidence = confidence?.takeIf { it.isFinite() }?.coerceIn(0f, 1f),
            language = language.take(24),
            route = route.take(24),
            source = source.take(12)
        )
        save(CommandLogPolicy.keepLast100(read() + entry))
        return id
    }

    @Synchronized
    fun complete(id: String?, result: String?, failReason: String? = null) {
        if (id.isNullOrBlank()) return
        val updated = read().map { entry ->
            if (entry.id != id) entry else entry.copy(
                result = CommandLogPolicy.redact(result),
                failReason = CommandLogPolicy.redact(failReason)
            )
        }
        save(CommandLogPolicy.keepLast100(updated))
    }

    @Synchronized
    fun entries(): List<CommandLogEntry> = read()

    fun shareReport(): String = buildString {
        appendLine("Jarvis Command Lab — redacted report")
        appendLine("Entries: ${entries().size}/100")
        entries().forEach { entry ->
            appendLine()
            appendLine("Time: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date(entry.timeMillis))}")
            appendLine("Source: ${entry.source} • Language: ${entry.language} • Route: ${entry.route}")
            appendLine("STT confidence: ${entry.sttConfidence?.toString() ?: "unavailable"}")
            appendLine("Command: ${CommandLogPolicy.redact(entry.commandText) ?: ""}")
            appendLine("Result: ${CommandLogPolicy.redact(entry.result) ?: "pending"}")
            appendLine("Fail reason: ${CommandLogPolicy.redact(entry.failReason) ?: "—"}")
        }
    }

    private fun read(): List<CommandLogEntry> = runCatching {
        val json = JSONArray(prefs.getString(KEY_ENTRIES, "[]").orEmpty())
        buildList {
            for (index in 0 until json.length()) {
                val item = json.optJSONObject(index) ?: continue
                val confidence = if (item.isNull("confidence")) null else item.optDouble("confidence").toFloat()
                add(
                    CommandLogEntry(
                        id = item.optString("id"),
                        timeMillis = item.optLong("time"),
                        commandText = item.optString("command"),
                        sttConfidence = confidence,
                        language = item.optString("language", "unknown"),
                        route = item.optString("route", "unknown"),
                        source = item.optString("source", "typed"),
                        result = item.optString("result").takeUnless { it == "null" || it.isBlank() },
                        failReason = item.optString("failReason").takeUnless { it == "null" || it.isBlank() }
                    )
                )
            }
        }
    }.getOrDefault(emptyList())

    private fun save(entries: List<CommandLogEntry>) {
        val json = JSONArray()
        CommandLogPolicy.keepLast100(entries).forEach { entry ->
            json.put(
                JSONObject().apply {
                    put("id", entry.id)
                    put("time", entry.timeMillis)
                    put("command", CommandLogPolicy.redact(entry.commandText))
                    put("confidence", entry.sttConfidence ?: JSONObject.NULL)
                    put("language", entry.language)
                    put("route", entry.route)
                    put("source", entry.source)
                    put("result", CommandLogPolicy.redact(entry.result) ?: JSONObject.NULL)
                    put("failReason", CommandLogPolicy.redact(entry.failReason) ?: JSONObject.NULL)
                }
            )
        }
        prefs.edit().putString(KEY_ENTRIES, json.toString()).apply()
    }
}
