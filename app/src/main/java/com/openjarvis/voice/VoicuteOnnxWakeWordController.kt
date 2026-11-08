package com.openjarvis.voice

import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.openjarvis.voice.voicute.DetectionLogic
import kotlin.math.sqrt

/**
 * Voicute ONNX wake-word adapter. It owns the only AudioRecord while active,
 * keeps a rolling 1.01s PCM window, and runs one multi-keyword inference per
 * 100ms hop. No other wake-word engine is used.
 */
class VoicuteOnnxWakeWordController(
    context: Context,
    private val onWakeWord: () -> Unit,
    private val onError: (String) -> Unit
) : WakeWordEngine {
    private val appContext = context.applicationContext
    @Volatile private var running = false
    private var engine: com.openjarvis.voice.voicute.WakeWordEngine? = null
    private var detection: DetectionLogic? = null
    private var recorder: AudioRecord? = null
    private var agc: AutomaticGainControl? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    private var worker: Thread? = null
    @Volatile private var lastAcceptedAtMs = 0L

    @Synchronized
    override fun start(): String? {
        if (running) return null
        WakeWordDiagnostics.initialize(appContext)
        return try {
            val permission = ContextCompat.checkSelfPermission(
                appContext, android.Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
            if (!permission) throw IllegalStateException("Microphone permission is not granted")

            val loaded = VoicuteModelLoader(appContext).load()
            val sampleRate = SAMPLE_RATE
            val minBuffer = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            if (minBuffer <= 0) {
                loaded.engine.close()
                throw IllegalStateException("Android microphone buffer is unavailable")
            }
            val audio = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minBuffer * 2, sampleRate * 2)
            )
            if (audio.state != AudioRecord.STATE_INITIALIZED) {
                audio.release()
                loaded.engine.close()
                throw IllegalStateException("Android could not initialize microphone capture")
            }

            val createdAgc = runCatching { AutomaticGainControl.create(audio.audioSessionId) }.getOrNull()
            val createdNs = runCatching { NoiseSuppressor.create(audio.audioSessionId) }.getOrNull()
            val agcEnabled = createdAgc?.let { runCatching { it.enabled = true; it.enabled }.getOrDefault(false) } ?: false
            val nsEnabled = createdNs?.let { runCatching { it.enabled = true; it.enabled }.getOrDefault(false) } ?: false

            engine = loaded.engine
            detection = DetectionLogic()
            recorder = audio
            agc = createdAgc
            noiseSuppressor = createdNs
            audio.startRecording()
            if (audio.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                throw IllegalStateException("Android microphone did not enter recording state")
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                audio.activeRecordingConfiguration?.isClientSilenced == true
            ) {
                throw IllegalStateException(
                    "Microphone is silenced by Android because another app has capture priority."
                )
            }

            running = true
            WakeWordDiagnostics.started(
                permissionGranted = true,
                keywords = loaded.keywords,
                threshold = sensitivityThreshold(),
                boost = 1f,
                engineName = VoicuteModelLoader.MODEL_ENGINE_NAME
            )
            WakeWordDiagnostics.audioConfiguration(
                agcAvailable = createdAgc != null,
                agcEnabled = agcEnabled,
                noiseSuppressorAvailable = createdNs != null,
                noiseSuppressorEnabled = nsEnabled,
                audioSource = "MIC",
                sampleRate = sampleRate
            )
            worker = Thread(
                { processAudio(loaded.engine.getAudioSamplesNeeded()) },
                "JarvisVoicuteOnnx"
            ).apply {
                isDaemon = true
                start()
            }
            Log.i(TAG, "Voicute multi-keyword ONNX engine started offline: ${loaded.keywords.joinToString()}")
            null
        } catch (error: Exception) {
            stop()
            val detail = error.message?.takeIf { it.isNotBlank() } ?: error.javaClass.simpleName
            Log.e(TAG, "Voicute ONNX wake-word initialization failed: $detail", error)
            WakeWordDiagnostics.failed(detail)
            val friendly = "Voicute offline wake-word start nahi hua: $detail. Automatic retry scheduled."
            onError(friendly)
            friendly
        }
    }

    private fun processAudio(windowSize: Int) {
        val audio = recorder ?: return
        val model = engine ?: return
        val detector = detection ?: return
        val ring = ShortArray(windowSize)
        var writeIndex = 0
        var samplesSeen = 0
        var samplesSinceInference = 0
        val readBuffer = ShortArray(INFERENCE_HOP_SAMPLES)

        try {
            while (running && !Thread.currentThread().isInterrupted) {
                val count = audio.read(readBuffer, 0, readBuffer.size)
                if (count <= 0) {
                    if (running) {
                        val message = when (count) {
                            AudioRecord.ERROR_INVALID_OPERATION -> "Microphone is unavailable or already in use."
                            AudioRecord.ERROR_DEAD_OBJECT -> "Microphone capture disconnected; Jarvis will retry."
                            AudioRecord.ERROR_BAD_VALUE -> "Microphone rejected the requested PCM format."
                            else -> "Microphone read failed (AudioRecord=$count)."
                        }
                        WakeWordDiagnostics.failed(message)
                        onError(message)
                    }
                    running = false
                    break
                }

                var energy = 0.0
                var peak = 0f
                var clipped = 0
                for (i in 0 until count) {
                    val sample = readBuffer[i]
                    ring[writeIndex] = sample
                    writeIndex = (writeIndex + 1) % ring.size
                    samplesSeen++
                    samplesSinceInference++
                    val normalized = sample / 32768f
                    val magnitude = kotlin.math.abs(normalized)
                    if (magnitude > peak) peak = magnitude
                    if (magnitude >= 0.98f) clipped++
                    energy += normalized * normalized
                }
                val rms = if (count > 0) sqrt(energy / count).toFloat() else 0f
                val rawRms = rms * 32768f
                WakeWordDiagnostics.audioFrame(rms, peak, clipped)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                    audio.activeRecordingConfiguration?.isClientSilenced == true
                ) {
                    val message = "Microphone is silenced by Android because another app has capture priority."
                    WakeWordDiagnostics.failed(message)
                    onError(message)
                    running = false
                    break
                }

                if (samplesSeen >= windowSize && samplesSinceInference >= INFERENCE_HOP_SAMPLES) {
                    val window = ShortArray(windowSize)
                    for (i in window.indices) window[i] = ring[(writeIndex + i) % ring.size]
                    samplesSinceInference = 0

                    val result = model.process(window)
                    // Keep the complete raw per-keyword scores for diagnostics, but only
                    // allow the configured wake phrases into the live detection pipeline.
                    val allowBareJarvis = appContext.getSharedPreferences(
                        WakeWordConfig.SENSITIVITY_PREFS, Context.MODE_PRIVATE
                    ).getBoolean(WakeWordConfig.ALLOW_BARE_JARVIS_KEY, false)
                    val selected = WakeWordConfig.selectCandidate(
                        parseKeywordScores(result?.scoreSummary), allowBareJarvis
                    )
                    val probability = selected?.second ?: 0f
                    val keyword = selected?.first
                    val now = System.currentTimeMillis()
                    detector.record(probability, keyword, rawRms, now)
                    val threshold = sensitivityThreshold()
                    val accepted = detector.evaluate(
                        keyword,
                        probability,
                        rawRms,
                        threshold,
                        result?.recommendedConsFrames ?: 2,
                        EXTRA_CONSECUTIVE_FRAMES,
                        now
                    )
                    var inferenceEnergy = 0.0
                    var inferencePeak = 0f
                    for (sample in window) {
                        val normalized = sample / 32768f
                        inferenceEnergy += normalized * normalized
                        inferencePeak = maxOf(inferencePeak, kotlin.math.abs(normalized))
                    }
                    val inferenceRms = sqrt(inferenceEnergy / window.size).toFloat()
                    WakeWordDiagnostics.inferenceRecord(
                        result?.scoreSummary,
                        threshold,
                        inferenceRms,
                        inferencePeak,
                        accepted
                    )
                    WakeWordDiagnostics.candidate(
                        result?.scoreSummary ?: ((keyword ?: "unknown") + "=" + String.format(java.util.Locale.US, "%.4f", probability)),
                        threshold,
                        rawRms,
                        !accepted.isNullOrBlank()
                    )
                    if (!accepted.isNullOrBlank() &&
                        WakeWordDetectionPolicy.shouldAccept(now, lastAcceptedAtMs)
                    ) {
                        lastAcceptedAtMs = now
                        WakeWordDiagnostics.detected(accepted, probability)
                        Log.i(TAG, "Voicute wake word detected: $accepted score=$probability")
                        onWakeWord()
                    }
                }
            }
        } catch (error: Exception) {
            if (running) {
                val detail = error.message ?: error.javaClass.simpleName
                WakeWordDiagnostics.failed(detail)
                onError("Voicute ONNX processing failed: $detail")
            }
            Log.e(TAG, "Voicute ONNX audio loop failed", error)
            running = false
        }
    }

    @Synchronized
    override fun stop() {
        running = false
        val audio = recorder
        recorder = null
        if (audio != null) {
            runCatching { if (audio.recordingState == AudioRecord.RECORDSTATE_RECORDING) audio.stop() }
            runCatching { audio.release() }
        }
        worker?.interrupt()
        if (worker !== Thread.currentThread()) runCatching { worker?.join(1_000) }
        worker = null
        agc?.let { runCatching { it.release() } }
        agc = null
        noiseSuppressor?.let { runCatching { it.release() } }
        noiseSuppressor = null
        detection = null
        engine?.let { runCatching { it.close() } }
        engine = null
        WakeWordDiagnostics.stopped()
    }

    override fun isRunning(): Boolean = running

    private fun parseKeywordScores(summary: String?): Map<String, Float> {
        if (summary.isNullOrBlank()) return emptyMap()
        return summary.split(",").mapNotNull { item ->
            val pair = item.trim().split("=", limit = 2)
            if (pair.size != 2) null
            else pair[1].trim().toFloatOrNull()?.let { pair[0].trim() to it }
        }.toMap()
    }

    private fun sensitivityThreshold(): Float {
        val preference = appContext.getSharedPreferences(
            WakeWordConfig.SENSITIVITY_PREFS, Context.MODE_PRIVATE
        ).getString(WakeWordConfig.SENSITIVITY_KEY, WakeWordConfig.Sensitivity.HIGH.name)
        return WakeWordConfig.Sensitivity.fromPreference(preference).threshold
    }

    companion object {
        private const val TAG = "JarvisVoicuteKws"
        private const val SAMPLE_RATE = 16_000
        private const val INFERENCE_HOP_SAMPLES = 1_600 // 100ms; one ONNX pass per hop
        private const val EXTRA_CONSECUTIVE_FRAMES = 2
    }
}
