package com.openjarvis.voice

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.os.Build
import android.util.Log
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import kotlin.math.sqrt
import com.k2fsa.sherpa.onnx.KeywordSpotter
import com.k2fsa.sherpa.onnx.KeywordSpotterConfig
import com.k2fsa.sherpa.onnx.getFeatureConfig
import com.k2fsa.sherpa.onnx.getKeywordsFile
import com.k2fsa.sherpa.onnx.getKwsModelConfig

/**
 * Key-free, fully offline JARVIS keyword spotter.
 * The English GigaSpeech KWS model is bundled as app assets by the release workflow.
 */
class SherpaOnnxWakeWordController(
    context: Context,
    private val onWakeWord: () -> Unit,
    private val onError: (String) -> Unit
) : WakeWordEngine {
    private val appContext = context.applicationContext
    @Volatile private var running = false
    private var spotter: KeywordSpotter? = null
    private var stream: com.k2fsa.sherpa.onnx.OnlineStream? = null
    private var recorder: AudioRecord? = null
    private var automaticGainControl: AutomaticGainControl? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    private var worker: Thread? = null
    @Volatile private var lastWakeWordAtMs = 0L

    @Synchronized
    override fun start(): String? {
        if (running) return null
        return try {
            val sampleRate = 16_000
            val model = getKwsModelConfig(type = 1)
                ?: throw IllegalStateException("English sherpa KWS model config is unavailable")
            val keywordFile = getKeywordsFile(type = 1)
            val keywordAssetLines = runCatching {
                appContext.assets.open(keywordFile).bufferedReader().use { it.readLines() }
            }.getOrElse { error ->
                throw IllegalStateException("JARVIS keyword asset is missing or unreadable: $keywordFile", error)
            }
            if (!WakeWordConfig.isValidTokenizedKeywordAsset(keywordAssetLines)) {
                throw IllegalStateException(
                    "JARVIS keyword asset is invalid: expected at least ${WakeWordConfig.EXPECTED_KEYWORD_TOKEN_LINES} " +
                        "BPE-tokenized keyword lines in $keywordFile"
                )
            }
            val keywords = WakeWordConfig.KEYWORD_PHRASES
            val sensitivity = WakeWordConfig.Sensitivity.fromPreference(
                appContext.getSharedPreferences(WakeWordConfig.SENSITIVITY_PREFS, Context.MODE_PRIVATE)
                    .getString(WakeWordConfig.SENSITIVITY_KEY, WakeWordConfig.Sensitivity.MEDIUM.name)
            )
            val threshold = sensitivity.threshold
            val keywordBoost = sensitivity.keywordBoost
            val config = KeywordSpotterConfig(
                featConfig = getFeatureConfig(sampleRate = sampleRate, featureDim = 80),
                modelConfig = model,
                keywordsFile = keywordFile,
                keywordsScore = keywordBoost,
                keywordsThreshold = threshold,
                numTrailingBlanks = 1
            )
            val createdSpotter = KeywordSpotter(assetManager = appContext.assets, config = config)
            // prepare-sherpa.sh generates the model-compatible BPE keyword file
            // containing JARVIS. Use the configured file instead of passing raw text.
            val createdStream = createdSpotter.createStream()
            if (createdStream.ptr == 0L) {
                createdSpotter.release()
                throw IllegalStateException("Could not create JARVIS keyword stream")
            }

            val minBuffer = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            if (minBuffer <= 0) {
                createdStream.release()
                createdSpotter.release()
                throw IllegalStateException("Android microphone buffer is unavailable")
            }
            val audio = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBuffer * 2
            )
            if (audio.state != AudioRecord.STATE_INITIALIZED) {
                audio.release()
                createdStream.release()
                createdSpotter.release()
                throw IllegalStateException("Android could not initialize microphone capture")
            }

            // Enable device-provided processing when available. OEMs may not expose
            // either effect; diagnostics record availability and actual enabled state.
            val agc = runCatching { AutomaticGainControl.create(audio.audioSessionId) }.getOrNull()
            val ns = runCatching { NoiseSuppressor.create(audio.audioSessionId) }.getOrNull()
            val agcEnabled = agc?.let { effect -> runCatching { effect.enabled = true; effect.enabled }.getOrDefault(false) } ?: false
            val nsEnabled = ns?.let { effect -> runCatching { effect.enabled = true; effect.enabled }.getOrDefault(false) } ?: false

            spotter = createdSpotter
            stream = createdStream
            recorder = audio
            automaticGainControl = agc
            noiseSuppressor = ns
            audio.startRecording()
            if (audio.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                throw IllegalStateException("Android microphone did not enter recording state")
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                audio.activeRecordingConfiguration?.isClientSilenced == true
            ) {
                throw IllegalStateException(
                    "Microphone is silenced by Android because another app has capture priority (Google Assistant or another recorder)."
                )
            }
            running = true
            val micGranted = ContextCompat.checkSelfPermission(
                appContext, android.Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
            WakeWordDiagnostics.started(micGranted, keywords, threshold, keywordBoost)
            WakeWordDiagnostics.audioConfiguration(
                agcAvailable = agc != null,
                agcEnabled = agcEnabled,
                noiseSuppressorAvailable = ns != null,
                noiseSuppressorEnabled = nsEnabled,
                audioSource = "MIC",
                sampleRate = sampleRate
            )
            worker = Thread({ processAudio(sampleRate) }, "JarvisSherpaKws").apply {
                isDaemon = true
                start()
            }
            Log.i(TAG, "Key-free sherpa-onnx wake-word engine started with Android MIC source")
            null
        } catch (error: Exception) {
            stop()
            val detail = error.message?.takeIf { it.isNotBlank() } ?: error.javaClass.simpleName
            Log.e(TAG, "sherpa-onnx wake-word initialization failed: $detail", error)
            val micProblem = detail.contains("microphone", ignoreCase = true) ||
                detail.contains("AudioRecord", ignoreCase = true) ||
                detail.contains("silenced", ignoreCase = true)
            val friendly = if (micProblem) {
                "Microphone unavailable: $detail. Google Assistant/Recorder ko mic chhodne do; JARVIS retry karega."
            } else {
                "Offline JARVIS wake-word model start nahi hua: $detail. Manual mic abhi bhi use kar sakte ho."
            }
            WakeWordDiagnostics.failed(detail)
            onError(friendly)
            friendly
        }
    }

    private fun processAudio(sampleRate: Int) {
        val audio = recorder ?: return
        val kws = spotter ?: return
        val inputStream = stream ?: return
        val buffer = ShortArray(sampleRate / 10)
        try {
            while (running && !Thread.currentThread().isInterrupted) {
                val count = audio.read(buffer, 0, buffer.size)
                if (count <= 0) {
                    if (running) {
                        val message = when (count) {
                            AudioRecord.ERROR_INVALID_OPERATION ->
                                "Microphone unavailable or already in use by another app (for example Google Assistant)."
                            AudioRecord.ERROR_DEAD_OBJECT ->
                                "Microphone capture was disconnected. JARVIS will retry."
                            AudioRecord.ERROR_BAD_VALUE ->
                                "Microphone rejected the requested audio format."
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
                var clippedSamples = 0
                val samples = FloatArray(count) {
                    val value = buffer[it] / 32768.0f
                    val magnitude = kotlin.math.abs(value)
                    if (magnitude > peak) peak = magnitude
                    if (magnitude >= 0.98f) clippedSamples++
                    energy += value * value
                    value
                }
                val rms = if (count > 0) sqrt(energy / count).toFloat() else 0f
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                    audio.activeRecordingConfiguration?.isClientSilenced == true
                ) {
                    val message = "Microphone is silenced by Android because another app has capture priority (Google Assistant or another recorder)."
                    WakeWordDiagnostics.failed(message)
                    onError(message)
                    running = false
                    break
                }
                WakeWordDiagnostics.audioFrame(rms, peak, clippedSamples)
                inputStream.acceptWaveform(samples, sampleRate = sampleRate)
                while (running && kws.isReady(inputStream)) {
                    kws.decode(inputStream)
                    val keyword = kws.getResult(inputStream).keyword
                    if (keyword.isNotBlank()) {
                        kws.reset(inputStream)
                        val now = System.currentTimeMillis()
                        if (WakeWordDetectionPolicy.shouldAccept(now, lastWakeWordAtMs)) {
                            lastWakeWordAtMs = now
                            WakeWordDiagnostics.detected(keyword, score = null)
                            Log.i(TAG, "Offline wake word detected: keyword=$keyword score=unavailable")
                            onWakeWord()
                        }
                    }
                }
            }
        } catch (error: Exception) {
            if (running) {
                WakeWordDiagnostics.failed(error.message ?: error.javaClass.simpleName)
                onError("Wake-word processing failed: ${error.message ?: error.javaClass.simpleName}")
            }
            Log.e(TAG, "sherpa-onnx audio loop failed", error)
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
        worker?.join(500)
        worker = null
        automaticGainControl?.let { runCatching { it.release() } }
        automaticGainControl = null
        noiseSuppressor?.let { runCatching { it.release() } }
        noiseSuppressor = null
        stream?.let { runCatching { it.release() } }
        stream = null
        spotter?.let { runCatching { it.release() } }
        spotter = null
        WakeWordDiagnostics.stopped()
    }

    override fun isRunning(): Boolean = running

    companion object {
        private const val TAG = "JarvisSherpaKws"
    }
}
