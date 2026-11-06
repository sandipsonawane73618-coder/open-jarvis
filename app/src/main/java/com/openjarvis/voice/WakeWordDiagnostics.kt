package com.openjarvis.voice

/**
 * Process-local diagnostics shared by the foreground listener and Settings.
 * Sherpa's Kotlin KeywordSpotterResult does not expose an acoustic confidence
 * score, so score remains null rather than presenting a fabricated value.
 */
data class WakeWordDiagnosticSnapshot(
    val engineName: String = "Sherpa-ONNX KWS",
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
    val lastError: String? = null
)

object WakeWordDiagnostics {
    private const val SILENCE_RMS_THRESHOLD = 0.003f
    @Volatile private var current = WakeWordDiagnosticSnapshot()

    fun snapshot(): WakeWordDiagnosticSnapshot = current

    @Synchronized
    fun started(permissionGranted: Boolean, keywords: List<String>, threshold: Float, boost: Float) {
        current = current.copy(
            engineName = "Sherpa-ONNX KWS",
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
            keywords = keywords,
            threshold = threshold,
            keywordBoost = boost,
            lastError = null
        )
    }

    @Synchronized
    fun audioConfiguration(
        agcAvailable: Boolean,
        agcEnabled: Boolean,
        noiseSuppressorAvailable: Boolean,
        noiseSuppressorEnabled: Boolean,
        audioSource: String,
        sampleRate: Int
    ) {
        current = current.copy(
            agcAvailable = agcAvailable,
            agcEnabled = agcEnabled,
            noiseSuppressorAvailable = noiseSuppressorAvailable,
            noiseSuppressorEnabled = noiseSuppressorEnabled,
            audioSource = audioSource,
            sampleRate = sampleRate
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
    fun detected(keyword: String, score: Float? = null) {
        current = current.copy(
            state = "DETECTED",
            lastDetectionAtMs = System.currentTimeMillis(),
            lastDetectionKeyword = keyword,
            lastDetectionScore = score
        )
    }

    @Synchronized
    fun failed(message: String) {
        current = current.copy(state = "ERROR", lastError = message)
    }

    @Synchronized
    fun stopped() {
        current = current.copy(state = "STOPPED", rms = 0f, peakLevel = 0f)
    }
}
