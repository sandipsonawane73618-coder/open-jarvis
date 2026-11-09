package com.openjarvis.diagnostics

import android.content.Context
import android.os.Build
import com.openjarvis.BuildConfig
import com.openjarvis.voice.WakeWordDiagnosticSnapshot
import com.openjarvis.voice.WakeWordDiagnostics
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal data class LocalDiagnosticEvent(
    val timestampMs: Long,
    val category: String,
    val message: String,
    val stackTrace: String = ""
)

/** Bounded, app-private diagnostic history. No upload or sharing occurs here. */
object JarvisDiagnosticLog {
    private const val PREFS_NAME = "jarvis_local_diagnostic_log"
    private const val EVENTS_KEY = "events_json"
    private const val MAX_EVENTS = 160
    private const val MAX_MESSAGE_CHARS = 1_200
    private const val MAX_STACK_CHARS = 3_000
    private const val MAX_EXPORT_CHARS = 80_000
    private val lock = Any()
    private val lastCandidateWrittenAt = HashMap<String, Long>()

    fun record(context: Context, category: String, message: String, error: Throwable? = null) {
        val event = LocalDiagnosticEvent(
            timestampMs = System.currentTimeMillis(),
            category = category.uppercase(Locale.US).filter { it.isLetterOrDigit() || it == '_' }.take(32),
            message = DiagnosticRedactor.redact(message).take(MAX_MESSAGE_CHARS),
            stackTrace = error?.let { DiagnosticRedactor.stackTrace(it).take(MAX_STACK_CHARS) }.orEmpty()
        )
        synchronized(lock) {
            val current = readEventsLocked(context)
            current.add(event)
            writeEventsLocked(context, retainNewest(current, MAX_EVENTS))
        }
    }

    /** Records model confidence even when the Voicute gates reject a candidate. */
    fun recordCandidate(
        context: Context,
        keyword: String?,
        score: Float,
        threshold: Float,
        accepted: Boolean,
        rejectionReason: String,
        consecutiveFrames: Int,
        gateCode: Int,
        peak: Float = 0f,
        background: Float = 0f,
        preRmsRaw: Float = -1f,
        timestampMs: Long = System.currentTimeMillis()
    ) {
        val message = formatCandidateMessage(
            keyword, score, threshold, accepted, rejectionReason, consecutiveFrames, gateCode,
            peak, background, preRmsRaw
        )
        val key = message.substringBefore(' ')
        val shouldWrite = synchronized(lock) {
            val previous = lastCandidateWrittenAt[key] ?: 0L
            if (accepted || timestampMs - previous >= 500L) {
                lastCandidateWrittenAt[key] = timestampMs
                true
            } else false
        }
        if (shouldWrite) record(context, "WAKE_CANDIDATE", message)
    }

    internal fun events(context: Context): List<LocalDiagnosticEvent> = synchronized(lock) {
        readEventsLocked(context).toList()
    }

    fun eventCount(context: Context): Int = events(context).size

    fun clear(context: Context) {
        synchronized(lock) {
            context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().remove(EVENTS_KEY).apply()
            context.applicationContext.getSharedPreferences("jarvis_agent_diagnostics", Context.MODE_PRIVATE)
                .edit().clear().apply()
            context.applicationContext.getSharedPreferences("ollama_diagnostics", Context.MODE_PRIVATE)
                .edit().remove("recent_requests").apply()
            lastCandidateWrittenAt.clear()
        }
    }

    fun buildReport(
        context: Context,
        wake: WakeWordDiagnosticSnapshot = WakeWordDiagnostics.snapshot()
    ): String {
        val generated = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
        val history = events(context)
        val header = buildString {
            appendLine("OPEN JARVIS — LOCAL DIAGNOSTIC EXPORT")
            appendLine("Generated: $generated")
            appendLine("App version: ${BuildConfig.VERSION_NAME} (versionCode=${BuildConfig.VERSION_CODE})")
            appendLine("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            appendLine("Privacy: no microphone audio, transcripts, prompts, commands, contacts, tokens or API keys")
            appendLine()
            appendLine("[SERVICE / WATCHDOG]")
            appendLine("serviceState=${wake.serviceState}, overlayAttached=${wake.overlayAttached}, wakeWordRunning=${wake.wakeWordRunning}")
            appendLine("watchdogLastTick=${formatTime(wake.watchdogLastTickAtMs)}, watchdogTicks=${wake.watchdogTickCount}, watchdogRecoveries=${wake.watchdogRecoveryCount}")
            appendLine("wakeWordRestartAttempt=${wake.wakeWordRestartAttempt}")
            appendLine()
            appendLine("[MICROPHONE / AUDIO]")
            appendLine("permissionGranted=${wake.micPermissionGranted}, source=${wake.audioSource}, sampleRate=${wake.sampleRate}")
            appendLine("channel=${wake.channelConfig}, encoding=${wake.encoding}, minBufferBytes=${wake.minBufferSizeBytes}, bufferBytes=${wake.bufferSizeBytes}")
            appendLine("recorderInitialized=${wake.recorderInitialized}, recordingState=${wake.recordingState}, clientSilenced=${wake.microphoneSilenced}")
            appendLine("AGC available=${wake.agcAvailable}, enabled=${wake.agcEnabled}; NoiseSuppressor available=${wake.noiseSuppressorAvailable}, enabled=${wake.noiseSuppressorEnabled}")
            appendLine("frames=${wake.framesProcessed}, silentFrames=${wake.silentFrames}, clippedSamples=${wake.clippedSamples}, rms=${fmt(wake.rms)}, peak=${fmt(wake.peakLevel)}")
            appendLine()
            appendLine("[WAKE WORD MODEL]")
            appendLine("engine=${wake.engineName}, phrases=${wake.keywords.joinToString(" / ")}, threshold=${fmt(wake.threshold)}, boost=${fmt(wake.keywordBoost)}")
            appendLine("lastCandidateAt=${formatTime(wake.lastCandidateAtMs)}, candidate=${wake.lastCandidateKeyword ?: "none"}, score=${wake.lastCandidateScore?.let(::fmt) ?: "n/a"}, accepted=${wake.lastCandidateAccepted}, rejection=${wake.lastCandidateRejectionReason ?: "n/a"}")
            appendLine("lastAcceptedDetectionAt=${formatTime(wake.lastDetectionAtMs)}, keyword=${wake.lastDetectionKeyword ?: "none"}, score=${wake.lastDetectionScore?.let(::fmt) ?: "n/a"}")
            appendLine("lastWakeWordError=${wake.lastError ?: "none"}")
            appendLine()
            appendLine("[RECENT LOCAL EVENTS — newest last]")
        }
        val blocks = history.takeLast(100).map { event ->
            buildString {
                appendLine("${formatTime(event.timestampMs)} [${event.category}] ${event.message}")
                if (event.stackTrace.isNotBlank()) {
                    appendLine("Stack trace:")
                    appendLine(event.stackTrace)
                }
            }
        }
        val selected = ArrayList<String>()
        var remaining = (MAX_EXPORT_CHARS - header.length).coerceAtLeast(0)
        for (block in blocks.asReversed()) {
            if (block.length > remaining) break
            selected.add(block)
            remaining -= block.length
        }
        return header + selected.asReversed().joinToString("")
    }

    internal fun formatCandidateMessage(
        keyword: String?,
        score: Float,
        threshold: Float,
        accepted: Boolean,
        rejectionReason: String,
        consecutiveFrames: Int,
        gateCode: Int,
        peak: Float = 0f,
        background: Float = 0f,
        preRmsRaw: Float = -1f
    ): String {
        val safeKeyword = when (keyword?.trim()?.lowercase(Locale.US)) {
            "jarvis" -> "Jarvis"
            "hey jarvis" -> "Hey Jarvis"
            "hi jarvis" -> "Hi Jarvis"
            null, "" -> "unknown"
            else -> "other"
        }
        val safeReason = rejectionReason.filter { it.isLetterOrDigit() || it == '_' || it == '-' }.take(48)
        return String.format(
            Locale.US,
            "keyword=%s score=%.4f threshold=%.4f accepted=%s rejection=%s consecutiveFrames=%d gateCode=%d peak=%.4f background=%.4f preRmsRaw=%.1f",
            safeKeyword,
            score.coerceIn(0f, 1f),
            threshold.coerceIn(0f, 1f),
            accepted,
            safeReason.ifBlank { "unspecified" },
            consecutiveFrames.coerceAtLeast(0),
            gateCode,
            peak.coerceIn(0f, 1f),
            background.coerceIn(0f, 1f),
            preRmsRaw
        )
    }

    internal fun <T> retainNewest(items: List<T>, maxItems: Int): List<T> =
        if (maxItems <= 0) emptyList() else items.takeLast(maxItems)

    private fun readEventsLocked(context: Context): MutableList<LocalDiagnosticEvent> {
        val raw = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(EVENTS_KEY, null) ?: return mutableListOf()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    add(LocalDiagnosticEvent(
                        timestampMs = item.optLong("at", 0L),
                        category = item.optString("category", "UNKNOWN"),
                        message = item.optString("message", ""),
                        stackTrace = item.optString("stack", "")
                    ))
                }
            }.toMutableList()
        }.getOrDefault(mutableListOf())
    }

    private fun writeEventsLocked(context: Context, events: List<LocalDiagnosticEvent>) {
        val array = JSONArray()
        events.forEach { event ->
            array.put(JSONObject()
                .put("at", event.timestampMs)
                .put("category", event.category)
                .put("message", event.message)
                .put("stack", event.stackTrace))
        }
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(EVENTS_KEY, array.toString()).apply()
    }

    private fun formatTime(timestampMs: Long?): String =
        timestampMs?.let { SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date(it)) } ?: "n/a"

    private fun fmt(value: Float): String = String.format(Locale.US, "%.4f", value)
}

internal object DiagnosticRedactor {
    private val secretAssignment = Regex(
        """(?i)\b(api[_-]?key|access[_-]?token|refresh[_-]?token|id[_-]?token|authorization|password|passwd|client[_-]?secret|secret|bridge[_-]?token)\b(\s*[:=]\s*)([^\s,;]+)"""
    )
    private val bearer = Regex("""(?i)\bBearer\s+[A-Za-z0-9._~+/=-]+""")
    private val url = Regex("""(?i)https?://[^\s)\]]+""")
    private val email = Regex("""(?i)\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}\b""")
    private val indiaPhone = Regex("""(?<!\d)(?:\+?91[- ]?)?[6-9]\d{9}(?!\d)""")
    private val jwt = Regex("""\b[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}\b""")

    fun stackTrace(error: Throwable): String {
        val output = StringBuilder()
        val seen = HashSet<Throwable>()
        var current: Throwable? = error
        var depth = 0
        while (current != null && depth < 8 && seen.add(current)) {
            if (depth == 0) output.append(current.javaClass.name)
            else output.append("\nCaused by: ").append(current.javaClass.name)
            current.stackTrace.take(80).forEach { frame ->
                output.append("\n at ").append(frame.toString())
            }
            current = current.cause
            depth++
        }
        return redact(output.toString())
    }

    fun redact(input: String): String {
        var safe = input.replace("\u0000", "")
        safe = bearer.replace(safe, "Bearer [REDACTED]")
        safe = secretAssignment.replace(safe) { "${it.groupValues[1]}${it.groupValues[2]}[REDACTED]" }
        safe = jwt.replace(safe, "[REDACTED_TOKEN]")
        safe = url.replace(safe, "[URL_REDACTED]")
        safe = email.replace(safe, "[EMAIL_REDACTED]")
        safe = indiaPhone.replace(safe, "[PHONE_REDACTED]")
        return safe.take(6_000)
    }
}
