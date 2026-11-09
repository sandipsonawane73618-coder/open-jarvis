package com.openjarvis.voice

import com.openjarvis.diagnostics.DiagnosticRedactor

/**
 * Current in-process wake-word/audio/service snapshot. Candidate history and error
 * stack traces are held separately in JarvisDiagnosticLog's app-private storage.
 */
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
    val channelConfig: String = "unknown",
    val encoding: String = "unknown",
    val minBufferSizeBytes: Int = 0,
    val bufferSizeBytes: Int = 0,
    val recorderInitialized: Boolean = false,
    val recordingState: String = "unknown",
    val microphoneSilenced: Boolean = false,
    val lastDetectionAtMs: Long? = null,
    val lastDetectionKeyword: String? = null,
    val lastDetectionScore: Float? = null,
    val lastCandidateAtMs: Long? = null,
    val lastCandidateKeyword: String? = null,
    val lastCandidateScore: Float? = null,
    val lastCandidateAccepted: Boolean = false,
    val lastCandidateRejectionReason: String? = null,
    val keywords: List<String> = emptyList(),
    val threshold: Float = 0.25f,
    val keywordBoost: Float = 1.5f,
    val lastError: String? = null,
    val serviceState: String = "UNKNOWN",
    val overlayAttached: Boolean = false,
    val wakeWordRunning: Boolean = false,
    val watchdogLastTickAtMs: Long? = null,
    val watchdogTickCount: Long = 0L,
    val watchdogRecoveryCount: Long = 0L,
    val wakeWordRestartAttempt: Int = 0
)

object WakeWordDiagnostics {
    private const val SILENCE_RMS_THRESHOLD = 0.003f
    @Volatile private var current = WakeWordDiagnosticSnapshot()

    fun snapshot(): WakeWordDiagnosticSnapshot = current

    @Synchronized
    fun started(
        permissionGranted: Boolean,
        keywords: List<String>,
        threshold: Float,
        boost: Float,
        engineName: String = "Voicute ONNX KWS"
    ) {
        current = current.copy(
            engineName = engineName,
            state = "LISTENING",
            micPermissionGranted = permissionGranted,
            rms = 0f,
            peakLevel = 0f,
            framesProcessed = 0L,
            silentFrames = 0L,
            clippedSamples = 0L,
            agcAvailable = false,
            agcEnabled = false,
            noiseSuppressorAvailable = false,
            noiseSuppressorEnabled = false,
            audioSource = "unknown",
            sampleRate = 0,
            channelConfig = "unknown",
            encoding = "unknown",
            minBufferSizeBytes = 0,
            bufferSizeBytes = 0,
            recorderInitialized = false,
            recordingState = "unknown",
            microphoneSilenced = false,
            lastDetectionAtMs = null,
            lastDetectionKeyword = null,
            lastDetectionScore = null,
            lastCandidateAtMs = null,
            lastCandidateKeyword = null,
            lastCandidateScore = null,
            lastCandidateAccepted = false,
            lastCandidateRejectionReason = null,
            keywords = keywords,
            threshold = threshold,
            keywordBoost = boost,
            lastError = null,
            wakeWordRunning = true
        )
    }

    @Synchronized
    fun audioConfiguration(
        agcAvailable: Boolean,
        agcEnabled: Boolean,
        noiseSuppressorAvailable: Boolean,
        noiseSuppressorEnabled: Boolean,
        audioSource: String,
        sampleRate: Int,
        channelConfig: String = "MONO",
        encoding: String = "PCM_16BIT",
        minBufferSizeBytes: Int = 0,
        bufferSizeBytes: Int = 0,
        recorderInitialized: Boolean = true,
        recordingState: String = "RECORDING",
        microphoneSilenced: Boolean = false
    ) {
        current = current.copy(
            agcAvailable = agcAvailable,
            agcEnabled = agcEnabled,
            noiseSuppressorAvailable = noiseSuppressorAvailable,
            noiseSuppressorEnabled = noiseSuppressorEnabled,
            audioSource = audioSource,
            sampleRate = sampleRate,
            channelConfig = channelConfig,
            encoding = encoding,
            minBufferSizeBytes = minBufferSizeBytes,
            bufferSizeBytes = bufferSizeBytes,
            recorderInitialized = recorderInitialized,
            recordingState = recordingState,
            microphoneSilenced = microphoneSilenced
        )
    }

    @Synchronized
    fun audioFrame(level: Float, peak: Float, clipped: Int) {
        current = current.copy(
            state = "LISTENING",
            rms = level.coerceIn(0f, 1f),
            peakLevel = peak.coerceIn(0f, 1f),
            framesProcessed = current.framesProcessed + 1L,
            silentFrames = current.silentFrames + if (level < SILENCE_RMS_THRESHOLD) 1L else 0L,
            clippedSamples = current.clippedSamples + clipped.coerceAtLeast(0)
        )
    }

    @Synchronized
    fun candidate(
        keyword: String?,
        score: Float,
        threshold: Float,
        accepted: Boolean,
        rejectionReason: String,
        atMs: Long = System.currentTimeMillis()
    ) {
        val safeKeyword = when (keyword?.trim()?.lowercase()) {
            "jarvis" -> "Jarvis"
            "hey jarvis" -> "Hey Jarvis"
            "hi jarvis" -> "Hi Jarvis"
            null, "" -> "unknown"
            else -> "other"
        }
        current = current.copy(
            lastCandidateAtMs = atMs,
            lastCandidateKeyword = safeKeyword,
            lastCandidateScore = score.coerceIn(0f, 1f),
            lastCandidateAccepted = accepted,
            lastCandidateRejectionReason = rejectionReason.take(64)
        )
    }

    @Synchronized
    fun detected(keyword: String, score: Float? = null) {
        current = current.copy(
            state = "DETECTED",
            lastDetectionAtMs = System.currentTimeMillis(),
            lastDetectionKeyword = keyword,
            lastDetectionScore = score?.coerceIn(0f, 1f)
        )
    }

    @Synchronized
    fun microphoneSilenced(silenced: Boolean) {
        current = current.copy(microphoneSilenced = silenced)
    }

    @Synchronized
    fun serviceState(
        state: String,
        overlayAttached: Boolean? = null,
        wakeWordRunning: Boolean? = null,
        restartAttempt: Int? = null
    ) {
        current = current.copy(
            serviceState = state.take(32),
            overlayAttached = overlayAttached ?: current.overlayAttached,
            wakeWordRunning = wakeWordRunning ?: current.wakeWordRunning,
            wakeWordRestartAttempt = restartAttempt ?: current.wakeWordRestartAttempt
        )
    }

    @Synchronized
    fun watchdogTick(
        atMs: Long,
        overlayAttached: Boolean,
        wakeWordRunning: Boolean,
        restartAttempt: Int
    ) {
        current = current.copy(
            watchdogLastTickAtMs = atMs,
            watchdogTickCount = current.watchdogTickCount + 1L,
            overlayAttached = overlayAttached,
            wakeWordRunning = wakeWordRunning,
            wakeWordRestartAttempt = restartAttempt
        )
    }

    @Synchronized
    fun watchdogRecovery() {
        current = current.copy(watchdogRecoveryCount = current.watchdogRecoveryCount + 1L)
    }

    @Synchronized
    fun failed(message: String) {
        current = current.copy(
            state = "ERROR",
            lastError = DiagnosticRedactor.redact(message).take(1_000),
            wakeWordRunning = false
        )
    }

    @Synchronized
    fun stopped() {
        current = current.copy(state = "STOPPED", rms = 0f, peakLevel = 0f, wakeWordRunning = false)
    }

    @Synchronized
    fun clearCandidateHistory() {
        current = current.copy(
            lastCandidateAtMs = null,
            lastCandidateKeyword = null,
            lastCandidateScore = null,
            lastCandidateAccepted = false,
            lastCandidateRejectionReason = null,
            lastDetectionAtMs = null,
            lastDetectionKeyword = null,
            lastDetectionScore = null,
            lastError = null
        )
    }
}
