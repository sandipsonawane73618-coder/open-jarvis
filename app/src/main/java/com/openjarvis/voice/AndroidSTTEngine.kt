package com.openjarvis.voice

import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

class AndroidSTTEngine(private val context: Context) : STTEngine {
    companion object {
        const val SAMPLE_RATE = 16000
        const val SILENCE_THRESHOLD = 500
        const val SILENCE_TIMEOUT_MS = 1500L
        const val MAX_RECORDING_MS = 30000L
        private const val TAG = "AndroidSTTEngine"
    }

    private var recognizer: SpeechRecognizer? = null
    private var audioRecord: AudioRecord? = null
    private val _isListening = MutableStateFlow(false)
    val isListeningState: StateFlow<Boolean> = _isListening
    private var currentCallback: ((String?) -> Unit)? = null
    private var currentErrorCallback: ((String) -> Unit)? = null
    private var pendingResult: String? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var sessionActive = false
    private var silenceStartTime = -1L
    private var recordingStartTime = -1L

    init {
        mainHandler.post { createRecognizer() }
    }

    private fun createRecognizer() {
        // Strictly on-device recognition: never create the system/default recognizer,
        // because that service may route audio to a network-backed provider.
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S ||
            !SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
        ) {
            recognizer = null
            return
        }
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(context).also { speechRecognizer ->
            speechRecognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    _isListening.value = true
                }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {
                    if (rmsdB * 100 < SILENCE_THRESHOLD) {
                        if (silenceStartTime < 0) silenceStartTime = System.currentTimeMillis()
                        if (System.currentTimeMillis() - silenceStartTime >= SILENCE_TIMEOUT_MS && audioRecord != null) {
                            stopListeningEarly()
                        }
                    } else silenceStartTime = -1
                }
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() { _isListening.value = false }
                override fun onError(error: Int) {
                    _isListening.value = false
                    val message = when (error) {
                        SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                        SpeechRecognizer.ERROR_CLIENT -> "Client error"
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Missing permissions"
                        SpeechRecognizer.ERROR_NETWORK -> "Network error"
                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                        SpeechRecognizer.ERROR_NO_MATCH -> "No speech detected"
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy"
                        SpeechRecognizer.ERROR_SERVER -> "Server error"
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech timeout"
                        else -> "Speech recognition error ($error)"
                    }
                    sessionActive = false
                    currentErrorCallback?.invoke(message)
                }
                override fun onResults(results: Bundle?) {
                    _isListening.value = false
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull()?.trim()
                    pendingResult = text
                    if (!text.isNullOrBlank()) {
                        sessionActive = false
                        currentCallback?.invoke(text)
                    } else {
                        sessionActive = false
                        currentErrorCallback?.invoke("No speech detected")
                    }
                }
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }
    }

    override fun startListening(onResult: (String?) -> Unit, onError: (String) -> Unit) {
        currentCallback = onResult
        currentErrorCallback = onError
        pendingResult = null
        sessionActive = true
        mainHandler.post {
            if (recognizer == null) createRecognizer()
            if (recognizer == null) {
                sessionActive = false
                _isListening.value = false
                currentErrorCallback?.invoke(
                    "On-device speech recognition is unavailable on this device. Enable an offline speech service; no cloud fallback is used."
                )
                return@post
            }
            startListeningInternal()
        }
    }

    private fun startListeningInternal() {
        if (!sessionActive || recognizer == null) return
        silenceStartTime = -1L
        val deviceLanguage = Locale.getDefault().language
        val preferredLanguage = SpeechLanguage.preferredRecognitionLocale(deviceLanguage)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, preferredLanguage)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, preferredLanguage)
            if (android.os.Build.VERSION.SDK_INT >= 34) {
                putExtra("android.speech.extra.ENABLE_LANGUAGE_DETECTION", true)
                putStringArrayListExtra("android.speech.extra.LANGUAGE_DETECTION_ALLOWED_LANGUAGES",
                    ArrayList(SpeechLanguage.recognitionLocales))
            }
            // Bias common JARVIS vocabulary so app names and everyday Hinglish
            // phrases are less likely to be replaced by phonetically similar words.
            putStringArrayListExtra(
                "android.speech.extra.BIASING_STRINGS",
                arrayListOf(
                    "Jarvis", "JARVIS", "WhatsApp", "YouTube", "Instagram",
                    "time kya hua hai", "abhi kitne baje hain", "aaj ka samay kya hai",
                    "समय क्या हुआ है", "अभी कितने बजे हैं",
                    "आत्ता किती वाजले", "सध्या किती वाजले",
                    "खोलो", "बंद करो", "कॉल करो", "टॉर्च चालू करो"
                )
            )
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, SILENCE_TIMEOUT_MS)
        }
        try {
            recognizer?.startListening(intent)
        } catch (error: IllegalStateException) {
            sessionActive = false
            _isListening.value = false
            currentErrorCallback?.invoke("Speech recognizer is busy; tap Mic to try again")
        }
    }

    private fun stopListeningEarly() {
        recognizer?.stopListening()
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
    }

    override fun stop(): String? {
        sessionActive = false
        _isListening.value = false
        mainHandler.post { recognizer?.cancel() }
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        val result = pendingResult
        pendingResult = null
        currentCallback = null
        currentErrorCallback = null
        return result
    }

    override fun isListening(): Boolean = _isListening.value

    override fun release() {
        stop()
        mainHandler.post {
            recognizer?.destroy()
            recognizer = null
        }
    }

    fun startAudioCapture(): Boolean {
        val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        return try {
            audioRecord = AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT, bufferSize * 4)
            audioRecord?.startRecording()
            recordingStartTime = System.currentTimeMillis()
            true
        } catch (e: SecurityException) {
            false
        }
    }

    suspend fun captureWithVAD(): FloatArray = withContext(Dispatchers.IO) {
        val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val shortBuffer = ShortArray(bufferSize / 2)
        val allSamples = mutableListOf<Short>()
        while (_isListening.value) {
            if (System.currentTimeMillis() - recordingStartTime >= MAX_RECORDING_MS) break
            val read = audioRecord?.read(shortBuffer, 0, shortBuffer.size) ?: break
            if (read <= 0) continue
            allSamples.addAll(shortBuffer.take(read))
            val amplitude = shortBuffer.take(read).maxOfOrNull { kotlin.math.abs(it.toInt()) } ?: 0
            if (amplitude < SILENCE_THRESHOLD) {
                if (silenceStartTime < 0) silenceStartTime = System.currentTimeMillis()
                if (System.currentTimeMillis() - silenceStartTime >= SILENCE_TIMEOUT_MS) {
                    stop()
                    break
                }
            } else silenceStartTime = -1
        }
        allSamples.map { it.toFloat() / Short.MAX_VALUE }.toFloatArray()
    }
}