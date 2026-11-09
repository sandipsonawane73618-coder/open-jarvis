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
import com.openjarvis.diagnostics.JarvisDiagnosticLog
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
            val requestedBufferBytes = maxOf(minBuffer * 2, sampleRate * 2)
            val audio = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                requestedBufferBytes
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
                sampleRate = sampleRate,
                channelConfig = "MONO",
                encoding = "PCM_16BIT",
                minBufferSizeBytes = minBuffer,
                bufferSizeBytes = requestedBufferBytes,
                recorderInitialized = audio.state == AudioRecord.STATE_INITIALIZED,
                recordingState = if (audio.recordingState == AudioRecord.RECORDSTATE_RECORDING) "RECORDING" else "NOT_RECORDING"
            )
            JarvisDiagnosticLog.record(appContext, "WAKEWORD", "Voicute ONNX listener started")
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
            val detail = error.javaClass.simpleName
            Log.e(TAG, "Voicute ONNX wake-word initialization failed: $detail", error)
            WakeWordDiagnostics.failed(detail)
            JarvisDiagnosticLog.record(appContext, "WAKEWORD_START", "Voicute ONNX initialization failed", error)
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
                        JarvisDiagnosticLog.record(appContext, "AUDIO_READ", message)
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
                    WakeWordDiagnostics.microphoneSilenced(true)
                    WakeWordDiagnostics.failed(message)
                    JarvisDiagnosticLog.record(appContext, "MICROPHONE", message)
                    onError(message)
                    running = false
                    break
                }

                if (samplesSeen >= windowSize && samplesSinceInference >= INFERENCE_HOP_SAMPLES) {
                    val window = ShortArray(windowSize)
                    for (i in window.indices) window[i] = ring[(writeIndex + i) % ring.size]
                    samplesSinceInference = 0

                    val result = model.process(window)
                    val probability = result?.probability ?: 0f
                    val keyword = result?.wakeWord
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
                    val detectorAccepted = !accepted.isNullOrBlank()
                    val cooldownAccepted = detectorAccepted && WakeWordDetectionPolicy.shouldAccept(now, lastAcceptedAtMs)
                    val finalAccepted = detectorAccepted && cooldownAccepted
                    val rejectionReason = when {
                        finalAccepted -> "accepted"
                        detectorAccepted -> "app_cooldown"
                        detector.debugFail == 1 -> "consecutive_frames"
                        detector.debugFail == 2 -> "relative_peak_background"
                        detector.debugFail == 3 -> "detector_cooldown"
                        detector.debugFail == 4 -> "burst_guard"
                        detector.debugFail == 5 -> "energy_guard"
                        else -> "pending_confirmation"
                    }
                    if (!keyword.isNullOrBlank() || probability >= threshold * 0.5f) {
                        WakeWordDiagnostics.candidate(keyword, probability, threshold, finalAccepted, rejectionReason, now)
                        JarvisDiagnosticLog.recordCandidate(
                            context = appContext,
                            keyword = keyword,
                            score = probability,
                            threshold = threshold,
                            accepted = finalAccepted,
                            rejectionReason = rejectionReason,
                            consecutiveFrames = detector.consecutiveFrames,
                            gateCode = detector.debugFail,
                            peak = detector.debugPeak,
                            background = detector.debugBackground,
                            preRmsRaw = detector.debugPreRms,
                            timestampMs = now
                        )
                    }
                    if (finalAccepted) {
                        lastAcceptedAtMs = now
                        WakeWordDiagnostics.detected(accepted.orEmpty(), probability)
                        Log.i(TAG, "Voicute wake word detected: $accepted score=$probability")
                        onWakeWord()
                    }
                }
            }
        } catch (error: Exception) {
            if (running) {
                val detail = error.javaClass.simpleName
                WakeWordDiagnostics.failed(detail)
                JarvisDiagnosticLog.record(appContext, "WAKEWORD_PROCESS", "Voicute ONNX audio loop failed", error)
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
