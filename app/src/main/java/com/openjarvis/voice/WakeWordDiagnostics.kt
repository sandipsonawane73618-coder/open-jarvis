package com.openjarvis.voice

import android.content.Context
import android.os.Build
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.io.File
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean

data class WakeWordDiagnosticSnapshot(
    val engineName: String = "Voicute ONNX KWS",
    val state: String = "NOT_STARTED",
    val micPermissionGranted: Boolean = false,
    val rms: Float = 0f,
    val peakLevel: Float = 0f,
    val framesProcessed: Long = 0L,
    val silentFrames: Long = 0L,
    val clippedSamples: Long = 0L,
    val agcAvailable: Boolean = false,
    val agcEnabled: Boolean = false,
    val noiseSuppressorAvailable: Boolean = false,
    val noiseSuppressorEnabled: Boolean = false,
    val audioSource: String = "unknown",
    val sampleRate: Int = 0,
    val lastDetectionAtMs: Long? = null,
    val lastDetectionKeyword: String? = null,
    val lastDetectionScore: Float? = null,
    val keywords: List<String> = emptyList(),
    val threshold: Float = 0.25f,
    val keywordBoost: Float = 1.5f,
    val lastError: String? = null,
    val lastCandidateScores: String? = null,
    val candidateFrames: Long = 0L
)

data class JarvisDiagnosticEvent(val atMs: Long, val level: String, val message: String)

object WakeWordDiagnostics {
    private const val SILENCE_RMS_THRESHOLD = 0.003f
    private const val MAX_EVENTS = 300
    private const val MAX_INFERENCE_RECORDS = 2000
    @Volatile private var current = WakeWordDiagnosticSnapshot()
    private val events = ArrayList<JarvisDiagnosticEvent>(MAX_EVENTS)
    private val inferenceRecords = ArrayList<String>(MAX_INFERENCE_RECORDS)
    @Volatile private var inferenceFile: File? = null
    @Volatile private var logFile: File? = null
    private val crashHandlerInstalled = AtomicBoolean(false)

    @Synchronized
    fun initialize(context: Context) {
        if (logFile != null) return
        val file = File(context.noBackupFilesDir, "jarvis_diagnostics.log")
        logFile = file
        inferenceFile = File(context.noBackupFilesDir, "wake_inference_records.jsonl")
        runCatching {
            inferenceFile?.let { records ->
                if (records.exists()) records.readLines().takeLast(MAX_INFERENCE_RECORDS).forEach { line ->
                    if (line.isNotBlank()) inferenceRecords.add(line)
                }
            }
        }
        runCatching {
            if (file.exists()) {
                file.readLines().takeLast(MAX_EVENTS).forEach { line ->
                    val parts = line.split("\t", limit = 3)
                    if (parts.size == 3) {
                        val at = parts[0].toLongOrNull() ?: System.currentTimeMillis()
                        events.add(JarvisDiagnosticEvent(at, parts[1], parts[2]))
                    }
                }
            }
        }
        if (crashHandlerInstalled.compareAndSet(false, true)) {
            val previous = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, error ->
                val trace = error.stackTraceToString().take(10000).replace("\n", " ")
                failed("UNCAUGHT CRASH on " + thread.name + ": " + error.javaClass.simpleName + ": " + trace)
                previous?.uncaughtException(thread, error)
            }
        }
    }

    @Synchronized fun snapshot(): WakeWordDiagnosticSnapshot = current
    @Synchronized fun recentEvents(): List<JarvisDiagnosticEvent> = events.toList()

    @Synchronized private fun addEvent(level: String, message: String) {
        val now = System.currentTimeMillis()
        val safeMessage = message.replace("\n", " ").replace("\r", " ").take(12000)
        events.add(JarvisDiagnosticEvent(now, level, safeMessage.take(500)))
        if (events.size > MAX_EVENTS) events.removeAt(0)
        runCatching {
            logFile?.appendText(now.toString() + "\t" + level + "\t" + safeMessage + "\n")
            logFile?.let { file ->
                if (file.length() > 256 * 1024) {
                    val retained = file.readLines().takeLast(MAX_EVENTS)
                    file.writeText(retained.joinToString("\n", postfix = if (retained.isEmpty()) "" else "\n"))
                }
            }
        }
    }

    @Synchronized
    fun started(permissionGranted: Boolean, keywords: List<String>, threshold: Float, boost: Float, engineName: String = "Voicute ONNX KWS") {
        current = current.copy(engineName = engineName, state = "LISTENING", micPermissionGranted = permissionGranted,
            rms = 0f, peakLevel = 0f, framesProcessed = 0L, silentFrames = 0L, clippedSamples = 0L,
            agcAvailable = false, agcEnabled = false, noiseSuppressorAvailable = false,
            noiseSuppressorEnabled = false, audioSource = "unknown", sampleRate = 0,
            lastDetectionAtMs = null, lastDetectionKeyword = null, lastDetectionScore = null,
            keywords = keywords, threshold = threshold, keywordBoost = boost, lastError = null,
            lastCandidateScores = null, candidateFrames = 0L)
        addEvent("INFO", "Wake listener started; permission=" + permissionGranted + "; phrases=" + keywords.joinToString() + "; threshold=" + threshold)
    }

    @Synchronized
    fun audioConfiguration(agcAvailable: Boolean, agcEnabled: Boolean, noiseSuppressorAvailable: Boolean,
                           noiseSuppressorEnabled: Boolean, audioSource: String, sampleRate: Int) {
        current = current.copy(agcAvailable = agcAvailable, agcEnabled = agcEnabled,
            noiseSuppressorAvailable = noiseSuppressorAvailable, noiseSuppressorEnabled = noiseSuppressorEnabled,
            audioSource = audioSource, sampleRate = sampleRate)
        addEvent("INFO", "Audio config: source=" + audioSource + " rate=" + sampleRate + " AGC=" + agcEnabled + "/" + agcAvailable + " NS=" + noiseSuppressorEnabled + "/" + noiseSuppressorAvailable)
    }

    @Synchronized
    fun audioFrame(level: Float, peak: Float, clipped: Int) {
        current = current.copy(state = if (current.state == "ERROR") "ERROR" else "LISTENING",
            rms = level.coerceIn(0f, 1f), peakLevel = peak.coerceIn(0f, 1f),
            framesProcessed = current.framesProcessed + 1L,
            silentFrames = current.silentFrames + if (level < SILENCE_RMS_THRESHOLD) 1L else 0L,
            clippedSamples = current.clippedSamples + clipped.coerceAtLeast(0))
    }

    @Synchronized
    fun candidate(scores: String, threshold: Float, rms: Float, accepted: Boolean) {
        current = current.copy(lastCandidateScores = scores, candidateFrames = current.candidateFrames + 1L)
        addEvent(if (accepted) "MATCH" else "SCORE", "scores={" + scores + "}; threshold=" + threshold + "; rms=" + String.format(Locale.US, "%.4f", rms) + "; accepted=" + accepted)
    }

    /** One metadata-only JSONL record for every Voicute model inference. No PCM/audio is stored. */
    @Synchronized
    fun inferenceRecord(scoresSummary: String?, threshold: Float, rms: Float, peak: Float, acceptedPhrase: String?) {
        val s = current
        val now = System.currentTimeMillis()
        val scores = JSONObject()
        current.keywords.forEach { scores.put(it, JSONObject.NULL) }
        scoresSummary.orEmpty().split(",").forEach { item ->
            val pair = item.trim().split("=", limit = 2)
            if (pair.size == 2) pair[1].toFloatOrNull()?.let { scores.put(pair[0], it.toDouble()) }
        }
        val record = JSONObject().apply {
            put("timestampMs", now)
            put("engine", s.engineName)
            put("scores", scores)
            put("threshold", threshold.toDouble())
            put("rms", rms.toDouble())
            put("peak", peak.toDouble())
            put("audioSource", s.audioSource)
            put("sampleRate", s.sampleRate)
            put("agcAvailable", s.agcAvailable)
            put("agcEnabled", s.agcEnabled)
            put("noiseSuppressorAvailable", s.noiseSuppressorAvailable)
            put("noiseSuppressorEnabled", s.noiseSuppressorEnabled)
            put("accepted", !acceptedPhrase.isNullOrBlank())
            put("matchedPhrase", acceptedPhrase ?: JSONObject.NULL)
        }.toString()
        inferenceRecords.add(record)
        while (inferenceRecords.size > MAX_INFERENCE_RECORDS) inferenceRecords.removeAt(0)
        runCatching {
            val file = inferenceFile ?: return@runCatching
            file.appendText(record + "\n")
            if (file.length() > 1024 * 1024) {
                file.writeText(inferenceRecords.joinToString("\n", postfix = if (inferenceRecords.isEmpty()) "" else "\n"))
            }
        }
        current = current.copy(lastCandidateScores = scoresSummary)
    }

    @Synchronized
    fun inferenceRecordLines(): List<String> = inferenceRecords.toList()

    @Synchronized
    fun detected(keyword: String, score: Float? = null) {
        current = current.copy(state = "DETECTED", lastDetectionAtMs = System.currentTimeMillis(),
            lastDetectionKeyword = keyword, lastDetectionScore = score)
        addEvent("MATCH", "Wake word accepted: " + keyword + " score=" + (score?.let { String.format(Locale.US, "%.4f", it) } ?: "n/a"))
    }

    @Synchronized
    fun record(level: String, message: String) {
        addEvent(level, message)
    }

    @Synchronized
    fun failed(message: String) {
        current = current.copy(state = "ERROR", lastError = message)
        addEvent("ERROR", message)
    }

    @Synchronized
    fun stopped() {
        current = current.copy(state = "STOPPED", rms = 0f, peakLevel = 0f)
        addEvent("INFO", "Wake listener stopped")
    }

    @Synchronized
    fun buildReport(context: Context): String {
        val s = current
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS Z", Locale.US).format(Date())
        val version = runCatching {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            info.versionName + " (" + info.versionCode + ")"
        }.getOrDefault("unknown")
        val out = StringBuilder()
        out.appendLine("JARVIS DIAGNOSTIC REPORT")
        out.appendLine("Generated: " + stamp)
        out.appendLine("App: " + version)
        out.appendLine("Device: " + Build.MANUFACTURER + " " + Build.MODEL)
        out.appendLine("Android: " + Build.VERSION.RELEASE + " (SDK " + Build.VERSION.SDK_INT + ")")
        out.appendLine("Engine: " + s.engineName)
        out.appendLine("State: " + s.state)
        out.appendLine("Mic permission: " + s.micPermissionGranted)
        out.appendLine("Audio: source=" + s.audioSource + ", sampleRate=" + s.sampleRate)
        out.appendLine("Signal: rms=" + String.format(Locale.US, "%.5f", s.rms) + ", peak=" + String.format(Locale.US, "%.5f", s.peakLevel) + ", frames=" + s.framesProcessed + ", silentFrames=" + s.silentFrames + ", clippedSamples=" + s.clippedSamples)
        out.appendLine("Effects: AGC available=" + s.agcAvailable + ", enabled=" + s.agcEnabled + "; NoiseSuppressor available=" + s.noiseSuppressorAvailable + ", enabled=" + s.noiseSuppressorEnabled)
        out.appendLine("Phrases: " + s.keywords.joinToString())
        out.appendLine("Threshold: " + s.threshold + "; keywordBoost=" + s.keywordBoost)
        out.appendLine("Last candidate scores: " + (s.lastCandidateScores ?: "unavailable"))
        out.appendLine("Candidate frames recorded: " + s.candidateFrames)
        out.appendLine("Last accepted detection: " + (s.lastDetectionKeyword ?: "never") + "; score=" + (s.lastDetectionScore?.toString() ?: "unavailable") + "; at=" + (s.lastDetectionAtMs?.toString() ?: "n/a"))
        out.appendLine("Last error: " + (s.lastError ?: "none"))
        out.appendLine()
        out.appendLine("STRUCTURED WAKE INFERENCE RECORDS (newest at bottom; max " + MAX_INFERENCE_RECORDS + ")")
        inferenceRecordLines().forEach { out.appendLine(it) }
        out.appendLine()
        out.appendLine("RECENT EVENTS (newest at bottom; max " + MAX_EVENTS + ")")
        recentEvents().forEach { event ->
            val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date(event.atMs))
            out.appendLine("[" + time + "] [" + event.level + "] " + event.message)
        }
        out.appendLine()
        out.appendLine("Privacy: no microphone audio or speech recordings are included.")
        return out.toString()
    }
}