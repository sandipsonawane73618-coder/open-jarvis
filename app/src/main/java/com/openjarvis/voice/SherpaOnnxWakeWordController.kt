package com.openjarvis.voice

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
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
    private var worker: Thread? = null
    @Volatile private var lastWakeWordAtMs = 0L

    @Synchronized
    override fun start(): String? {
        if (running) return null
        return try {
            val sampleRate = 16_000
            val model = getKwsModelConfig(type = 1)
                ?: throw IllegalStateException("English sherpa KWS model config is unavailable")
            val config = KeywordSpotterConfig(
                featConfig = getFeatureConfig(sampleRate = sampleRate, featureDim = 80),
                modelConfig = model,
                keywordsFile = getKeywordsFile(type = 1)
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
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
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

            spotter = createdSpotter
            stream = createdStream
            recorder = audio
            audio.startRecording()
            if (audio.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                throw IllegalStateException("Android microphone did not enter recording state")
            }
            running = true
            worker = Thread({ processAudio(sampleRate) }, "JarvisSherpaKws").apply {
                isDaemon = true
                start()
            }
            Log.i(TAG, "Key-free sherpa-onnx wake-word engine started")
            null
        } catch (error: Exception) {
            stop()
            val detail = error.message?.takeIf { it.isNotBlank() } ?: error.javaClass.simpleName
            Log.e(TAG, "sherpa-onnx wake-word initialization failed: $detail", error)
            val friendly = "Offline JARVIS wake-word model start nahi hua: $detail. Manual mic abhi bhi use kar sakte ho."
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
                    if (running) onError("Wake-word microphone read failed (AudioRecord=$count)")
                    running = false
                    break
                }
                val samples = FloatArray(count) { buffer[it] / 32768.0f }
                inputStream.acceptWaveform(samples, sampleRate = sampleRate)
                while (running && kws.isReady(inputStream)) {
                    kws.decode(inputStream)
                    val keyword = kws.getResult(inputStream).keyword
                    if (keyword.isNotBlank()) {
                        kws.reset(inputStream)
                        val now = System.currentTimeMillis()
                        if (now - lastWakeWordAtMs >= WAKE_WORD_COOLDOWN_MS) {
                            lastWakeWordAtMs = now
                            Log.i(TAG, "Offline wake word detected")
                            onWakeWord()
                        }
                    }
                }
            }
        } catch (error: Exception) {
            if (running) onError("Wake-word processing failed: ${error.message ?: error.javaClass.simpleName}")
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
        stream?.let { runCatching { it.release() } }
        stream = null
        spotter?.let { runCatching { it.release() } }
        spotter = null
    }

    override fun isRunning(): Boolean = running

    companion object {
        private const val TAG = "JarvisSherpaKws"
        private const val WAKE_WORD_COOLDOWN_MS = 2_000L
    }
}
